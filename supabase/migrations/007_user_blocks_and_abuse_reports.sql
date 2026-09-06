-- Migration 007: Production-grade User Blocking, Abuse Reporting & Social Safety
-- Base Migrations: 001_auth_social_rls.sql, 002_schema_contract_and_rls_hardening.sql,
--                  003_deployment_safety_and_id_contract.sql, 004_add_direct_messages_realtime_publication.sql,
--                  005_media_storage_and_feed_replies.sql, 006_push_device_tokens.sql

-- ===================================================
-- 1. USER BLOCKS TABLE & ROW LEVEL SECURITY
-- ===================================================

CREATE TABLE IF NOT EXISTS public.user_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_blocks_no_self CHECK (blocker_id <> blocked_id),
    CONSTRAINT uq_user_blocks_blocker_blocked UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker_id ON public.user_blocks (blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked_id ON public.user_blocks (blocked_id);

ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;

-- Privacy enforcement: Users may read ONLY their own outbound blocks.
-- A blocked user MUST NOT be able to enumerate who blocked them.
DROP POLICY IF EXISTS "Users select own outbound blocks" ON public.user_blocks;
CREATE POLICY "Users select own outbound blocks" ON public.user_blocks
    FOR SELECT USING (auth.uid() = blocker_id);

-- Direct client INSERT / UPDATE / DELETE are forbidden; mutations must use SECURITY DEFINER RPCs.
DROP POLICY IF EXISTS "Deny direct insert user blocks" ON public.user_blocks;
DROP POLICY IF EXISTS "Deny direct update user blocks" ON public.user_blocks;
DROP POLICY IF EXISTS "Deny direct delete user blocks" ON public.user_blocks;

GRANT SELECT ON public.user_blocks TO authenticated;


-- ===================================================
-- 2. USER REPORTS / ABUSE REPORTS TABLE & ROW LEVEL SECURITY
-- ===================================================

CREATE TABLE IF NOT EXISTS public.user_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reported_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    category TEXT NOT NULL CHECK (category IN ('spam', 'harassment', 'impersonation', 'inappropriate_content', 'other')),
    entity_type TEXT DEFAULT NULL,
    entity_id TEXT DEFAULT NULL,
    note TEXT DEFAULT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_user_reports_note_len CHECK (note IS NULL OR length(note) <= 1000),
    CONSTRAINT chk_user_reports_no_self CHECK (reporter_id IS NULL OR reporter_id <> reported_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_reports_reporter_id ON public.user_reports (reporter_id);
CREATE INDEX IF NOT EXISTS idx_user_reports_reported_user_id ON public.user_reports (reported_user_id);
CREATE INDEX IF NOT EXISTS idx_user_reports_status ON public.user_reports (status);

ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;

-- Reports are strictly moderation/server data. Normal clients have ZERO select access.
DROP POLICY IF EXISTS "Deny client select user reports" ON public.user_reports;
-- By enabling RLS without granting SELECT to authenticated/anon, direct client SELECT yields 0 rows.


-- ===================================================
-- 3. SECURITY DEFINER RPCS FOR SOCIAL SAFETY
-- ===================================================

-- 3.1 Block User RPC:
-- Atomically removes friendship and pending requests, then idempotently records block.
CREATE OR REPLACE FUNCTION public.block_user(p_blocked_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_deleted_friends INT;
    v_deleted_reqs INT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    IF v_acting_uid = p_blocked_id THEN
        RAISE EXCEPTION 'Cannot block oneself';
    END IF;

    -- Require target profile exists
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_blocked_id) THEN
        RAISE EXCEPTION 'User not found';
    END IF;

    -- 1. Atomically delete existing friendship between acting user and target in both directions
    DELETE FROM public.friends
    WHERE (user_id_1 = v_acting_uid AND user_id_2 = p_blocked_id)
       OR (user_id_2 = v_acting_uid AND user_id_1 = p_blocked_id);
    GET DIAGNOSTICS v_deleted_friends = ROW_COUNT;

    -- 2. Atomically delete/cancel any pending friend requests in either direction
    DELETE FROM public.friend_requests
    WHERE (sender_id = v_acting_uid AND recipient_id = p_blocked_id)
       OR (sender_id = p_blocked_id AND recipient_id = v_acting_uid);
    GET DIAGNOSTICS v_deleted_reqs = ROW_COUNT;

    -- 3. Idempotently insert block record
    INSERT INTO public.user_blocks (blocker_id, blocked_id)
    VALUES (v_acting_uid, p_blocked_id)
    ON CONFLICT (blocker_id, blocked_id) DO NOTHING;

    RETURN jsonb_build_object(
        'success', true,
        'blocker_id', v_acting_uid,
        'blocked_id', p_blocked_id,
        'deleted_friendships', v_deleted_friends,
        'deleted_requests', v_deleted_reqs
    );
END;
$$;


-- 3.2 Unblock User RPC:
-- Removes the block record. Restores permission to contact only; DOES NOT restore past friendship.
CREATE OR REPLACE FUNCTION public.unblock_user(p_blocked_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_deleted_blocks INT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    DELETE FROM public.user_blocks
    WHERE blocker_id = v_acting_uid AND blocked_id = p_blocked_id;
    GET DIAGNOSTICS v_deleted_blocks = ROW_COUNT;

    RETURN jsonb_build_object(
        'success', true,
        'blocker_id', v_acting_uid,
        'unblocked_id', p_blocked_id,
        'unblocked', (v_deleted_blocks > 0)
    );
END;
$$;


-- 3.3 Report User RPC:
-- Derives reporter strictly from auth.uid(). Validates bounded categories and length-limited note.
CREATE OR REPLACE FUNCTION public.report_user(
    p_reported_user_id UUID,
    p_category TEXT,
    p_note TEXT DEFAULT NULL,
    p_entity_type TEXT DEFAULT NULL,
    p_entity_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_report_id UUID;
    v_clean_category TEXT;
    v_clean_note TEXT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    IF v_acting_uid = p_reported_user_id THEN
        RAISE EXCEPTION 'Cannot report oneself';
    END IF;

    v_clean_category := lower(trim(p_category));
    IF v_clean_category NOT IN ('spam', 'harassment', 'impersonation', 'inappropriate_content', 'other') THEN
        RAISE EXCEPTION 'Invalid report category: %', p_category;
    END IF;

    IF p_note IS NOT NULL THEN
        v_clean_note := trim(p_note);
        IF length(v_clean_note) > 1000 THEN
            RAISE EXCEPTION 'Report note exceeds maximum allowed length of 1000 characters';
        END IF;
        IF v_clean_note = '' THEN
            v_clean_note := NULL;
        END IF;
    ELSE
        v_clean_note := NULL;
    END IF;

    -- Require reported profile exists
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_reported_user_id) THEN
        RAISE EXCEPTION 'Reported user not found';
    END IF;

    INSERT INTO public.user_reports (
        reporter_id,
        reported_user_id,
        category,
        entity_type,
        entity_id,
        note,
        status
    )
    VALUES (
        v_acting_uid,
        p_reported_user_id,
        v_clean_category,
        p_entity_type,
        p_entity_id,
        v_clean_note,
        'PENDING'
    )
    RETURNING id INTO v_report_id;

    RETURN jsonb_build_object(
        'success', true,
        'report_id', v_report_id,
        'status', 'PENDING'
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.block_user(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.unblock_user(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.report_user(UUID, TEXT, TEXT, TEXT, TEXT) TO authenticated;


-- ===================================================
-- 4. SOCIAL & FEED RLS POLICIES HARDENED WITH BLOCK CHECKS
-- ===================================================

-- 4.1 Friend Requests: Deny new requests if blocked in either direction
DROP POLICY IF EXISTS "Sender insert friend request" ON public.friend_requests;
CREATE POLICY "Sender insert friend request" ON public.friend_requests
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id 
        AND sender_id <> recipient_id
        AND NOT EXISTS (
            SELECT 1 FROM public.user_blocks ub
            WHERE (ub.blocker_id = sender_id AND ub.blocked_id = recipient_id)
               OR (ub.blocker_id = recipient_id AND ub.blocked_id = sender_id)
        )
    );

-- 4.2 Accept Friend Request RPC: Reject if blocked in either direction
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
    IF EXISTS (
        SELECT 1 FROM public.user_blocks ub
        WHERE (ub.blocker_id = v_acting_uid AND ub.blocked_id = v_req.sender_id)
           OR (ub.blocker_id = v_req.sender_id AND ub.blocked_id = v_acting_uid)
    ) THEN
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

-- 4.3 Direct Messages: Deny new DMs if blocked in either direction
DROP POLICY IF EXISTS "Sender insert direct message" ON public.direct_messages;
CREATE POLICY "Sender insert direct message" ON public.direct_messages
    FOR INSERT WITH CHECK (
        auth.uid() = sender_id
        AND NOT EXISTS (
            SELECT 1 FROM public.user_blocks ub
            WHERE (ub.blocker_id = sender_id AND ub.blocked_id = recipient_id)
               OR (ub.blocker_id = recipient_id AND ub.blocked_id = sender_id)
        )
    );

-- 4.4 Feed Reactions: Deny reaction if post author and reactor have a block relation
DROP POLICY IF EXISTS "User insert feed reaction" ON public.feed_reactions;
CREATE POLICY "User insert feed reaction" ON public.feed_reactions
    FOR INSERT WITH CHECK (
        auth.uid() = user_id
        AND NOT EXISTS (
            SELECT 1 FROM public.feed_posts fp
            JOIN public.user_blocks ub 
              ON (ub.blocker_id = user_id AND ub.blocked_id = fp.author_id)
              OR (ub.blocker_id = fp.author_id AND ub.blocked_id = user_id)
            WHERE fp.id::text = feed_reactions.post_id::text
        )
    );

-- 4.5 Feed Comments: Deny comment if post author or parent comment author is blocked
DROP POLICY IF EXISTS "Author insert feed comment" ON public.feed_comments;
CREATE POLICY "Author insert feed comment" ON public.feed_comments
    FOR INSERT WITH CHECK (
        auth.uid() = author_id
        AND NOT EXISTS (
            SELECT 1 FROM public.feed_posts fp
            JOIN public.user_blocks ub 
              ON (ub.blocker_id = author_id AND ub.blocked_id = fp.author_id)
              OR (ub.blocker_id = fp.author_id AND ub.blocked_id = author_id)
            WHERE fp.id::text = feed_comments.post_id::text
        )
        AND (
            parent_comment_id IS NULL
            OR NOT EXISTS (
                SELECT 1 FROM public.feed_comments pc
                JOIN public.user_blocks ub 
                  ON (ub.blocker_id = author_id AND ub.blocked_id = pc.author_id)
                  OR (ub.blocker_id = pc.author_id AND ub.blocked_id = author_id)
                WHERE pc.id::text = feed_comments.parent_comment_id::text
            )
        )
    );

-- 4.6 Feed Replies: Deny reply if post author and replier have a block relation
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
            JOIN public.user_blocks ub 
              ON (ub.blocker_id = author_id AND ub.blocked_id = fp.author_id)
              OR (ub.blocker_id = fp.author_id AND ub.blocked_id = author_id)
            WHERE fp.id = feed_replies.post_id
        )
    );
