-- Migration 008: Block Privacy Oracle Hotfix & SECURITY DEFINER Privilege Hardening
-- Base Migrations: 001_auth_social_rls.sql through 007_user_blocks_and_abuse_reports.sql

-- ===================================================
-- 1. PRIVATE SCHEMA FOR INTERNAL SECURITY DEFINER HELPERS
-- ===================================================
-- Create schema 'app_private' which is NOT in PostgREST's exposed schemas list
-- (supabase/config.toml configures: schemas = ["public", "storage", "graphql_public"]).
-- Functions in this schema cannot be invoked via /rest/v1/rpc/... by any client.

CREATE SCHEMA IF NOT EXISTS app_private;
REVOKE ALL ON SCHEMA app_private FROM PUBLIC;
GRANT USAGE ON SCHEMA app_private TO postgres, authenticated, anon, service_role;


-- ===================================================
-- 2. RELOCATE BLOCK HELPER TO APP_PRIVATE SCHEMA
-- ===================================================
-- Internal predicate remains SECURITY DEFINER so table RLS policies can check
-- reciprocal block status without recursion or exposing user_blocks rows.
-- Uses a pinned, immutable search_path for safety.

CREATE OR REPLACE FUNCTION app_private.is_blocked_bidirectional(p_user_1 UUID, p_user_2 UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.user_blocks ub
        WHERE (ub.blocker_id = p_user_1 AND ub.blocked_id = p_user_2)
           OR (ub.blocker_id = p_user_2 AND ub.blocked_id = p_user_1)
    );
$$;

-- Allow authenticated and anon to execute the internal helper during RLS policy evaluation,
-- but revoke from PUBLIC. Since app_private is not in PostgREST schemas, it cannot be called as an RPC.
REVOKE ALL ON FUNCTION app_private.is_blocked_bidirectional(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_private.is_blocked_bidirectional(UUID, UUID) TO authenticated, anon;


-- ===================================================
-- 3. REPOINT RLS POLICIES TO APP_PRIVATE BLOCK HELPER
-- ===================================================

-- 3.1 Friend Requests: Deny new requests if blocked in either direction
DROP POLICY IF EXISTS "Sender insert friend request" ON public.friend_requests;
CREATE POLICY "Sender insert friend request" ON public.friend_requests
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id 
        AND sender_id <> recipient_id
        AND NOT app_private.is_blocked_bidirectional(sender_id, recipient_id)
    );

-- 3.2 Direct Messages: Deny new DMs if blocked in either direction
DROP POLICY IF EXISTS "Sender insert direct message" ON public.direct_messages;
CREATE POLICY "Sender insert direct message" ON public.direct_messages
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id
        AND NOT app_private.is_blocked_bidirectional(sender_id, recipient_id)
    );

-- 3.3 Feed Reactions: Deny reaction if post author and reactor have a block relation
DROP POLICY IF EXISTS "User insert feed reaction" ON public.feed_reactions;
CREATE POLICY "User insert feed reaction" ON public.feed_reactions
    FOR INSERT WITH CHECK (
        auth.uid() = user_id
        AND NOT EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id::text = feed_reactions.post_id::text
              AND app_private.is_blocked_bidirectional(user_id, fp.author_id)
        )
    );

-- 3.4 Feed Comments: Deny comment if post author and commenter have a block relation
DROP POLICY IF EXISTS "Author insert feed comment" ON public.feed_comments;
CREATE POLICY "Author insert feed comment" ON public.feed_comments
    FOR INSERT WITH CHECK (
        auth.uid() = author_id
        AND NOT EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id::text = feed_comments.post_id::text
              AND app_private.is_blocked_bidirectional(author_id, fp.author_id)
        )
    );

-- 3.5 Feed Replies: Deny reply if post author and replier have a block relation
DROP POLICY IF EXISTS "Author insert feed reply" ON public.feed_replies;
CREATE POLICY "Author insert feed reply" ON public.feed_replies
    FOR INSERT WITH CHECK (
        auth.uid() = author_id
        AND EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id = feed_replies.post_id
        )
        AND NOT EXISTS (
            SELECT 1 FROM public.feed_posts fp
            WHERE fp.id = feed_replies.post_id
              AND app_private.is_blocked_bidirectional(author_id, fp.author_id)
        )
    );


-- ===================================================
-- 4. UPDATE ACCEPT FRIEND REQUEST RPC TO USE APP_PRIVATE
-- ===================================================

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

    -- 6. Deny if either user has blocked the other
    IF app_private.is_blocked_bidirectional(v_acting_uid, v_req.sender_id) THEN
        RAISE EXCEPTION 'Cannot accept friend request: relationship is blocked';
    END IF;

    -- 7. Update request status to ACCEPTED
    UPDATE public.friend_requests
    SET status = 'ACCEPTED',
        updated_at = now()
    WHERE id = p_request_id;

    -- 8. Determine canonical pair ordering
    IF v_req.sender_id < v_req.recipient_id THEN
        v_u1 := v_req.sender_id;
        v_u2 := v_req.recipient_id;
    ELSE
        v_u1 := v_req.recipient_id;
        v_u2 := v_req.sender_id;
    END IF;

    -- 9. Insert canonical friendship pair (ignore duplicates)
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


-- ===================================================
-- 5. DROP THE PUBLIC BLOCK ORACLE
-- ===================================================
-- Dropping public.is_blocked_bidirectional removes the /rest/v1/rpc/is_blocked_bidirectional
-- endpoint from PostgREST, preventing enumeration and oracle attacks.

DROP FUNCTION IF EXISTS public.is_blocked_bidirectional(UUID, UUID);


-- ===================================================
-- 6. PROJECT-WIDE SECURITY DEFINER PRIVILEGE HARDENING
-- ===================================================
-- Audit classification:
--
-- A. Authenticated Client RPCs (derive caller from auth.uid()):
--    - block_user(UUID)
--    - unblock_user(UUID)
--    - report_user(UUID, TEXT, TEXT, TEXT, TEXT)
--    - accept_friend_request(UUID)
--    - decline_friend_request(UUID)
--    - cancel_friend_request(UUID)
--    - unfriend_user(UUID)
--    - mark_direct_messages_read(UUID)
--    - register_push_device_token(TEXT, TEXT, TEXT, TEXT, TEXT)
--    - unregister_push_device_token(TEXT, TEXT)
--    -> Action: Explicitly revoke from PUBLIC and anon; grant exclusively to authenticated.
--
-- B. Internal Trigger Functions:
--    - sync_profile_user_id()
--    -> Action: Explicitly revoke from PUBLIC, anon, authenticated.
--
-- C. Internal RLS Predicate Helpers:
--    - app_private.is_blocked_bidirectional(UUID, UUID)
--    -> Action: Located in app_private schema (non-PostgREST). Revoked from PUBLIC.

-- 6.1 Social Safety & Moderation RPCs
REVOKE ALL ON FUNCTION public.block_user(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.block_user(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.unblock_user(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.unblock_user(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.report_user(UUID, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.report_user(UUID, TEXT, TEXT, TEXT, TEXT) TO authenticated;

-- 6.2 Friendship Lifecycle RPCs
REVOKE ALL ON FUNCTION public.accept_friend_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_friend_request(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.decline_friend_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.decline_friend_request(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.cancel_friend_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.cancel_friend_request(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.unfriend_user(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.unfriend_user(UUID) TO authenticated;

-- 6.3 Chat & Messaging RPCs
REVOKE ALL ON FUNCTION public.mark_direct_messages_read(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.mark_direct_messages_read(UUID) TO authenticated;

-- 6.4 Push Notification Token RPCs
REVOKE ALL ON FUNCTION public.register_push_device_token(TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_push_device_token(TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.unregister_push_device_token(TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.unregister_push_device_token(TEXT, TEXT) TO authenticated;

-- 6.5 Internal Profile Trigger Function
REVOKE ALL ON FUNCTION public.sync_profile_user_id() FROM PUBLIC, anon, authenticated;

-- 6.6 Future Defense-in-Depth:
-- Ensure future functions created in schema public do not automatically grant EXECUTE to PUBLIC.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
