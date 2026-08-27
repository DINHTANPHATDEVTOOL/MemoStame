-- Migration 001: Auth, Profiles, Social & Feed RLS Foundation + RPCs
-- Security authority: auth.uid() ONLY

-- ===================================================
-- 1. PROFILES TABLE & RLS
-- ===================================================

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    user_id TEXT DEFAULT NULL,
    username TEXT,
    display_name TEXT,
    avatar_url TEXT,
    cover_url TEXT,
    bio TEXT,
    city TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Backwards-compatibility column check for user_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE public.profiles ADD COLUMN user_id TEXT;
    END IF;
END $$;

-- Populate user_id = id::text for existing rows where user_id is null
UPDATE public.profiles SET user_id = id::text WHERE user_id IS NULL;

-- Unique case-insensitive index on username
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_lower ON public.profiles (lower(username)) WHERE username IS NOT NULL AND username <> '';

-- Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Policies for profiles
DROP POLICY IF EXISTS "Public profile view" ON public.profiles;
CREATE POLICY "Public profile view" ON public.profiles
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "User insert own profile" ON public.profiles;
CREATE POLICY "User insert own profile" ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "User update own profile" ON public.profiles;
CREATE POLICY "User update own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "User delete own profile" ON public.profiles;
CREATE POLICY "User delete own profile" ON public.profiles
    FOR DELETE USING (auth.uid() = id);


-- ===================================================
-- 2. FRIEND REQUESTS TABLE & RLS
-- ===================================================

CREATE TABLE IF NOT EXISTS public.friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT chk_friend_requests_sender_not_recipient CHECK (sender_id <> recipient_id)
);

-- Unique index to prevent multiple PENDING requests between same pair
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_friend_requests 
ON public.friend_requests (least(sender_id, recipient_id), greatest(sender_id, recipient_id)) 
WHERE status = 'PENDING';

-- Enable RLS
ALTER TABLE public.friend_requests ENABLE ROW LEVEL SECURITY;

-- Policies for friend_requests
DROP POLICY IF EXISTS "Participants select friend requests" ON public.friend_requests;
CREATE POLICY "Participants select friend requests" ON public.friend_requests
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Sender insert friend request" ON public.friend_requests;
CREATE POLICY "Sender insert friend request" ON public.friend_requests
    FOR INSERT WITH CHECK (auth.uid() = sender_id AND sender_id <> recipient_id);

DROP POLICY IF EXISTS "Recipient update friend request status" ON public.friend_requests;
CREATE POLICY "Recipient update friend request status" ON public.friend_requests
    FOR UPDATE USING (auth.uid() = recipient_id)
    WITH CHECK (auth.uid() = recipient_id AND status IN ('ACCEPTED', 'DECLINED'));

DROP POLICY IF EXISTS "Sender cancel friend request" ON public.friend_requests;
CREATE POLICY "Sender cancel friend request" ON public.friend_requests
    FOR UPDATE USING (auth.uid() = sender_id)
    WITH CHECK (auth.uid() = sender_id AND status = 'CANCELLED');


-- ===================================================
-- 3. FRIENDS TABLE & RLS
-- ===================================================

CREATE TABLE IF NOT EXISTS public.friends (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id_1 UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    user_id_2 UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_friends_user1_not_user2 CHECK (user_id_1 <> user_id_2)
);

-- Canonical ordering unique index (least, greatest)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_friendship_pair 
ON public.friends (least(user_id_1, user_id_2), greatest(user_id_1, user_id_2));

-- Enable RLS
ALTER TABLE public.friends ENABLE ROW LEVEL SECURITY;

-- Policies for friends
DROP POLICY IF EXISTS "Participants select friends" ON public.friends;
CREATE POLICY "Participants select friends" ON public.friends
    FOR SELECT USING (auth.uid() = user_id_1 OR auth.uid() = user_id_2);


-- ===================================================
-- 4. DIRECT MESSAGES TABLE & RLS
-- ===================================================

CREATE TABLE IF NOT EXISTS public.direct_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    sender_name TEXT,
    sender_avatar TEXT,
    recipient_name TEXT,
    recipient_avatar TEXT,
    text TEXT,
    stamp_id TEXT,
    stamp_title TEXT,
    stamp_image_url TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Participants select direct messages" ON public.direct_messages;
CREATE POLICY "Participants select direct messages" ON public.direct_messages
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Sender insert direct message" ON public.direct_messages;
CREATE POLICY "Sender insert direct message" ON public.direct_messages
    FOR INSERT WITH CHECK (auth.uid() = sender_id);

DROP POLICY IF EXISTS "Recipient update read state" ON public.direct_messages;
CREATE POLICY "Recipient update read state" ON public.direct_messages
    FOR UPDATE USING (auth.uid() = recipient_id)
    WITH CHECK (auth.uid() = recipient_id);


-- ===================================================
-- 5. FEED TABLES & RLS
-- ===================================================

CREATE TABLE IF NOT EXISTS public.feed_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    author_name TEXT,
    author_username TEXT,
    author_avatar TEXT,
    content TEXT,
    image_url TEXT,
    visibility TEXT DEFAULT 'EVERYONE',
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.feed_posts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public select feed posts" ON public.feed_posts;
CREATE POLICY "Public select feed posts" ON public.feed_posts
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Author insert feed posts" ON public.feed_posts;
CREATE POLICY "Author insert feed posts" ON public.feed_posts
    FOR INSERT WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Author update feed posts" ON public.feed_posts;
CREATE POLICY "Author update feed posts" ON public.feed_posts
    FOR UPDATE USING (auth.uid() = author_id)
    WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Author delete feed posts" ON public.feed_posts;
CREATE POLICY "Author delete feed posts" ON public.feed_posts
    FOR DELETE USING (auth.uid() = author_id);

-- feed_reactions
CREATE TABLE IF NOT EXISTS public.feed_reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.feed_posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    user_name TEXT,
    user_avatar TEXT,
    reaction_type TEXT DEFAULT 'LIKE',
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT unique_user_post_reaction UNIQUE (post_id, user_id)
);

ALTER TABLE public.feed_reactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public select feed reactions" ON public.feed_reactions;
CREATE POLICY "Public select feed reactions" ON public.feed_reactions
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "User insert feed reaction" ON public.feed_reactions;
CREATE POLICY "User insert feed reaction" ON public.feed_reactions
    FOR INSERT WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "User delete feed reaction" ON public.feed_reactions;
CREATE POLICY "User delete feed reaction" ON public.feed_reactions
    FOR DELETE USING (auth.uid() = user_id);

-- feed_comments
CREATE TABLE IF NOT EXISTS public.feed_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.feed_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    author_name TEXT,
    author_avatar TEXT,
    content TEXT NOT NULL,
    parent_comment_id UUID REFERENCES public.feed_comments(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.feed_comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public select feed comments" ON public.feed_comments;
CREATE POLICY "Public select feed comments" ON public.feed_comments
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Author insert feed comment" ON public.feed_comments;
CREATE POLICY "Author insert feed comment" ON public.feed_comments
    FOR INSERT WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Author update feed comment" ON public.feed_comments;
CREATE POLICY "Author update feed comment" ON public.feed_comments
    FOR UPDATE USING (auth.uid() = author_id)
    WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Author delete feed comment" ON public.feed_comments;
CREATE POLICY "Author delete feed comment" ON public.feed_comments
    FOR DELETE USING (auth.uid() = author_id);


-- ===================================================
-- 6. SECURITY DEFINER RPC FUNCTIONS
-- ===================================================

-- 6.1 Accept Friend Request RPC
CREATE OR REPLACE FUNCTION public.accept_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_req RECORD;
    v_u1 UUID;
    v_u2 UUID;
    v_acting_uid UUID;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    -- 1. Read & lock request
    SELECT * INTO v_req
    FROM public.friend_requests
    WHERE id = p_request_id
    FOR UPDATE;

    -- 2. Require request exists
    IF v_req.id IS NULL THEN
        RAISE EXCEPTION 'Friend request not found';
    END IF;

    -- 3. Require status = PENDING
    IF v_req.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Friend request is not pending';
    END IF;

    -- 4. Require auth.uid() = recipient_id
    IF v_req.recipient_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only recipient can accept friend request';
    END IF;

    -- 5. Require sender_id != auth.uid()
    IF v_req.sender_id = v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Sender cannot accept own friend request';
    END IF;

    -- 6. Update request status to ACCEPTED
    UPDATE public.friend_requests
    SET status = 'ACCEPTED',
        updated_at = now()
    WHERE id = p_request_id;

    -- 7. Determine canonical pair ordering
    IF v_req.sender_id < v_req.recipient_id THEN
        v_u1 := v_req.sender_id;
        v_u2 := v_req.recipient_id;
    ELSE
        v_u1 := v_req.recipient_id;
        v_u2 := v_req.sender_id;
    END IF;

    -- 8. Insert canonical friendship pair (ignore duplicates)
    INSERT INTO public.friends (user_id_1, user_id_2)
    VALUES (v_u1, v_u2)
    ON CONFLICT DO NOTHING;

    RETURN jsonb_build_object(
        'request_id', p_request_id,
        'status', 'ACCEPTED',
        'friend_user_id', v_req.sender_id
    );
END;
$$;


-- 6.2 Decline Friend Request RPC
CREATE OR REPLACE FUNCTION public.decline_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_req RECORD;
    v_acting_uid UUID;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    SELECT * INTO v_req
    FROM public.friend_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF v_req.id IS NULL THEN
        RAISE EXCEPTION 'Friend request not found';
    END IF;

    IF v_req.recipient_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only recipient can decline friend request';
    END IF;

    IF v_req.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Friend request is not pending';
    END IF;

    UPDATE public.friend_requests
    SET status = 'DECLINED',
        updated_at = now()
    WHERE id = p_request_id;

    RETURN jsonb_build_object(
        'request_id', p_request_id,
        'status', 'DECLINED'
    );
END;
$$;


-- 6.3 Cancel Friend Request RPC
CREATE OR REPLACE FUNCTION public.cancel_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_req RECORD;
    v_acting_uid UUID;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    SELECT * INTO v_req
    FROM public.friend_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF v_req.id IS NULL THEN
        RAISE EXCEPTION 'Friend request not found';
    END IF;

    IF v_req.sender_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only sender can cancel friend request';
    END IF;

    IF v_req.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Friend request is not pending';
    END IF;

    UPDATE public.friend_requests
    SET status = 'CANCELLED',
        updated_at = now()
    WHERE id = p_request_id;

    RETURN jsonb_build_object(
        'request_id', p_request_id,
        'status', 'CANCELLED'
    );
END;
$$;


-- 6.4 Unfriend RPC
CREATE OR REPLACE FUNCTION public.unfriend_user(p_friend_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_deleted_count INT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    DELETE FROM public.friends
    WHERE (user_id_1 = v_acting_uid AND user_id_2 = p_friend_id)
       OR (user_id_2 = v_acting_uid AND user_id_1 = p_friend_id);

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    RETURN jsonb_build_object(
        'friend_user_id', p_friend_id,
        'unfriended', (v_deleted_count > 0)
    );
END;
$$;
