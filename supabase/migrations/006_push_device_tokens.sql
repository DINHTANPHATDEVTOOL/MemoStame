-- Migration 006: Production Push Notifications Device Registry & Deduplication
-- Task #51: Secure device token registry, account reassignment, server dedupe, RLS

-- 1. Push Device Tokens Table
CREATE TABLE IF NOT EXISTS public.push_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('android', 'ios')),
    provider TEXT NOT NULL CHECK (provider IN ('fcm', 'apns')),
    token TEXT NOT NULL CHECK (length(trim(token)) > 0 AND length(token) <= 4096),
    installation_id TEXT NOT NULL CHECK (length(trim(installation_id)) > 0 AND length(installation_id) <= 256),
    environment TEXT NOT NULL DEFAULT 'production' CHECK (environment IN ('development', 'production')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT push_device_tokens_platform_provider_check CHECK (
        (platform = 'android' AND provider = 'fcm') OR
        (platform = 'ios' AND provider = 'apns')
    )
);

-- Unique index to prevent duplicate token across users/devices
CREATE UNIQUE INDEX IF NOT EXISTS push_device_tokens_provider_token_uidx
    ON public.push_device_tokens (provider, token);

-- Unique index on provider + installation_id to prevent multiple active accounts per install
CREATE UNIQUE INDEX IF NOT EXISTS push_device_tokens_provider_install_uidx
    ON public.push_device_tokens (provider, installation_id);

-- Lookup index for dispatching pushes to active recipient tokens
CREATE INDEX IF NOT EXISTS push_device_tokens_user_active_idx
    ON public.push_device_tokens (user_id)
    WHERE is_active = true;

-- 2. Server Internal Delivery Deduplication Table
CREATE TABLE IF NOT EXISTS public.push_delivery_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    recipient_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    dispatched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT push_delivery_events_dedupe_uniq UNIQUE(event_type, entity_id, recipient_user_id)
);

CREATE INDEX IF NOT EXISTS push_delivery_events_lookup_idx
    ON public.push_delivery_events (event_type, entity_id, recipient_user_id);

-- 3. Row Level Security Policies
ALTER TABLE public.push_device_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.push_delivery_events ENABLE ROW LEVEL SECURITY;

-- Device tokens: Users can only view their own registered tokens
CREATE POLICY push_device_tokens_select_own
    ON public.push_device_tokens
    FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

-- No public, anon, or direct client INSERT/UPDATE/DELETE policies.
-- Writes are mediated strictly via SECURITY DEFINER stored procedures.

-- Push delivery events: Strictly server-internal. Deny all client operations.
-- (service_role bypasses RLS).

-- 4. Registration RPC with Atomic Account Switch Reassignment
CREATE OR REPLACE FUNCTION public.register_push_device_token(
    p_platform TEXT,
    p_provider TEXT,
    p_token TEXT,
    p_installation_id TEXT,
    p_environment TEXT DEFAULT 'production'
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID;
    v_plat TEXT;
    v_prov TEXT;
    v_tok TEXT;
    v_inst TEXT;
    v_env TEXT;
BEGIN
    -- 1. Strictly derive authenticated caller UID
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'AUTH_REQUIRED: Caller must be authenticated to register push token';
    END IF;

    -- Clean inputs
    v_plat := lower(trim(coalesce(p_platform, '')));
    v_prov := lower(trim(coalesce(p_provider, '')));
    v_tok := trim(coalesce(p_token, ''));
    v_inst := trim(coalesce(p_installation_id, ''));
    v_env := lower(trim(coalesce(p_environment, 'production')));

    -- 2. Validate platform and provider pairing
    IF v_plat = 'android' AND v_prov <> 'fcm' THEN
        RAISE EXCEPTION 'INVALID_PROVIDER: Android platform must use fcm provider';
    ELSIF v_plat = 'ios' AND v_prov <> 'apns' THEN
        RAISE EXCEPTION 'INVALID_PROVIDER: iOS platform must use apns provider';
    ELSIF v_plat NOT IN ('android', 'ios') THEN
        RAISE EXCEPTION 'INVALID_PLATFORM: Platform must be android or ios';
    END IF;

    -- 3. Validate token and installation_id lengths
    IF length(v_tok) = 0 OR length(v_tok) > 4096 THEN
        RAISE EXCEPTION 'INVALID_TOKEN: Token must be non-empty and <= 4096 characters';
    END IF;

    IF length(v_inst) = 0 OR length(v_inst) > 256 THEN
        RAISE EXCEPTION 'INVALID_INSTALLATION_ID: Installation ID must be non-empty and <= 256 characters';
    END IF;

    IF v_env NOT IN ('development', 'production') THEN
        RAISE EXCEPTION 'INVALID_ENVIRONMENT: Environment must be development or production';
    END IF;

    -- 4. Account Switch Safety / Atomic Token Reassignment:
    -- Remove any token or installation_id associated with any OTHER user
    DELETE FROM public.push_device_tokens
    WHERE provider = v_prov
      AND (token = v_tok OR installation_id = v_inst)
      AND user_id <> v_uid;

    -- If the current user previously had a different token for this installation_id, delete old row
    DELETE FROM public.push_device_tokens
    WHERE user_id = v_uid
      AND provider = v_prov
      AND installation_id = v_inst
      AND token <> v_tok;

    -- 5. Upsert registration for current user
    INSERT INTO public.push_device_tokens (
        user_id,
        platform,
        provider,
        token,
        installation_id,
        environment,
        is_active,
        created_at,
        updated_at,
        last_seen_at
    ) VALUES (
        v_uid,
        v_plat,
        v_prov,
        v_tok,
        v_inst,
        v_env,
        true,
        now(),
        now(),
        now()
    )
    ON CONFLICT (provider, token) DO UPDATE SET
        user_id = v_uid,
        platform = v_plat,
        installation_id = v_inst,
        environment = v_env,
        is_active = true,
        updated_at = now(),
        last_seen_at = now();
END;
$$;

-- 5. Unregistration RPC
CREATE OR REPLACE FUNCTION public.unregister_push_device_token(
    p_provider TEXT,
    p_installation_id TEXT
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID;
    v_prov TEXT;
    v_inst TEXT;
BEGIN
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'AUTH_REQUIRED: Caller must be authenticated to unregister push token';
    END IF;

    v_prov := lower(trim(coalesce(p_provider, '')));
    v_inst := trim(coalesce(p_installation_id, ''));

    UPDATE public.push_device_tokens
    SET is_active = false,
        updated_at = now()
    WHERE user_id = v_uid
      AND provider = v_prov
      AND installation_id = v_inst;
END;
$$;

-- 6. Permissions
REVOKE ALL ON public.push_device_tokens FROM PUBLIC, anon;
GRANT SELECT ON public.push_device_tokens TO authenticated;

REVOKE ALL ON public.push_delivery_events FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.register_push_device_token(TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_push_device_token(TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

REVOKE ALL ON FUNCTION public.unregister_push_device_token(TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.unregister_push_device_token(TEXT, TEXT) TO authenticated;
