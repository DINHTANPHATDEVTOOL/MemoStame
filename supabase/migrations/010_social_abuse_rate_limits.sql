-- Migration 010: Production-grade Server-Authoritative Abuse Throttling & Rate Limits
-- Base Migrations: 001 through 009

-- ===================================================
-- 1. PRIVATE RATE LIMIT SCHEMA & CONFIGURATION TABLE
-- ===================================================
-- Ensure app_private schema exists (isolated from PostgREST public exposition)
CREATE SCHEMA IF NOT EXISTS app_private;
REVOKE ALL ON SCHEMA app_private FROM PUBLIC;
GRANT USAGE ON SCHEMA app_private TO postgres, authenticated, anon, service_role;

-- Rate limit configuration table: centrally declares limits with no client-side magic numbers
CREATE TABLE IF NOT EXISTS app_private.rate_limit_configs (
    action_type TEXT NOT NULL,
    tier TEXT NOT NULL DEFAULT 'default',
    max_requests INT NOT NULL,
    window_seconds INT NOT NULL,
    scope TEXT NOT NULL CHECK (scope IN ('actor', 'pair')),
    PRIMARY KEY (action_type, tier)
);

REVOKE ALL ON TABLE app_private.rate_limit_configs FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE app_private.rate_limit_configs TO postgres;

-- Seed production default quotas
INSERT INTO app_private.rate_limit_configs (action_type, tier, max_requests, window_seconds, scope)
VALUES
    -- Friend Requests: 10/10m burst, 50/24h daily, 3/10m rapid repeated to same target
    ('friend_request', 'burst', 10, 600, 'actor'),
    ('friend_request', 'daily', 50, 86400, 'actor'),
    ('friend_request', 'pair', 3, 600, 'pair'),

    -- Direct Messages: 60/min per actor, 30/min per recipient pair
    ('direct_message', 'actor', 60, 60, 'actor'),
    ('direct_message', 'pair', 30, 60, 'pair'),

    -- Feed Comments: 20/5m per actor
    ('feed_comment', 'actor', 20, 300, 'actor'),

    -- Feed Replies: 10 stamp replies/5m per actor
    ('feed_reply', 'actor', 10, 300, 'actor'),

    -- Abuse Reports: 5/hr per actor, 1/5m duplicate suppression per target
    ('report_user', 'actor', 5, 3600, 'actor'),
    ('report_user', 'pair', 1, 300, 'pair'),

    -- Stamp Trade Creation: 10 new trades/hr per actor
    ('stamp_trade', 'actor', 10, 3600, 'actor')
ON CONFLICT (action_type, tier) DO UPDATE SET
    max_requests = EXCLUDED.max_requests,
    window_seconds = EXCLUDED.window_seconds,
    scope = EXCLUDED.scope;


-- ===================================================
-- 2. CONCURRENCY-SAFE RATE LIMIT BUCKET LEDGER
-- ===================================================

CREATE TABLE IF NOT EXISTS app_private.rate_limit_buckets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    action_type TEXT NOT NULL,
    target_id TEXT NOT NULL DEFAULT '',
    window_start TIMESTAMPTZ NOT NULL,
    bucket_interval_seconds INT NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rate_limit_bucket UNIQUE (actor_id, action_type, target_id, window_start)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_buckets_window ON app_private.rate_limit_buckets(window_start);
CREATE INDEX IF NOT EXISTS idx_rate_limit_buckets_actor ON app_private.rate_limit_buckets(actor_id, action_type);

REVOKE ALL ON TABLE app_private.rate_limit_buckets FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE app_private.rate_limit_buckets TO postgres;


-- ===================================================
-- 3. INTERNAL ENFORCEMENT & MAINTENANCE FUNCTIONS
-- ===================================================

CREATE OR REPLACE FUNCTION app_private.enforce_rate_limit(
    p_actor_id UUID,
    p_action_type TEXT,
    p_target_id TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
DECLARE
    v_clean_action TEXT;
    v_clean_target TEXT;
    v_cfg RECORD;
    v_target_key TEXT;
    v_window_start TIMESTAMPTZ;
    v_current_count INT;
    v_retry_after INT;
    v_now TIMESTAMPTZ;
BEGIN
    IF p_actor_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Actor identity required for rate limit enforcement';
    END IF;

    v_clean_action := lower(trim(p_action_type));
    v_clean_target := COALESCE(trim(p_target_id), '');
    v_now := clock_timestamp();

    -- Iterate through active configs for this action type ordered by window duration
    FOR v_cfg IN
        SELECT tier, max_requests, window_seconds, scope
        FROM app_private.rate_limit_configs
        WHERE action_type = v_clean_action
        ORDER BY window_seconds ASC
    LOOP
        IF v_cfg.scope = 'pair' THEN
            -- Only enforce pair limit if target is provided
            IF v_clean_target = '' THEN
                CONTINUE;
            END IF;
            v_target_key := v_clean_target;
        ELSE
            v_target_key := '';
        END IF;

        -- Fixed window aligned to epoch interval
        v_window_start := to_timestamp(floor(extract(epoch from v_now) / v_cfg.window_seconds) * v_cfg.window_seconds);

        -- Atomic UPSERT to increment counter safely under high concurrency
        INSERT INTO app_private.rate_limit_buckets (
            actor_id,
            action_type,
            target_id,
            window_start,
            bucket_interval_seconds,
            request_count,
            updated_at
        )
        VALUES (
            p_actor_id,
            v_clean_action,
            v_target_key,
            v_window_start,
            v_cfg.window_seconds,
            1,
            v_now
        )
        ON CONFLICT (actor_id, action_type, target_id, window_start)
        DO UPDATE SET
            request_count = app_private.rate_limit_buckets.request_count + 1,
            updated_at = v_now
        RETURNING request_count INTO v_current_count;

        -- Check quota limit
        IF v_current_count > v_cfg.max_requests THEN
            v_retry_after := GREATEST(1, CEIL(EXTRACT(EPOCH FROM (v_window_start + (v_cfg.window_seconds * interval '1 second') - v_now)))::INT);
            RAISE EXCEPTION 'RATE_LIMITED: % quota exceeded (% tier). Please retry after % seconds.',
                v_clean_action, v_cfg.tier, v_retry_after
                USING ERRCODE = 'P0001',
                      DETAIL = jsonb_build_object(
                          'error', 'RATE_LIMITED',
                          'action', v_clean_action,
                          'tier', v_cfg.tier,
                          'retry_after_seconds', v_retry_after
                      )::text;
        END IF;
    END LOOP;
END;
$$;

REVOKE ALL ON FUNCTION app_private.enforce_rate_limit(UUID, TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION app_private.enforce_rate_limit(UUID, TEXT, TEXT) TO postgres;

-- Bounded growth maintenance function
CREATE OR REPLACE FUNCTION app_private.cleanup_expired_rate_limit_buckets()
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = app_private, pg_temp
AS $$
DECLARE
    v_deleted INT;
BEGIN
    DELETE FROM app_private.rate_limit_buckets
    WHERE window_start < now() - interval '2 days';
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

REVOKE ALL ON FUNCTION app_private.cleanup_expired_rate_limit_buckets() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION app_private.cleanup_expired_rate_limit_buckets() TO postgres;


-- ===================================================
-- 4. SOCIAL MUTATION TRIGGERS (BEFORE INSERT)
-- ===================================================

-- 4.1 Friend Requests Trigger
CREATE OR REPLACE FUNCTION app_private.tg_enforce_friend_request_rate_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
DECLARE
    v_actor UUID;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required' USING ERRCODE = '42501';
    END IF;

    -- Strict actor identity: sender_id must be caller
    NEW.sender_id := v_actor;

    -- Self-request check
    IF NEW.sender_id = NEW.recipient_id THEN
        RAISE EXCEPTION 'Cannot send friend request to oneself' USING ERRCODE = '23514';
    END IF;

    -- Block check strictly precedes rate limit
    IF app_private.is_blocked_bidirectional(NEW.sender_id, NEW.recipient_id) THEN
        RAISE EXCEPTION 'Cannot send friend request: relationship is blocked' USING ERRCODE = '42501';
    END IF;

    -- Enforce rate limits
    PERFORM app_private.enforce_rate_limit(NEW.sender_id, 'friend_request', NEW.recipient_id::text);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_friend_request_rate_limit ON public.friend_requests;
CREATE TRIGGER trg_friend_request_rate_limit
    BEFORE INSERT ON public.friend_requests
    FOR EACH ROW
    EXECUTE FUNCTION app_private.tg_enforce_friend_request_rate_limit();


-- 4.2 Direct Messages Trigger
CREATE OR REPLACE FUNCTION app_private.tg_enforce_direct_message_rate_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
DECLARE
    v_actor UUID;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required' USING ERRCODE = '42501';
    END IF;

    -- Strict actor identity: sender_id must be caller
    NEW.sender_id := v_actor;

    -- Block check strictly precedes rate limit
    IF app_private.is_blocked_bidirectional(NEW.sender_id, NEW.recipient_id) THEN
        RAISE EXCEPTION 'Cannot send direct message: relationship is blocked' USING ERRCODE = '42501';
    END IF;

    -- Enforce rate limits (both global 60/min and pair 30/min)
    PERFORM app_private.enforce_rate_limit(NEW.sender_id, 'direct_message', NEW.recipient_id::text);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_direct_message_rate_limit ON public.direct_messages;
CREATE TRIGGER trg_direct_message_rate_limit
    BEFORE INSERT ON public.direct_messages
    FOR EACH ROW
    EXECUTE FUNCTION app_private.tg_enforce_direct_message_rate_limit();


-- 4.3 Feed Comments Trigger
CREATE OR REPLACE FUNCTION app_private.tg_enforce_feed_comment_rate_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
DECLARE
    v_actor UUID;
    v_post_author UUID;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required' USING ERRCODE = '42501';
    END IF;

    NEW.author_id := v_actor;

    -- Check if post exists and check block relation
    SELECT author_id INTO v_post_author
    FROM public.feed_posts
    WHERE id = NEW.post_id;

    IF v_post_author IS NOT NULL AND app_private.is_blocked_bidirectional(v_actor, v_post_author) THEN
        RAISE EXCEPTION 'Cannot comment: relationship is blocked' USING ERRCODE = '42501';
    END IF;

    -- Enforce rate limits (20 comments / 5m)
    PERFORM app_private.enforce_rate_limit(v_actor, 'feed_comment', NEW.post_id);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_feed_comment_rate_limit ON public.feed_comments;
CREATE TRIGGER trg_feed_comment_rate_limit
    BEFORE INSERT ON public.feed_comments
    FOR EACH ROW
    EXECUTE FUNCTION app_private.tg_enforce_feed_comment_rate_limit();


-- 4.4 Feed Replies Trigger
CREATE OR REPLACE FUNCTION app_private.tg_enforce_feed_reply_rate_limit()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
DECLARE
    v_actor UUID;
    v_post_author UUID;
BEGIN
    v_actor := auth.uid();
    IF v_actor IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required' USING ERRCODE = '42501';
    END IF;

    NEW.author_id := v_actor;

    -- Check if post exists and check block relation
    SELECT author_id INTO v_post_author
    FROM public.feed_posts
    WHERE id = NEW.post_id;

    IF v_post_author IS NOT NULL AND app_private.is_blocked_bidirectional(v_actor, v_post_author) THEN
        RAISE EXCEPTION 'Cannot reply: relationship is blocked' USING ERRCODE = '42501';
    END IF;

    -- Enforce rate limits (10 replies / 5m)
    PERFORM app_private.enforce_rate_limit(v_actor, 'feed_reply', NEW.post_id);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_feed_reply_rate_limit ON public.feed_replies;
CREATE TRIGGER trg_feed_reply_rate_limit
    BEFORE INSERT ON public.feed_replies
    FOR EACH ROW
    EXECUTE FUNCTION app_private.tg_enforce_feed_reply_rate_limit();

-- Explicit least-privilege for trigger functions (called implicitly during authenticated INSERT)
REVOKE ALL ON FUNCTION app_private.tg_enforce_friend_request_rate_limit() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION app_private.tg_enforce_friend_request_rate_limit() TO authenticated;

REVOKE ALL ON FUNCTION app_private.tg_enforce_direct_message_rate_limit() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION app_private.tg_enforce_direct_message_rate_limit() TO authenticated;

REVOKE ALL ON FUNCTION app_private.tg_enforce_feed_comment_rate_limit() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION app_private.tg_enforce_feed_comment_rate_limit() TO authenticated;

REVOKE ALL ON FUNCTION app_private.tg_enforce_feed_reply_rate_limit() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION app_private.tg_enforce_feed_reply_rate_limit() TO authenticated;


-- ===================================================
-- 5. RATE-LIMITED RPCS: REPORT_USER & CREATE_STAMP_TRADE
-- ===================================================

-- 5.1 Report User RPC
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
SET search_path = public, app_private, pg_temp
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

    -- Enforce rate limits (5 reports/hr actor quota & 1 report/5m duplicate suppression)
    PERFORM app_private.enforce_rate_limit(v_acting_uid, 'report_user', p_reported_user_id::text);

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

REVOKE ALL ON FUNCTION public.report_user(UUID, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.report_user(UUID, TEXT, TEXT, TEXT, TEXT) TO authenticated;


-- 5.2 Create Stamp Trade RPC
DROP FUNCTION IF EXISTS public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.create_stamp_trade(
    p_recipient_id UUID,
    p_source_object_name TEXT DEFAULT NULL,
    p_stamp_title TEXT DEFAULT NULL,
    p_stamp_shape TEXT DEFAULT 'RECTANGLE',
    p_location TEXT DEFAULT NULL,
    p_note TEXT DEFAULT NULL,
    p_source_stamp_id TEXT DEFAULT NULL,
    p_stamp_id TEXT DEFAULT NULL,
    p_stamp_name TEXT DEFAULT NULL,
    p_stamp_media_path TEXT DEFAULT NULL,
    p_stamp_category TEXT DEFAULT NULL,
    p_stamp_svg TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, storage, app_private, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_trade_id UUID;
    v_clean_title TEXT;
    v_clean_shape TEXT;
    v_clean_note TEXT;
    v_clean_source_name TEXT;
    v_source_name TEXT;
    v_title TEXT;
    v_source_stamp TEXT;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    IF v_acting_uid = p_recipient_id THEN
        RAISE EXCEPTION 'Cannot trade with oneself';
    END IF;

    -- Validate recipient profile exists
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_recipient_id) THEN
        RAISE EXCEPTION 'Recipient user not found';
    END IF;

    -- Validate active friendship exists
    IF NOT EXISTS (
        SELECT 1 FROM public.friends
        WHERE (user_id_1 = v_acting_uid AND user_id_2 = p_recipient_id)
           OR (user_id_2 = v_acting_uid AND user_id_1 = p_recipient_id)
    ) THEN
        RAISE EXCEPTION 'Recipient must be an active friend';
    END IF;

    -- Block check strictly precedes rate limit
    IF app_private.is_blocked_bidirectional(v_acting_uid, p_recipient_id) THEN
        RAISE EXCEPTION 'Cannot trade: relationship is blocked';
    END IF;

    -- Resolve source object name
    v_source_name := COALESCE(p_source_object_name, p_stamp_media_path);
    IF v_source_name IS NULL THEN
        RAISE EXCEPTION 'Source object name is required';
    END IF;

    v_clean_source_name := trim(v_source_name);
    IF v_clean_source_name = '' THEN
        RAISE EXCEPTION 'Source object name cannot be empty';
    END IF;

    -- Reject local, data, blob, or external web URLs
    IF v_clean_source_name ~* '^(https?://|file://|content://|data:|blob:)' THEN
        RAISE EXCEPTION 'Local or external media URLs are not permitted; must be a valid stamp-media object name';
    END IF;

    -- Reject path traversal or leading slash
    IF v_clean_source_name LIKE '%..%' OR v_clean_source_name LIKE '/%' THEN
        RAISE EXCEPTION 'Invalid media object name format';
    END IF;

    -- Validate sender ownership: first path segment must equal caller UID
    IF split_part(v_clean_source_name, '/', 1) <> v_acting_uid::text THEN
        RAISE EXCEPTION 'Source media must belong to sender';
    END IF;

    -- Verify source object actually exists in stamp-media bucket
    IF NOT EXISTS (
        SELECT 1 FROM storage.objects
        WHERE bucket_id = 'stamp-media'
          AND name = v_clean_source_name
    ) THEN
        RAISE EXCEPTION 'Source media object not found in stamp-media';
    END IF;

    -- Resolve title
    v_title := COALESCE(p_stamp_title, p_stamp_name);
    IF v_title IS NULL OR trim(v_title) = '' THEN
        RAISE EXCEPTION 'Stamp title is required';
    END IF;
    v_clean_title := trim(v_title);

    v_clean_shape := COALESCE(nullif(trim(p_stamp_shape), ''), 'RECTANGLE');
    v_source_stamp := COALESCE(p_source_stamp_id, p_stamp_id);

    IF p_note IS NOT NULL THEN
        v_clean_note := trim(p_note);
        IF length(v_clean_note) > 1000 THEN
            RAISE EXCEPTION 'Note exceeds maximum allowed length of 1000 characters';
        END IF;
        IF v_clean_note = '' THEN v_clean_note := NULL; END IF;
    ELSE
        v_clean_note := NULL;
    END IF;

    -- Enforce rate limits (10 trades/hr quota)
    PERFORM app_private.enforce_rate_limit(v_acting_uid, 'stamp_trade', p_recipient_id::text);

    INSERT INTO public.stamp_trade_requests (
        sender_id,
        recipient_id,
        source_stamp_id,
        stamp_id,
        source_object_name,
        stamp_media_path,
        stamp_title,
        stamp_name,
        stamp_shape,
        location,
        note,
        status
    )
    VALUES (
        v_acting_uid,
        p_recipient_id,
        v_source_stamp,
        v_source_stamp,
        v_clean_source_name,
        v_clean_source_name,
        v_clean_title,
        v_clean_title,
        v_clean_shape,
        trim(p_location),
        v_clean_note,
        'PENDING'
    )
    RETURNING id INTO v_trade_id;

    RETURN jsonb_build_object(
        'id', v_trade_id,
        'trade_id', v_trade_id,
        'sender_id', v_acting_uid,
        'recipient_id', p_recipient_id,
        'source_stamp_id', v_source_stamp,
        'stamp_id', v_source_stamp,
        'source_object_name', v_clean_source_name,
        'stamp_media_path', v_clean_source_name,
        'stamp_title', v_clean_title,
        'stamp_name', v_clean_title,
        'stamp_shape', v_clean_shape,
        'location', trim(p_location),
        'note', v_clean_note,
        'status', 'PENDING',
        'success', true,
        'created_at', now(),
        'updated_at', now()
    );
END;
$$;

REVOKE ALL ON FUNCTION public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
