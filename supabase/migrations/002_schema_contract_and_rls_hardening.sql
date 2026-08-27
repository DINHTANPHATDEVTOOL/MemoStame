-- Migration 002: Schema Contract Alignment, RLS Hardening & Privacy Fixes
-- Base Migration: 001_auth_social_rls.sql

-- ===================================================
-- 1. ALIGN SCHEMA WITH REAL CLIENT CONTRACT
-- ===================================================

-- friend_requests columns
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS sender_username TEXT;
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS sender_display_name TEXT;
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS sender_avatar TEXT;
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS recipient_username TEXT;
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS recipient_display_name TEXT;
ALTER TABLE public.friend_requests ADD COLUMN IF NOT EXISTS recipient_avatar TEXT;

-- direct_messages columns
ALTER TABLE public.direct_messages ADD COLUMN IF NOT EXISTS stamp_location TEXT;

-- feed_posts columns
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS stamp_id TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS stamp_url TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS stamp_title TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS shape TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS author_name TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS author_avatar TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS caption TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS audience_type TEXT DEFAULT 'EVERYONE';
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS circle_id TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS circle_name TEXT;
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS type TEXT DEFAULT 'STAMP';
ALTER TABLE public.feed_posts ADD COLUMN IF NOT EXISTS location TEXT;

-- feed_reactions columns
ALTER TABLE public.feed_reactions ADD COLUMN IF NOT EXISTS emoji TEXT;
ALTER TABLE public.feed_reactions ADD COLUMN IF NOT EXISTS user_name TEXT;
ALTER TABLE public.feed_reactions ADD COLUMN IF NOT EXISTS user_avatar TEXT;

-- feed_comments columns
ALTER TABLE public.feed_comments ADD COLUMN IF NOT EXISTS author_name TEXT;
ALTER TABLE public.feed_comments ADD COLUMN IF NOT EXISTS author_avatar TEXT;


-- ===================================================
-- 2. PROFILE ID CONTRACT HARDENING
-- ===================================================

UPDATE public.profiles SET user_id = id::text WHERE user_id IS NULL OR user_id <> id::text;

-- Trigger to enforce profiles.user_id = profiles.id::text on insert/update
CREATE OR REPLACE FUNCTION public.sync_profile_user_id()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.user_id := NEW.id::text;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_profile_user_id ON public.profiles;
CREATE TRIGGER trg_sync_profile_user_id
    BEFORE INSERT OR UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_profile_user_id();


-- ===================================================
-- 3. SAFE PUBLIC PROFILE READ VIEW
-- ===================================================

CREATE OR REPLACE VIEW public.public_profiles AS
SELECT 
    id,
    user_id,
    username,
    display_name,
    avatar_url,
    cover_url,
    bio,
    city,
    created_at
FROM public.profiles;

GRANT SELECT ON public.public_profiles TO anon, authenticated;


-- ===================================================
-- 4. FRIEND REQUEST STATE IS RPC-ONLY
-- ===================================================

-- Drop direct client UPDATE policies on friend_requests
DROP POLICY IF EXISTS "Recipient update friend request status" ON public.friend_requests;
DROP POLICY IF EXISTS "Sender cancel friend request" ON public.friend_requests;

-- Direct client updates forbidden; status transitions must use SECURITY DEFINER RPCs


-- ===================================================
-- 5. DIRECT MESSAGE READ STATE RPC & RLS HARDENING
-- ===================================================

-- Drop broad UPDATE policy on direct_messages
DROP POLICY IF EXISTS "Recipient update read state" ON public.direct_messages;

-- RPC to safely mark direct messages as read
CREATE OR REPLACE FUNCTION public.mark_direct_messages_read(p_sender_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_updated_count INT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Authentication required';
    END IF;

    UPDATE public.direct_messages
    SET is_read = true
    WHERE recipient_id = v_acting_uid 
      AND sender_id = p_sender_id 
      AND is_read = false;

    GET DIAGNOSTICS v_updated_count = ROW_COUNT;

    RETURN jsonb_build_object(
        'sender_id', p_sender_id,
        'updated_count', v_updated_count
    );
END;
$$;


-- ===================================================
-- 6. FEED PRIVACY RLS HARDENING
-- ===================================================

-- Remove public SELECT USING (true) on feed_posts
DROP POLICY IF EXISTS "Public select feed posts" ON public.feed_posts;

-- Granular privacy SELECT policy for feed_posts
CREATE POLICY "Feed posts visibility policy" ON public.feed_posts
    FOR SELECT USING (
        -- Author always has access
        auth.uid() = author_id
        OR
        -- EVERYONE posts are visible to all authenticated users
        (COALESCE(audience_type, 'EVERYONE') = 'EVERYONE')
        OR
        -- FRIENDS posts visible if an accepted friendship pair exists
        (
            COALESCE(audience_type, 'EVERYONE') = 'FRIENDS' 
            AND EXISTS (
                SELECT 1 FROM public.friends f
                WHERE (f.user_id_1 = auth.uid() AND f.user_id_2 = author_id)
                   OR (f.user_id_2 = auth.uid() AND f.user_id_1 = author_id)
            )
        )
        -- SPECIFIC_FRIENDS non-author access is DENIED until server-side circle membership is ready
    );

-- Remove public SELECT USING (true) on reactions and comments
DROP POLICY IF EXISTS "Public select feed reactions" ON public.feed_reactions;
DROP POLICY IF EXISTS "Public select feed comments" ON public.feed_comments;

-- Reactions SELECT policy based on parent post visibility
CREATE POLICY "Select feed reactions if post visible" ON public.feed_reactions
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id::text = feed_reactions.post_id::text
        )
    );

-- Comments SELECT policy based on parent post visibility
CREATE POLICY "Select feed comments if post visible" ON public.feed_comments
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id::text = feed_comments.post_id::text
        )
    );
