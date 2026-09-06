-- Migration 009: Production-grade Cloud-Authoritative Stamp Trade System
-- Base Migrations: 001 through 008

-- ===================================================
-- 1. STAMP TRADE REQUESTS TABLE & ROW LEVEL SECURITY
-- ===================================================

CREATE TABLE IF NOT EXISTS public.stamp_trade_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    recipient_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    source_stamp_id TEXT,
    stamp_id TEXT,
    source_object_name TEXT NOT NULL,
    stamp_media_path TEXT,
    stamp_title TEXT NOT NULL,
    stamp_name TEXT,
    stamp_shape TEXT NOT NULL DEFAULT 'RECTANGLE',
    location TEXT,
    note TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    destination_media_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_trade_no_self CHECK (sender_id IS NULL OR recipient_id IS NULL OR sender_id <> recipient_id),
    CONSTRAINT chk_trade_note_len CHECK (note IS NULL OR length(note) <= 1000)
);

ALTER TABLE public.stamp_trade_requests ADD COLUMN IF NOT EXISTS stamp_id TEXT;
ALTER TABLE public.stamp_trade_requests ADD COLUMN IF NOT EXISTS stamp_media_path TEXT;
ALTER TABLE public.stamp_trade_requests ADD COLUMN IF NOT EXISTS stamp_name TEXT;
ALTER TABLE public.stamp_trade_requests ADD COLUMN IF NOT EXISTS destination_media_path TEXT;

CREATE INDEX IF NOT EXISTS idx_stamp_trades_sender ON public.stamp_trade_requests(sender_id);
CREATE INDEX IF NOT EXISTS idx_stamp_trades_recipient ON public.stamp_trade_requests(recipient_id);
CREATE INDEX IF NOT EXISTS idx_stamp_trades_status ON public.stamp_trade_requests(status);

ALTER TABLE public.stamp_trade_requests ENABLE ROW LEVEL SECURITY;

-- Trade participants may read only trades they sent or received:
DROP POLICY IF EXISTS "Participants select trade requests" ON public.stamp_trade_requests;
CREATE POLICY "Participants select trade requests" ON public.stamp_trade_requests
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

-- Direct client INSERT / UPDATE / DELETE are forbidden; mutations must use SECURITY DEFINER RPCs / Edge Functions:
DROP POLICY IF EXISTS "Deny direct insert trade requests" ON public.stamp_trade_requests;
DROP POLICY IF EXISTS "Deny direct update trade requests" ON public.stamp_trade_requests;
DROP POLICY IF EXISTS "Deny direct delete trade requests" ON public.stamp_trade_requests;

GRANT SELECT ON public.stamp_trade_requests TO authenticated;


-- ===================================================
-- 2. RECEIVED TRADE STAMPS TABLE & ROW LEVEL SECURITY
-- ===================================================

CREATE TABLE IF NOT EXISTS public.received_trade_stamps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    recipient_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    source_trade_id UUID UNIQUE,
    trade_id UUID,
    original_sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    recipient_media_path TEXT NOT NULL,
    media_path TEXT,
    stamp_id TEXT,
    stamp_title TEXT NOT NULL,
    stamp_name TEXT,
    stamp_shape TEXT NOT NULL DEFAULT 'RECTANGLE',
    location TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.received_trade_stamps ADD COLUMN IF NOT EXISTS stamp_id TEXT;
ALTER TABLE public.received_trade_stamps ADD COLUMN IF NOT EXISTS media_path TEXT;
ALTER TABLE public.received_trade_stamps ADD COLUMN IF NOT EXISTS stamp_name TEXT;

CREATE INDEX IF NOT EXISTS idx_received_stamps_owner ON public.received_trade_stamps(owner_id);
CREATE INDEX IF NOT EXISTS idx_received_stamps_recipient ON public.received_trade_stamps(recipient_id);
CREATE INDEX IF NOT EXISTS idx_received_stamps_source_trade ON public.received_trade_stamps(source_trade_id);
CREATE INDEX IF NOT EXISTS idx_received_stamps_trade ON public.received_trade_stamps(trade_id);

ALTER TABLE public.received_trade_stamps ENABLE ROW LEVEL SECURITY;

-- Only recipient owner may select their received stamps:
DROP POLICY IF EXISTS "Owner select received trade stamps" ON public.received_trade_stamps;
CREATE POLICY "Owner select received trade stamps" ON public.received_trade_stamps
    FOR SELECT USING (auth.uid() = owner_id OR auth.uid() = recipient_id);

GRANT SELECT ON public.received_trade_stamps TO authenticated;


-- ===================================================
-- 3. STORAGE ROW LEVEL SECURITY FOR RECIPIENT COPIES
-- ===================================================
-- Ensure authenticated users can write received copies into their own folder <auth.uid()>/received/...
-- In migration 005: "Owner insert stamp media" already enforces split_part(name, '/', 1) = auth.uid()::text.
-- That policy naturally allows <auth.uid()>/received/<filename>.


-- ===================================================
-- 4. SERVER-AUTHORITATIVE SECURITY DEFINER RPCS
-- ===================================================

-- 4.1 Create Stamp Trade RPC
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

    -- Validate relationship is not blocked in either direction
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


-- 4.2 Decline Stamp Trade RPC
CREATE OR REPLACE FUNCTION public.decline_stamp_trade(p_trade_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_trade RECORD;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    SELECT * INTO v_trade
    FROM public.stamp_trade_requests
    WHERE id = p_trade_id
    FOR UPDATE;

    IF v_trade.id IS NULL THEN
        RAISE EXCEPTION 'Trade request not found';
    END IF;

    IF v_trade.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Trade request is not pending (status: %)', v_trade.status;
    END IF;

    IF v_trade.recipient_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only recipient can decline trade request';
    END IF;

    UPDATE public.stamp_trade_requests
    SET status = 'DECLINED',
        responded_at = now(),
        updated_at = now()
    WHERE id = p_trade_id;

    RETURN jsonb_build_object(
        'id', p_trade_id,
        'trade_id', p_trade_id,
        'status', 'DECLINED',
        'success', true
    );
END;
$$;


-- 4.3 Cancel Stamp Trade RPC
CREATE OR REPLACE FUNCTION public.cancel_stamp_trade(p_trade_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_trade RECORD;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    SELECT * INTO v_trade
    FROM public.stamp_trade_requests
    WHERE id = p_trade_id
    FOR UPDATE;

    IF v_trade.id IS NULL THEN
        RAISE EXCEPTION 'Trade request not found';
    END IF;

    IF v_trade.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Trade request is not pending (status: %)', v_trade.status;
    END IF;

    IF v_trade.sender_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only sender can cancel trade request';
    END IF;

    UPDATE public.stamp_trade_requests
    SET status = 'CANCELLED',
        responded_at = now(),
        updated_at = now()
    WHERE id = p_trade_id;

    RETURN jsonb_build_object(
        'id', p_trade_id,
        'trade_id', p_trade_id,
        'status', 'CANCELLED',
        'success', true
    );
END;
$$;


-- 4.4 Accept Stamp Trade RPC
CREATE OR REPLACE FUNCTION public.accept_stamp_trade(
    p_trade_id UUID,
    p_recipient_media_path TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, storage, app_private, pg_temp
AS $$
DECLARE
    v_acting_uid UUID;
    v_trade RECORD;
    v_media_path TEXT;
    v_rec_id UUID;
BEGIN
    v_acting_uid := auth.uid();
    IF v_acting_uid IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User authentication required';
    END IF;

    SELECT * INTO v_trade
    FROM public.stamp_trade_requests
    WHERE id = p_trade_id
    FOR UPDATE;

    IF v_trade.id IS NULL THEN
        RAISE EXCEPTION 'Trade request not found';
    END IF;

    -- Idempotency check: if already accepted, return existing record
    IF v_trade.status = 'ACCEPTED' THEN
        SELECT id INTO v_rec_id FROM public.received_trade_stamps WHERE source_trade_id = p_trade_id;
        IF v_rec_id IS NOT NULL THEN
            RETURN jsonb_build_object(
                'id', p_trade_id,
                'trade_id', p_trade_id,
                'status', 'ACCEPTED',
                'destination_media_path', v_trade.destination_media_path,
                'recipient_media_path', v_trade.destination_media_path,
                'received_stamp_id', v_rec_id,
                'success', true,
                'idempotent', true
            );
        END IF;
    END IF;

    IF v_trade.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Trade request is not pending (status: %)', v_trade.status;
    END IF;

    IF v_trade.recipient_id <> v_acting_uid THEN
        RAISE EXCEPTION 'Unauthorized: Only recipient can accept trade request';
    END IF;

    -- Require active friendship at acceptance time
    IF NOT EXISTS (
        SELECT 1 FROM public.friends
        WHERE (user_id_1 = v_trade.sender_id AND user_id_2 = v_acting_uid)
           OR (user_id_2 = v_trade.sender_id AND user_id_1 = v_acting_uid)
    ) THEN
        RAISE EXCEPTION 'Cannot accept trade: sender and recipient are not friends';
    END IF;

    -- Require no block relationship at acceptance time
    IF app_private.is_blocked_bidirectional(v_acting_uid, v_trade.sender_id) THEN
        RAISE EXCEPTION 'Cannot accept trade: relationship is blocked';
    END IF;

    -- Resolve recipient media path
    IF p_recipient_media_path IS NOT NULL AND trim(p_recipient_media_path) <> '' THEN
        v_media_path := trim(p_recipient_media_path);
    ELSE
        v_media_path := v_acting_uid::text || '/received/' || p_trade_id::text || '.png';
    END IF;

    -- Enforce recipient path starts with recipient UID
    IF split_part(v_media_path, '/', 1) <> v_acting_uid::text THEN
        RAISE EXCEPTION 'Recipient media path must belong to recipient';
    END IF;

    -- Ensure recipient media object exists in stamp-media storage
    IF NOT EXISTS (
        SELECT 1 FROM storage.objects
        WHERE bucket_id = 'stamp-media'
          AND name = v_media_path
    ) THEN
        RAISE EXCEPTION 'Recipient media copy not found in storage. Ensure media copy is created prior to trade acceptance.';
    END IF;

    -- Insert into received_trade_stamps
    INSERT INTO public.received_trade_stamps (
        owner_id,
        recipient_id,
        source_trade_id,
        trade_id,
        original_sender_id,
        recipient_media_path,
        media_path,
        stamp_title,
        stamp_name,
        stamp_shape,
        location,
        note
    )
    VALUES (
        v_acting_uid,
        v_acting_uid,
        p_trade_id,
        p_trade_id,
        v_trade.sender_id,
        v_media_path,
        v_media_path,
        v_trade.stamp_title,
        v_trade.stamp_title,
        v_trade.stamp_shape,
        v_trade.location,
        v_trade.note
    )
    ON CONFLICT (source_trade_id) DO UPDATE
        SET recipient_media_path = EXCLUDED.recipient_media_path,
            media_path = EXCLUDED.media_path
    RETURNING id INTO v_rec_id;

    -- Update trade request status
    UPDATE public.stamp_trade_requests
    SET status = 'ACCEPTED',
        destination_media_path = v_media_path,
        responded_at = now(),
        updated_at = now()
    WHERE id = p_trade_id;

    RETURN jsonb_build_object(
        'id', p_trade_id,
        'trade_id', p_trade_id,
        'status', 'ACCEPTED',
        'destination_media_path', v_media_path,
        'recipient_media_path', v_media_path,
        'received_stamp_id', v_rec_id,
        'success', true
    );
END;
$$;


-- ===================================================
-- 5. UPDATE BLOCK_USER RPC TO CANCEL PENDING TRADES
-- ===================================================

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
    v_cancelled_trades INT;
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

    -- 3. Atomically cancel any pending trades between the pair in either direction
    UPDATE public.stamp_trade_requests
    SET status = 'CANCELLED',
        updated_at = now(),
        responded_at = now()
    WHERE status = 'PENDING'
      AND ((sender_id = v_acting_uid AND recipient_id = p_blocked_id)
        OR (sender_id = p_blocked_id AND recipient_id = v_acting_uid));
    GET DIAGNOSTICS v_cancelled_trades = ROW_COUNT;

    -- 4. Idempotently insert block record
    INSERT INTO public.user_blocks (blocker_id, blocked_id)
    VALUES (v_acting_uid, p_blocked_id)
    ON CONFLICT (blocker_id, blocked_id) DO NOTHING;

    RETURN jsonb_build_object(
        'success', true,
        'blocker_id', v_acting_uid,
        'blocked_id', p_blocked_id,
        'deleted_friendships', v_deleted_friends,
        'deleted_requests', v_deleted_reqs,
        'cancelled_trades', v_cancelled_trades
    );
END;
$$;


-- ===================================================
-- 6. EXPLICIT LEAST-PRIVILEGE SECURITY DEFINER GRANTS
-- ===================================================

REVOKE ALL ON FUNCTION public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_stamp_trade(UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.decline_stamp_trade(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.decline_stamp_trade(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.cancel_stamp_trade(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.cancel_stamp_trade(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.accept_stamp_trade(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_stamp_trade(UUID, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.block_user(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.block_user(UUID) TO authenticated;


-- ===================================================
-- 7. REALTIME PUBLICATION FOR STAMP TRADES
-- ===================================================

ALTER TABLE public.stamp_trade_requests REPLICA IDENTITY FULL;

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
        AND c.relname = 'stamp_trade_requests'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.stamp_trade_requests;
    END IF;
  END IF;
END $$;
