-- Migration 003: Deployment Safety, ID Contract Standardization & Security Hardening
-- Base Migrations: 001_auth_social_rls.sql, 002_schema_contract_and_rls_hardening.sql

-- ===================================================
-- 1. DEFENSIVE CLEANUP OF PRODUCTION RLS TEST RPC
-- ===================================================

-- Ensure test harness code is never exposed as a production SECURITY DEFINER function
DROP FUNCTION IF EXISTS public.run_rls_negative_tests();


-- ===================================================
-- 2. FEED CONTENT ID CONTRACT STANDARDIZATION
-- ===================================================

-- Standardize content identifier columns as TEXT for full compatibility with client IDs (e.g., feed_post_1)
ALTER TABLE public.feed_posts ALTER COLUMN id TYPE TEXT USING id::text;
ALTER TABLE public.feed_reactions ALTER COLUMN id TYPE TEXT USING id::text;
ALTER TABLE public.feed_reactions ALTER COLUMN post_id TYPE TEXT USING post_id::text;
ALTER TABLE public.feed_comments ALTER COLUMN id TYPE TEXT USING id::text;
ALTER TABLE public.feed_comments ALTER COLUMN post_id TYPE TEXT USING post_id::text;


-- ===================================================
-- 3. PROFILES BASE TABLE PRIVACY & PERMISSIONS HARDENING
-- ===================================================

-- Drop legacy public profile view policy on base profiles table
DROP POLICY IF EXISTS "Public profile view" ON public.profiles;

-- Base profiles table policies strictly protect owner
DROP POLICY IF EXISTS "User select own profile" ON public.profiles;
CREATE POLICY "User select own profile" ON public.profiles
    FOR SELECT USING (auth.uid() = id);

-- Explicitly revoke direct SELECT on base profiles from anon and authenticated users
REVOKE SELECT ON TABLE public.profiles FROM anon, authenticated;

-- Ensure public discovery operates strictly via public_profiles view
GRANT SELECT ON TABLE public.public_profiles TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.profiles TO authenticated;


-- ===================================================
-- 4. FEED AUDIENCE CONTRACT & PRIVACY POLICY HARDENING
-- ===================================================

-- Migrate legacy 'EVERYONE' or NULL audience_type to 'FRIENDS'
UPDATE public.feed_posts
SET audience_type = 'FRIENDS'
WHERE audience_type = 'EVERYONE' OR audience_type IS NULL;

-- Drop previous feed post policy
DROP POLICY IF EXISTS "Feed posts visibility policy" ON public.feed_posts;

-- Enforce strict feed posts visibility policy
CREATE POLICY "Feed posts visibility policy" ON public.feed_posts
    FOR SELECT USING (
        -- Author always has access
        auth.uid() = author_id
        OR
        -- FRIENDS posts visible if an accepted friendship pair exists
        (
            COALESCE(audience_type, 'FRIENDS') = 'FRIENDS'
            AND EXISTS (
                SELECT 1 FROM public.friends f
                WHERE (f.user_id_1 = auth.uid() AND f.user_id_2 = author_id)
                   OR (f.user_id_2 = auth.uid() AND f.user_id_1 = author_id)
            )
        )
        -- SPECIFIC_FRIENDS & ONLY_ME & Unknown are strictly DENIED for non-authors
    );


-- ===================================================
-- 5. SECURITY DEFINER FUNCTIONS HARDENING & PERMISSIONS
-- ===================================================

-- Re-declare RPCs with explicit search_path and auth checks
CREATE OR REPLACE FUNCTION public.accept_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_req RECORD;
    v_u1 UUID;
    v_u2 UUID;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Authentication required';
    END IF;

    SELECT * INTO v_req
    FROM public.friend_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Friend request not found';
    END IF;

    IF v_req.recipient_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only recipient can accept request';
    END IF;

    IF v_req.sender_id = v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Sender cannot accept own request';
    END IF;

    IF v_req.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Friend request is not pending';
    END IF;

    UPDATE public.friend_requests
    SET status = 'ACCEPTED', updated_at = NOW()
    WHERE id = p_request_id;

    IF v_req.sender_id < v_req.recipient_id THEN
        v_u1 := v_req.sender_id;
        v_u2 := v_req.recipient_id;
    ELSE
        v_u1 := v_req.recipient_id;
        v_u2 := v_req.sender_id;
    END IF;

    INSERT INTO public.friends (user_id_1, user_id_2, created_at)
    VALUES (v_u1, v_u2, NOW())
    ON CONFLICT DO NOTHING;

    RETURN jsonb_build_object('request_id', p_request_id, 'status', 'ACCEPTED');
END;
$$;

CREATE OR REPLACE FUNCTION public.decline_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_req RECORD;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Authentication required';
    END IF;

    SELECT * INTO v_req FROM public.friend_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Friend request not found'; END IF;
    IF v_req.recipient_id <> v_acting_uid THEN RAISE EXCEPTION 'Unauthorized: Only recipient can decline request'; END IF;
    IF v_req.status <> 'PENDING' THEN RAISE EXCEPTION 'Friend request is not pending'; END IF;

    UPDATE public.friend_requests SET status = 'DECLINED', updated_at = NOW() WHERE id = p_request_id;
    RETURN jsonb_build_object('request_id', p_request_id, 'status', 'DECLINED');
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_friend_request(p_request_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_req RECORD;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Authentication required';
    END IF;

    SELECT * INTO v_req FROM public.friend_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Friend request not found'; END IF;
    IF v_req.sender_id <> v_acting_uid THEN RAISE EXCEPTION 'Unauthorized: Only sender can cancel request'; END IF;
    IF v_req.status <> 'PENDING' THEN RAISE EXCEPTION 'Friend request is not pending'; END IF;

    UPDATE public.friend_requests SET status = 'CANCELLED', updated_at = NOW() WHERE id = p_request_id;
    RETURN jsonb_build_object('request_id', p_request_id, 'status', 'CANCELLED');
END;
$$;

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
        RAISE EXCEPTION 'Unauthorized: Authentication required';
    END IF;

    DELETE FROM public.friends
    WHERE (user_id_1 = v_acting_uid AND user_id_2 = p_friend_id)
       OR (user_id_2 = v_acting_uid AND user_id_1 = p_friend_id);

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    RETURN jsonb_build_object('friend_id', p_friend_id, 'deleted_count', v_deleted_count);
END;
$$;

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
    WHERE recipient_id = v_acting_uid AND sender_id = p_sender_id AND is_read = false;

    GET DIAGNOSTICS v_updated_count = ROW_COUNT;
    RETURN jsonb_build_object('sender_id', p_sender_id, 'updated_count', v_updated_count);
END;
$$;

-- Revoke execute from PUBLIC and anon for all RPCs
REVOKE EXECUTE ON FUNCTION public.accept_friend_request(UUID) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.decline_friend_request(UUID) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.cancel_friend_request(UUID) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.unfriend_user(UUID) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.mark_direct_messages_read(UUID) FROM PUBLIC, anon;

-- Grant execute exclusively to authenticated role
GRANT EXECUTE ON FUNCTION public.accept_friend_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.decline_friend_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.cancel_friend_request(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.unfriend_user(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_direct_messages_read(UUID) TO authenticated;
