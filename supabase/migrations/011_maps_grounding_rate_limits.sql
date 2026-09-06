-- Migration 011: Server-Side Abuse Rate Limits for Maps Grounding
-- Base Migrations: 001 through 010

-- ===================================================
-- 1. SEED RATE LIMIT CONFIGURATIONS FOR MAPS GROUNDING
-- ===================================================
-- SEARCH_PLACES: 20 requests / 10 minutes per authenticated user
-- GENERATE_POSTMARK_STORY: 10 requests / 10 minutes per authenticated user
INSERT INTO app_private.rate_limit_configs (action_type, tier, max_requests, window_seconds, scope)
VALUES
    ('maps_grounding_search', 'actor', 20, 600, 'actor'),
    ('maps_grounding_story', 'actor', 10, 600, 'actor')
ON CONFLICT (action_type, tier) DO UPDATE SET
    max_requests = EXCLUDED.max_requests,
    window_seconds = EXCLUDED.window_seconds,
    scope = EXCLUDED.scope;


-- ===================================================
-- 2. SERVER-AUTHORITATIVE RPC FOR EDGE FUNCTIONS
-- ===================================================
-- Executable strictly by service_role (Edge Functions with server credentials).
-- Normal clients (anon, authenticated, PUBLIC) have execution revoked.
CREATE OR REPLACE FUNCTION public.enforce_maps_grounding_rate_limit(
    p_actor_id UUID,
    p_action_type TEXT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, app_private, pg_temp
AS $$
BEGIN
    IF p_actor_id IS NULL THEN
        RAISE EXCEPTION 'Actor ID is required for rate limit verification' USING ERRCODE = '42501';
    END IF;

    IF p_action_type NOT IN ('maps_grounding_search', 'maps_grounding_story') THEN
        RAISE EXCEPTION 'Invalid maps grounding action type: %', p_action_type;
    END IF;

    -- Delegate to internal atomic UPSERT rate limiter in app_private schema
    PERFORM app_private.enforce_rate_limit(p_actor_id, p_action_type, '');
END;
$$;

-- Restrict privileges: server-only execution
REVOKE ALL ON FUNCTION public.enforce_maps_grounding_rate_limit(UUID, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.enforce_maps_grounding_rate_limit(UUID, TEXT) TO service_role;
