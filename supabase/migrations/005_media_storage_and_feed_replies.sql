-- Migration 005: Authenticated Media Storage, Feed Replies & Media URL Constraints
-- Base Migrations: 001_auth_social_rls.sql, 002_schema_contract_and_rls_hardening.sql,
--                  003_deployment_safety_and_id_contract.sql, 004_add_direct_messages_realtime_publication.sql

-- ===================================================
-- 1. STORAGE BUCKET CONFIGURATION FOR RENDERED STAMP MEDIA
-- ===================================================

-- Create the dedicated 'stamp-media' storage bucket if it does not exist
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'stamp-media',
    'stamp-media',
    true,
    8388608, -- 8 MB maximum per rendered image
    ARRAY['image/png', 'image/jpeg', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 8388608,
    allowed_mime_types = ARRAY['image/png', 'image/jpeg', 'image/webp'];

-- ===================================================
-- 2. STORAGE ROW LEVEL SECURITY POLICIES
-- ===================================================

-- Storage RLS policies for 'stamp-media' (storage.objects already has RLS enabled)

-- 2.1 Public read for rendered stamp media in 'stamp-media' bucket
DROP POLICY IF EXISTS "Public select stamp media" ON storage.objects;
CREATE POLICY "Public select stamp media" ON storage.objects
    FOR SELECT USING (bucket_id = 'stamp-media');

-- 2.2 Authenticated upload: first path segment MUST equal auth.uid()::text
DROP POLICY IF EXISTS "Owner insert stamp media" ON storage.objects;
CREATE POLICY "Owner insert stamp media" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'stamp-media'
        AND auth.role() = 'authenticated'
        AND split_part(name, '/', 1) = auth.uid()::text
    );

-- 2.3 Authenticated update: owner only
DROP POLICY IF EXISTS "Owner update stamp media" ON storage.objects;
CREATE POLICY "Owner update stamp media" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'stamp-media'
        AND auth.role() = 'authenticated'
        AND split_part(name, '/', 1) = auth.uid()::text
    ) WITH CHECK (
        bucket_id = 'stamp-media'
        AND auth.role() = 'authenticated'
        AND split_part(name, '/', 1) = auth.uid()::text
    );

-- 2.4 Authenticated delete: owner only
DROP POLICY IF EXISTS "Owner delete stamp media" ON storage.objects;
CREATE POLICY "Owner delete stamp media" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'stamp-media'
        AND auth.role() = 'authenticated'
        AND split_part(name, '/', 1) = auth.uid()::text
    );


-- ===================================================
-- 3. FEED REPLIES TABLE AND ROW LEVEL SECURITY
-- ===================================================

CREATE TABLE IF NOT EXISTS public.feed_replies (
    id TEXT PRIMARY KEY,
    post_id TEXT NOT NULL REFERENCES public.feed_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    author_name TEXT,
    author_avatar TEXT,
    reply_stamp_id TEXT,
    reply_stamp_url TEXT NOT NULL,
    shape TEXT,
    note TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT check_reply_stamp_url_remote CHECK (reply_stamp_url ~* '^https?://')
);

ALTER TABLE public.feed_replies ENABLE ROW LEVEL SECURITY;

-- Grant permissions to authenticated and anon roles
GRANT SELECT ON TABLE public.feed_replies TO anon, authenticated;
GRANT INSERT, DELETE ON TABLE public.feed_replies TO authenticated;

-- 3.1 SELECT: only if parent post is visible under feed post visibility policy
DROP POLICY IF EXISTS "Select feed replies if post visible" ON public.feed_replies;
CREATE POLICY "Select feed replies if post visible" ON public.feed_replies
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id = feed_replies.post_id
        )
    );

-- 3.2 INSERT: author MUST be authenticated user AND parent post must be accessible
DROP POLICY IF EXISTS "Author insert feed reply" ON public.feed_replies;
CREATE POLICY "Author insert feed reply" ON public.feed_replies
    FOR INSERT WITH CHECK (
        auth.uid() = author_id
        AND EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id = feed_replies.post_id
        )
    );

-- 3.3 DELETE: author only
DROP POLICY IF EXISTS "Author delete feed reply" ON public.feed_replies;
CREATE POLICY "Author delete feed reply" ON public.feed_replies
    FOR DELETE USING (auth.uid() = author_id);


-- ===================================================
-- 4. SERVER-SIDE MEDIA URL CONSTRAINTS
-- ===================================================

-- Protect direct_messages.stamp_image_url from local/data/file URLs
ALTER TABLE public.direct_messages DROP CONSTRAINT IF EXISTS check_dm_stamp_image_url_remote;
ALTER TABLE public.direct_messages ADD CONSTRAINT check_dm_stamp_image_url_remote
    CHECK (stamp_image_url IS NULL OR stamp_image_url ~* '^https?://') NOT VALID;

-- Protect feed_posts.stamp_url from local/data/file URLs
ALTER TABLE public.feed_posts DROP CONSTRAINT IF EXISTS check_feed_posts_stamp_url_remote;
ALTER TABLE public.feed_posts ADD CONSTRAINT check_feed_posts_stamp_url_remote
    CHECK (stamp_url IS NULL OR stamp_url ~* '^https?://') NOT VALID;


-- ===================================================
-- 5. REALTIME PUBLICATION FOR FEED REPLIES
-- ===================================================

ALTER TABLE public.feed_replies REPLICA IDENTITY FULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_rel pr
      JOIN pg_publication p ON p.oid = pr.prpubid
      JOIN pg_class c ON c.oid = pr.prrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE p.pubname = 'supabase_realtime'
        AND n.nspname = 'public'
        AND c.relname = 'feed_replies'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.feed_replies;
    END IF;
  END IF;
END $$;
