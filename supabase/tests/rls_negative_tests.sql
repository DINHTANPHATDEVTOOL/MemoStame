-- Standalone Real RLS Executable Test Script for Supabase / PostgreSQL
-- Run with: psql $DATABASE_URL -f supabase/tests/rls_negative_tests.sql
-- Fixtures created under superuser (postgres), assertions execute strictly under ROLE authenticated.

-- ===================================================
-- 1. FIXTURE PREPARATION (ADMIN / SUPERUSER ROLE)
-- ===================================================
SET ROLE postgres;

-- Ensure auth schema and auth.users exist for raw postgres local test environment
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY,
    instance_id UUID DEFAULT '00000000-0000-0000-0000-000000000000'::uuid,
    email TEXT UNIQUE,
    encrypted_password TEXT DEFAULT '',
    email_confirmed_at TIMESTAMPTZ DEFAULT now(),
    raw_app_meta_data JSONB DEFAULT '{"provider":"email","providers":["email"]}'::jsonb,
    raw_user_meta_data JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    role TEXT DEFAULT 'authenticated',
    aud TEXT DEFAULT 'authenticated'
);

-- Cleanup previous test data
DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM auth.users WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');

-- Insert auth.users
INSERT INTO auth.users (id, email) VALUES
    ('11111111-1111-1111-1111-111111111111', 'usera@test.local'),
    ('22222222-2222-2222-2222-222222222222', 'userb@test.local'),
    ('33333333-3333-3333-3333-333333333333', 'userc@test.local')
ON CONFLICT (id) DO NOTHING;

-- Insert profiles
INSERT INTO public.profiles (id, username, display_name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'user_a', 'User A'),
    ('22222222-2222-2222-2222-222222222222', 'user_b', 'User B'),
    ('33333333-3333-3333-3333-333333333333', 'user_c', 'User C');

-- Insert friend request A -> B
INSERT INTO public.friend_requests (id, sender_id, recipient_id, status) VALUES
    ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'PENDING');

-- Insert DM A -> B
INSERT INTO public.direct_messages (id, sender_id, recipient_id, text, is_read) VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Hello B', false);

-- Insert Feed Posts by A
INSERT INTO public.feed_posts (id, author_id, caption, audience_type) VALUES
    ('post_a_only_me', '11111111-1111-1111-1111-111111111111', 'Private Note', 'ONLY_ME'),
    ('post_a_friends', '11111111-1111-1111-1111-111111111111', 'Friends Note', 'FRIENDS');


-- ===================================================
-- 2. EXECUTABLE RLS ASSERTIONS (ROLE: AUTHENTICATED)
-- ===================================================

-- Test 1: A modifies A profile -> allowed (1 row updated)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    UPDATE public.profiles SET bio = 'Bio updated by A' WHERE id = '11111111-1111-1111-1111-111111111111';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 1 THEN
        RAISE EXCEPTION 'RLS Failure: User A could not update own profile';
    END IF;
END $$;

-- Test 2: A modifies B profile -> denied (0 rows updated)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    UPDATE public.profiles SET bio = 'Hacked by A' WHERE id = '22222222-2222-2222-2222-222222222222';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: A updated B profile (updated % rows)', v_c;
    END IF;
END $$;

-- Test 3: A sends request sender=A -> allowed
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'PENDING');
END $$;

-- Test 4: A sends request pretending sender=B -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('77777777-7777-7777-7777-777777777777', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'PENDING');
    RAISE EXCEPTION 'RLS Leak: A inserted request pretending sender=B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;

-- Test 5: Sender A accepts own request -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'RLS Leak: Sender A accepted own request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;

-- Test 6: Recipient B accepts request -> allowed
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
END $$;

-- Test 7: Third user C accepts A-B request -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'RLS Leak: Third user C accepted A-B request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;

-- Test 8: C reads A-B request -> denied (0 rows)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.friend_requests WHERE id = '44444444-4444-4444-4444-444444444444';
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: C read A-B request (count: %)', v_c;
    END IF;
END $$;

-- Test 9: C reads A-B direct message -> denied (0 rows)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.direct_messages WHERE id = '55555555-5555-5555-5555-555555555555';
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: C read A-B DM (count: %)', v_c;
    END IF;
END $$;

-- Test 10: Recipient B edits DM text directly -> denied (0 rows updated)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    UPDATE public.direct_messages SET text = 'Hacked DM Text' WHERE id = '55555555-5555-5555-5555-555555555555';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: Recipient B updated DM text directly (updated % rows)', v_c;
    END IF;
END $$;

-- Test 11: Recipient B mark-read RPC -> allowed
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    PERFORM public.mark_direct_messages_read('11111111-1111-1111-1111-111111111111');
END $$;

-- Test 12: B reads A ONLY_ME post -> denied (0 rows)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_only_me';
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: B read A ONLY_ME post (count: %)', v_c;
    END IF;
END $$;

-- Test 13: Unrelated C reads A FRIENDS post -> denied (0 rows)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c <> 0 THEN
        RAISE EXCEPTION 'RLS Leak: Unrelated C read A FRIENDS post (count: %)', v_c;
    END IF;
END $$;

-- Test 14: Friend B reads A FRIENDS post -> allowed (after friendship exists)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c <> 1 THEN
        RAISE EXCEPTION 'RLS Failure: Friend B could not read A FRIENDS post';
    END IF;
END $$;

-- Test 15: A writes feed post author=B -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.feed_posts (id, author_id, caption) VALUES ('post_fake', '22222222-2222-2222-2222-222222222222', 'Impersonated post');
    RAISE EXCEPTION 'RLS Leak: A inserted feed post with author=B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;

-- ===================================================
-- 3. CLEANUP FIXTURE DATA (ROLE: POSTGRES)
-- ===================================================
SET ROLE postgres;

DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM auth.users WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
