-- Real RLS Executable Test Script for Supabase / PostgreSQL
-- Run with psql or Supabase CLI: psql $DATABASE_URL -f supabase/tests/rls_negative_tests.sql
-- Fixtures are prepared as superuser, assertions execute strictly under ROLE authenticated.

BEGIN;

-- ===================================================
-- 1. FIXTURE PREPARATION (ADMIN / SUPERUSER ROLE)
-- ===================================================
SET LOCAL ROLE postgres;

-- Test User UIDs
\set user_a '11111111-1111-1111-1111-111111111111'
\set user_b '22222222-2222-2222-2222-222222222222'
\set user_c '33333333-3333-3333-3333-333333333333'
\set freq_ab '44444444-4444-4444-4444-444444444444'
\set dm_ab '55555555-5555-5555-5555-555555555555'

-- Cleanup previous test data
DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');

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

COMMIT;

-- ===================================================
-- 2. EXECUTABLE RLS ASSERTIONS (ROLE: AUTHENTICATED)
-- ===================================================

-- Test 1: A modifies A profile -> allowed
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
UPDATE public.profiles SET bio = 'Bio updated by A' WHERE id = '11111111-1111-1111-1111-111111111111';
ROLLBACK;

-- Test 2: A modifies B profile -> denied (0 rows updated)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
UPDATE public.profiles SET bio = 'Hacked by A' WHERE id = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE v_c INT;
BEGIN
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: A updated B profile'; END IF;
END $$;
ROLLBACK;

-- Test 3: A sends request sender=A -> allowed
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
VALUES ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'PENDING');
ROLLBACK;

-- Test 4: A sends request pretending sender=B -> denied
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('77777777-7777-7777-7777-777777777777', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'PENDING');
    RAISE EXCEPTION 'RLS Leak: A inserted request pretending sender=B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;
ROLLBACK;

-- Test 5: Sender A accepts own request -> denied
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'RLS Leak: Sender A accepted own request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;
ROLLBACK;

-- Test 6: Recipient B accepts request -> allowed
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
ROLLBACK;

-- Test 7: Third user C accepts A-B request -> denied
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
DO $$
BEGIN
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'RLS Leak: Third user C accepted A-B request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;
ROLLBACK;

-- Test 8: C reads A-B request -> denied (0 rows)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE v_c INT;
BEGIN
    SELECT COUNT(*) INTO v_c FROM public.friend_requests WHERE id = '44444444-4444-4444-4444-444444444444';
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: C read A-B request'; END IF;
END $$;
ROLLBACK;

-- Test 9: C reads A-B direct message -> denied (0 rows)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE v_c INT;
BEGIN
    SELECT COUNT(*) INTO v_c FROM public.direct_messages WHERE id = '55555555-5555-5555-5555-555555555555';
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: C read A-B DM'; END IF;
END $$;
ROLLBACK;

-- Test 10: Recipient B edits DM text directly -> denied (0 rows updated)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
UPDATE public.direct_messages SET text = 'Hacked DM Text' WHERE id = '55555555-5555-5555-5555-555555555555';
DO $$
DECLARE v_c INT;
BEGIN
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: Recipient B updated DM text directly'; END IF;
END $$;
ROLLBACK;

-- Test 11: Recipient B mark-read RPC -> allowed
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
PERFORM public.mark_direct_messages_read('11111111-1111-1111-1111-111111111111');
ROLLBACK;

-- Test 12: B reads A ONLY_ME post -> denied (0 rows)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE v_c INT;
BEGIN
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_only_me';
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: B read A ONLY_ME post'; END IF;
END $$;
ROLLBACK;

-- Test 13: Unrelated C reads A FRIENDS post -> denied (0 rows)
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
DO $$
DECLARE v_c INT;
BEGIN
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c > 0 THEN RAISE EXCEPTION 'RLS Leak: Unrelated C read A FRIENDS post'; END IF;
END $$;
ROLLBACK;

-- Test 14: Friend B reads A FRIENDS post -> allowed (after friendship created)
BEGIN;
SET LOCAL ROLE postgres;
INSERT INTO public.friends (user_id_1, user_id_2) VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
DO $$
DECLARE v_c INT;
BEGIN
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c = 0 THEN RAISE EXCEPTION 'RLS Failure: Friend B could not read A FRIENDS post'; END IF;
END $$;
ROLLBACK;

-- Test 15: A writes feed post author=B -> denied
BEGIN;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
DO $$
BEGIN
    INSERT INTO public.feed_posts (id, author_id, caption) VALUES ('post_fake', '22222222-2222-2222-2222-222222222222', 'Impersonated post');
    RAISE EXCEPTION 'RLS Leak: A inserted feed post with author=B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%RLS Leak%' THEN RAISE; END IF;
END $$;
ROLLBACK;
