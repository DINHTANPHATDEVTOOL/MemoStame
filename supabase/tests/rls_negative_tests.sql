-- Real Executable RLS & Security Test Suite for Native Supabase / PostgreSQL
-- Run with: psql -v ON_ERROR_STOP=1 $DATABASE_URL -f supabase/tests/rls_negative_tests.sql
-- All assertions run against native Supabase auth schema & authenticated role.

-- ===================================================
-- 1. PREREQUISITE ASSERTIONS (ADMIN / POSTGRES ROLE)
-- ===================================================
SET ROLE postgres;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: auth.users table missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = 'auth' AND p.proname = 'uid') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: auth.uid() function missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: authenticated role missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: anon role missing';
    END IF;
END $$;

-- Verify Migration 003 Content ID column types are TEXT
DO $$
DECLARE
    v_type text;
BEGIN
    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_posts' AND column_name = 'id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_posts.id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_reactions' AND column_name = 'id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_reactions.id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_reactions' AND column_name = 'post_id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_reactions.post_id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_comments' AND column_name = 'id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_comments.id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_comments' AND column_name = 'post_id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_comments.post_id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_comments' AND column_name = 'parent_comment_id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_comments.parent_comment_id is %, expected text', v_type; END IF;

    -- Verify Identity columns remain UUID
    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'id';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: profiles.id is %, expected uuid', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'friend_requests' AND column_name = 'sender_id';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: friend_requests.sender_id is %, expected uuid', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'friends' AND column_name = 'user_id_1';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: friends.user_id_1 is %, expected uuid', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'direct_messages' AND column_name = 'sender_id';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: direct_messages.sender_id is %, expected uuid', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_posts' AND column_name = 'author_id';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: feed_posts.author_id is %, expected uuid', v_type; END IF;
END $$;


-- ===================================================
-- 2. FIXTURE CLEANUP & POPULATION (SUPERUSER ROLE)
-- ===================================================
DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM auth.users WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');

-- Insert Auth test users into existing auth.users table
INSERT INTO auth.users (id, email, role, aud) VALUES
    ('11111111-1111-1111-1111-111111111111', 'usera@test.local', 'authenticated', 'authenticated'),
    ('22222222-2222-2222-2222-222222222222', 'userb@test.local', 'authenticated', 'authenticated'),
    ('33333333-3333-3333-3333-333333333333', 'userc@test.local', 'authenticated', 'authenticated')
ON CONFLICT (id) DO NOTHING;

-- Insert User Profiles
INSERT INTO public.profiles (id, username, display_name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'user_a', 'User A'),
    ('22222222-2222-2222-2222-222222222222', 'user_b', 'User B'),
    ('33333333-3333-3333-3333-333333333333', 'user_c', 'User C');

-- Insert DM from A to B
INSERT INTO public.direct_messages (id, sender_id, recipient_id, text, is_read) VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Hello B', false);

-- Insert Feed Posts by A
INSERT INTO public.feed_posts (id, author_id, caption, audience_type) VALUES
    ('post_a_only_me', '11111111-1111-1111-1111-111111111111', 'Private Note', 'ONLY_ME'),
    ('post_a_friends', '11111111-1111-1111-1111-111111111111', 'Friends Note', 'FRIENDS'),
    ('post_a_specific', '11111111-1111-1111-1111-111111111111', 'Circle Note', 'SPECIFIC_FRIENDS');


-- ===================================================
-- 3. REAL RLS ASSERTIONS (ROLE: AUTHENTICATED)
-- ===================================================

-- Assertion 1: Sanity Check - Verify auth.uid() resolution under authenticated role
DO $$
DECLARE
    v_uid uuid;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    v_uid := auth.uid();
    IF v_uid IS NULL OR v_uid <> '11111111-1111-1111-1111-111111111111'::uuid THEN
        RAISE EXCEPTION 'Sanity Check Failed: auth.uid() resolved to %, expected User A UUID', v_uid;
    END IF;
END $$;

-- Assertion 2: Profile RLS - A updates A -> allowed
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    UPDATE public.profiles SET bio = 'Bio updated by A' WHERE id = '11111111-1111-1111-1111-111111111111';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 1 THEN RAISE EXCEPTION 'Profile RLS Failed: User A could not update own profile'; END IF;
END $$;

-- Assertion 3: Profile RLS - A updates B -> denied (0 rows updated)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    UPDATE public.profiles SET bio = 'Hacked by A' WHERE id = '22222222-2222-2222-2222-222222222222';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Profile RLS Leak: A updated B profile (count: %)', v_c; END IF;
END $$;

-- Assertion 4 & 5: Public Profile Discovery vs Base Table Protection
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Assertion 4: public_profiles view allowed
    SELECT COUNT(*) INTO v_c FROM public.public_profiles WHERE id = '22222222-2222-2222-2222-222222222222';
    IF v_c <> 1 THEN RAISE EXCEPTION 'Public Profiles View Failed: Could not discover User B public profile'; END IF;

    -- Assertion 5: base profiles table SELECT for B by A is denied (insufficient_privilege or 0 rows returned)
    BEGIN
        SELECT COUNT(*) INTO v_c FROM public.profiles WHERE id = '22222222-2222-2222-2222-222222222222';
        IF v_c <> 0 THEN
            RAISE EXCEPTION 'Profile Privacy Leak: Base profiles table returned non-owner profile';
        END IF;
    EXCEPTION
        WHEN insufficient_privilege THEN
            -- PASS: REVOKE SELECT correctly blocked direct table read
            NULL;
    END;
END $$;

-- Assertion 6: Friend Request - A sends A -> B request -> allowed
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'PENDING');
END $$;

-- Assertion 7: Friend Request - A inserting request pretending sender=B -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('44444444-4444-4444-4444-999999999999', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PENDING');
    RAISE EXCEPTION 'Friend Request RLS Leak: User A inserted request pretending sender=B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Friend Request RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 8: Friend RPC - Sender A accepts own outgoing request -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'Friend RPC Leak: Sender A accepted own friend request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Friend RPC Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 9: Friend RPC - Third User C accepts A -> B request -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'Friend RPC Leak: Third user C accepted A-B request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Friend RPC Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 10: Friend RPC - Recipient B accepts A -> B request -> allowed & creates exactly 1 canonical friendship pair
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');

    SELECT COUNT(*) INTO v_c FROM public.friends 
    WHERE (user_id_1 = '11111111-1111-1111-1111-111111111111' AND user_id_2 = '22222222-2222-2222-2222-222222222222')
       OR (user_id_1 = '22222222-2222-2222-2222-222222222222' AND user_id_2 = '11111111-1111-1111-1111-111111111111');
    IF v_c <> 1 THEN RAISE EXCEPTION 'Friend RPC Failed: Canonical friendship pair count is %, expected 1', v_c; END IF;
END $$;

-- Assertion 11: Friend RPC - Repeated accept on non-pending request -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    PERFORM public.accept_friend_request('44444444-4444-4444-4444-444444444444');
    RAISE EXCEPTION 'Friend RPC Leak: Repeated accept succeeded on non-pending request';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Friend RPC Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 12 & 13: DM RLS - Participant A/B read DM -> allowed, Unrelated C read DM -> denied
DO $$
DECLARE v_c INT;
BEGIN
    -- Assertion 12a: Participant A
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_c FROM public.direct_messages WHERE id = '55555555-5555-5555-5555-555555555555';
    IF v_c <> 1 THEN RAISE EXCEPTION 'DM RLS Failed: Participant A could not read DM'; END IF;

    -- Assertion 12b: Participant B
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.direct_messages WHERE id = '55555555-5555-5555-5555-555555555555';
    IF v_c <> 1 THEN RAISE EXCEPTION 'DM RLS Failed: Participant B could not read DM'; END IF;

    -- Assertion 13: Unrelated C
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.direct_messages WHERE id = '55555555-5555-5555-5555-555555555555';
    IF v_c <> 0 THEN RAISE EXCEPTION 'DM RLS Leak: Unrelated C read DM (count: %)', v_c; END IF;
END $$;

-- Assertion 14 & 15: DM RLS - Recipient B direct text update -> denied, mark_direct_messages_read RPC -> allowed
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    
    -- Assertion 14: Direct text update denied
    UPDATE public.direct_messages SET text = 'Hacked DM Text' WHERE id = '55555555-5555-5555-5555-555555555555';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'DM RLS Leak: Recipient B directly updated DM text'; END IF;

    -- Assertion 15: RPC allowed
    PERFORM public.mark_direct_messages_read('11111111-1111-1111-1111-111111111111');
END $$;

-- Assertion 16 & 17: Feed Privacy RLS - ONLY_ME (A allowed, B denied)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    
    -- Assertion 16: Author A
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_only_me';
    IF v_c <> 1 THEN RAISE EXCEPTION 'Feed Privacy Failed: Author A could not read ONLY_ME post'; END IF;

    -- Assertion 17: Non-author B
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_only_me';
    IF v_c <> 0 THEN RAISE EXCEPTION 'Feed Privacy Leak: Non-author B read ONLY_ME post'; END IF;
END $$;

-- Assertion 18 & 19: Feed Privacy RLS - FRIENDS (Friend B allowed, Unrelated C denied)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    
    -- Assertion 18: Friend B (Friendship created in Assertion 10)
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c <> 1 THEN RAISE EXCEPTION 'Feed Privacy Failed: Friend B could not read FRIENDS post'; END IF;

    -- Assertion 19: Unrelated C
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_friends';
    IF v_c <> 0 THEN RAISE EXCEPTION 'Feed Privacy Leak: Unrelated C read FRIENDS post'; END IF;
END $$;

-- Assertion 20: Feed Privacy RLS - SPECIFIC_FRIENDS (Non-author B denied)
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_posts WHERE id = 'post_a_specific';
    IF v_c <> 0 THEN RAISE EXCEPTION 'Feed Privacy Leak: Non-author B read SPECIFIC_FRIENDS post'; END IF;
END $$;

-- Assertion 21: Feed Author Impersonation - A inserts post with author_id = B -> denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.feed_posts (id, author_id, caption) VALUES ('post_fake', '22222222-2222-2222-2222-222222222222', 'Impersonated post');
    RAISE EXCEPTION 'Feed RLS Leak: User A inserted feed post impersonating author B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Feed RLS Leak%' THEN RAISE; END IF;
END $$;


-- ===================================================
-- 4. FIXTURE TEARDOWN (POSTGRES ROLE)
-- ===================================================
SET ROLE postgres;

DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM auth.users WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
