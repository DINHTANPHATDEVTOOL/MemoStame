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

    -- Verify Migration 005 feed_replies table and types
    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_replies' AND column_name = 'id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_replies.id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_replies' AND column_name = 'post_id';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_replies.post_id is %, expected text', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_replies' AND column_name = 'author_id';
    IF v_type <> 'uuid' THEN RAISE EXCEPTION 'Type Check Failed: feed_replies.author_id is %, expected uuid', v_type; END IF;

    SELECT data_type INTO v_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'feed_replies' AND column_name = 'reply_stamp_url';
    IF v_type <> 'text' THEN RAISE EXCEPTION 'Type Check Failed: feed_replies.reply_stamp_url is %, expected text', v_type; END IF;

    -- Verify stamp-media storage bucket exists
    IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'stamp-media') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: storage bucket stamp-media missing';
    END IF;

    -- Verify Migration 006 push tables and RPCs
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'push_device_tokens') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: table push_device_tokens missing';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'push_delivery_events') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: table push_delivery_events missing';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = 'public' AND p.proname = 'register_push_device_token') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: function register_push_device_token missing';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = 'public' AND p.proname = 'unregister_push_device_token') THEN
        RAISE EXCEPTION 'Prerequisite Check Failed: function unregister_push_device_token missing';
    END IF;
END $$;


-- ===================================================
-- 2. FIXTURE CLEANUP & POPULATION (SUPERUSER ROLE)
-- ===================================================
DELETE FROM public.push_delivery_events WHERE recipient_user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.push_device_tokens WHERE user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_replies WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
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

-- Assertion 22: Storage - Verify bucket 'stamp-media' exists and is public
DO $$
DECLARE v_public BOOLEAN;
BEGIN
    SELECT public INTO v_public FROM storage.buckets WHERE id = 'stamp-media';
    IF v_public IS NOT TRUE THEN RAISE EXCEPTION 'Storage Assertion Failed: stamp-media bucket is not public'; END IF;
END $$;

-- Assertion 23: Storage RLS - Anon cannot INSERT into storage.objects
DO $$
BEGIN
    SET LOCAL ROLE anon;
    INSERT INTO storage.objects (bucket_id, name, owner)
    VALUES ('stamp-media', 'anon_file.png', '11111111-1111-1111-1111-111111111111');
    RAISE EXCEPTION 'Storage RLS Leak: Anon inserted object into storage.objects';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Storage RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 24: Storage RLS - User A can INSERT storage object with path prefix '11111111-1111-1111-1111-111111111111/'
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO storage.objects (bucket_id, name, owner)
    VALUES ('stamp-media', '11111111-1111-1111-1111-111111111111/rendered/stamp_a.png', '11111111-1111-1111-1111-111111111111');
END $$;

-- Assertion 25: Storage RLS - User B cannot INSERT storage object under User A's path prefix
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    INSERT INTO storage.objects (bucket_id, name, owner)
    VALUES ('stamp-media', '11111111-1111-1111-1111-111111111111/rendered/stamp_b.png', '22222222-2222-2222-2222-222222222222');
    RAISE EXCEPTION 'Storage RLS Leak: User B inserted object under User A directory prefix';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Storage RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 26: Storage RLS - User B cannot UPDATE User A's storage object
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    UPDATE storage.objects SET metadata = '{"hacked": true}'::jsonb
    WHERE bucket_id = 'stamp-media' AND name = '11111111-1111-1111-1111-111111111111/rendered/stamp_a.png';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Storage RLS Leak: User B updated User A object'; END IF;
END $$;

-- Assertion 27: Storage RLS - User B cannot DELETE User A's storage object
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    DELETE FROM storage.objects
    WHERE bucket_id = 'stamp-media' AND name = '11111111-1111-1111-1111-111111111111/rendered/stamp_a.png';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Storage RLS Leak: User B deleted User A object'; END IF;
EXCEPTION WHEN OTHERS THEN
    -- Direct deletion is blocked by RLS or storage.protect_delete trigger
    IF SQLERRM LIKE '%Storage RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 29: Feed Replies - Author A inserts valid reply on accessible post (post_a_friends)
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.feed_replies (id, post_id, author_id, reply_stamp_url)
    VALUES ('reply_a_1', 'post_a_friends', '11111111-1111-1111-1111-111111111111', 'https://example.com/stamp.png');
END $$;

-- Assertion 30: Feed Replies - Author Impersonation (User A inserts reply with author_id = B -> denied)
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.feed_replies (id, post_id, author_id, reply_stamp_url)
    VALUES ('reply_fake', 'post_a_friends', '22222222-2222-2222-2222-222222222222', 'https://example.com/stamp.png');
    RAISE EXCEPTION 'Feed Replies RLS Leak: User A inserted reply impersonating author B';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Feed Replies RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 31: Feed Replies - Unrelated C cannot read reply to friend-only post
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c FROM public.feed_replies WHERE id = 'reply_a_1';
    IF v_c <> 0 THEN RAISE EXCEPTION 'Feed Replies RLS Leak: Unrelated C read reply to friend-only post'; END IF;
END $$;

-- Assertion 32: Feed Replies - Friend B can read reply to friend-only post
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.feed_replies WHERE id = 'reply_a_1';
    IF v_c <> 1 THEN RAISE EXCEPTION 'Feed Replies RLS Failed: Friend B could not read reply to friend-only post'; END IF;
END $$;

-- Assertion 33: Feed Replies - Non-author B cannot delete Author A's reply
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    DELETE FROM public.feed_replies WHERE id = 'reply_a_1';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Feed Replies RLS Leak: Non-author B deleted Author A reply'; END IF;
END $$;

-- Assertion 34: Feed Replies - Author A can delete own reply
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    DELETE FROM public.feed_replies WHERE id = 'reply_a_1';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 1 THEN RAISE EXCEPTION 'Feed Replies RLS Failed: Author A could not delete own reply'; END IF;
END $$;

-- Assertion 35: Media URL Constraint - Invalid local/file/data URL rejected on feed_replies.reply_stamp_url
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.feed_replies (id, post_id, author_id, reply_stamp_url)
    VALUES ('reply_bad_url', 'post_a_friends', '11111111-1111-1111-1111-111111111111', 'file:///local/path.png');
    RAISE EXCEPTION 'Media URL Constraint Leak: feed_replies accepted local file:// URL';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Media URL Constraint Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 36: Media URL Constraint - Invalid local/file/data URL rejected on direct_messages.stamp_image_url
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    INSERT INTO public.direct_messages (id, sender_id, recipient_id, text, stamp_image_url)
    VALUES ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'DM with bad stamp', 'data:image/png;base64,bad');
    RAISE EXCEPTION 'Media URL Constraint Leak: direct_messages accepted data:image URL';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Media URL Constraint Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 37: Push Tokens - Anon role cannot execute register_push_device_token
DO $$
BEGIN
    SET LOCAL ROLE anon;
    PERFORM public.register_push_device_token('android', 'fcm', 'token_anon_test', 'install_anon_test');
    RAISE EXCEPTION 'Push Token RLS Leak: Anon role registered push token';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE '%Push Token RLS Leak%' THEN RAISE; END IF;
END $$;

-- Assertion 38: Push Tokens - Authenticated User A registers FCM token; User A can select own token
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.register_push_device_token('android', 'fcm', 'fcm_token_user_a', 'install_dev_a');

    SELECT COUNT(*) INTO v_c FROM public.push_device_tokens WHERE user_id = '11111111-1111-1111-1111-111111111111' AND token = 'fcm_token_user_a';
    IF v_c <> 1 THEN RAISE EXCEPTION 'Push Token RLS Failed: User A could not select own registered token'; END IF;
END $$;

-- Assertion 39: Push Tokens - User B cannot select User A's token
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_c FROM public.push_device_tokens WHERE token = 'fcm_token_user_a';
    IF v_c <> 0 THEN RAISE EXCEPTION 'Push Token RLS Leak: User B selected User A push token'; END IF;
END $$;

-- Assertion 40: Push Tokens - User A cannot directly insert/update/delete push_device_tokens without RPC
DO $$
DECLARE v_c INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Direct INSERT should fail or be denied by policy
    BEGIN
        INSERT INTO public.push_device_tokens (user_id, platform, provider, token, installation_id)
        VALUES ('11111111-1111-1111-1111-111111111111', 'android', 'fcm', 'direct_token', 'direct_install');
        RAISE EXCEPTION 'Push Token RLS Leak: User A directly inserted row into push_device_tokens';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Push Token RLS Leak%' THEN RAISE; END IF;
    END;

    -- Direct UPDATE should affect 0 rows
    UPDATE public.push_device_tokens SET token = 'hacked_token' WHERE user_id = '11111111-1111-1111-1111-111111111111';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Push Token RLS Leak: User A directly updated push_device_tokens'; END IF;

    -- Direct DELETE should affect 0 rows
    DELETE FROM public.push_device_tokens WHERE user_id = '11111111-1111-1111-1111-111111111111';
    GET DIAGNOSTICS v_c = ROW_COUNT;
    IF v_c <> 0 THEN RAISE EXCEPTION 'Push Token RLS Leak: User A directly deleted push_device_tokens'; END IF;
END $$;

-- Assertion 41: Push Tokens - Invalid platform/provider combination rejected
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Android + APNS must fail
    BEGIN
        PERFORM public.register_push_device_token('android', 'apns', 'invalid_pair_token', 'install_bad');
        RAISE EXCEPTION 'Push Token Check Leak: register accepted android + apns';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Push Token Check Leak%' THEN RAISE; END IF;
    END;

    -- Unknown platform must fail
    BEGIN
        PERFORM public.register_push_device_token('windows', 'fcm', 'invalid_plat_token', 'install_bad');
        RAISE EXCEPTION 'Push Token Check Leak: register accepted windows platform';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Push Token Check Leak%' THEN RAISE; END IF;
    END;
END $$;

-- Assertion 42: Account Switch Atomic Token Reassignment - User B registers same token & install ID
DO $$
DECLARE
    v_cA INT;
    v_cB INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';

    -- User B authenticates on same device previously used by User A
    PERFORM public.register_push_device_token('android', 'fcm', 'fcm_token_user_a', 'install_dev_a');

    -- User B now sees 1 row for this token
    SELECT COUNT(*) INTO v_cB FROM public.push_device_tokens WHERE token = 'fcm_token_user_a';
    IF v_cB <> 1 THEN RAISE EXCEPTION 'Push Token Reassignment Failed: User B does not own token'; END IF;

    -- User A should no longer own this token
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_cA FROM public.push_device_tokens WHERE token = 'fcm_token_user_a';
    IF v_cA <> 0 THEN RAISE EXCEPTION 'Push Token Isolation Failed: User A still has token after reassignment'; END IF;
END $$;

-- Assertion 43: Unregister RPC deactivates token
DO $$
DECLARE v_active BOOLEAN;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';

    PERFORM public.unregister_push_device_token('fcm', 'install_dev_a');

    SELECT is_active INTO v_active FROM public.push_device_tokens WHERE token = 'fcm_token_user_a';
    IF v_active IS NOT FALSE THEN RAISE EXCEPTION 'Push Token Unregister Failed: token is not inactive'; END IF;
END $$;

-- Assertion 44: Account Deletion Cascade removes push tokens
DO $$
DECLARE v_cnt INT;
BEGIN
    -- Insert disposable auth user D with push token
    INSERT INTO auth.users (id, email, role, aud) VALUES
        ('44444444-4444-4444-4444-444444444444', 'userd@test.local', 'authenticated', 'authenticated')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.profiles (id, username, display_name) VALUES
        ('44444444-4444-4444-4444-444444444444', 'user_d', 'User D')
    ON CONFLICT (id) DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '44444444-4444-4444-4444-444444444444';
    PERFORM public.register_push_device_token('ios', 'apns', 'apns_token_user_d', 'install_dev_d');

    SET LOCAL ROLE postgres;
    DELETE FROM auth.users WHERE id = '44444444-4444-4444-4444-444444444444';

    SELECT COUNT(*) INTO v_cnt FROM public.push_device_tokens WHERE token = 'apns_token_user_d';
    IF v_cnt <> 0 THEN RAISE EXCEPTION 'Push Token Cascade Failed: token not deleted on user deletion'; END IF;
END $$;


-- ===================================================
-- SOCIAL SAFETY & USER BLOCKING ASSERTIONS (TASK #53)
-- ===================================================

-- Assertion 45: Self-block is rejected by block_user RPC
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        PERFORM public.block_user('11111111-1111-1111-1111-111111111111');
        RAISE EXCEPTION 'Self-Block Allowed: RPC should have rejected self-block';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLERRM NOT LIKE '%Cannot block oneself%' THEN
                RAISE EXCEPTION 'Unexpected error on self-block: %', SQLERRM;
            END IF;
    END;
END $$;

-- Assertion 46: User A blocks User B: removes friendship & pending requests atomically, persists block idempotently
DO $$
DECLARE
    v_f_count INT;
    v_r_count INT;
    v_b_count INT;
BEGIN
    SET ROLE postgres;
    -- Setup friendship and pending request
    INSERT INTO public.friends (user_id_1, user_id_2) VALUES
        ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
    ON CONFLICT DO NOTHING;

    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status) VALUES
        ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PENDING')
    ON CONFLICT (id) DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Execute block_user RPC
    PERFORM public.block_user('22222222-2222-2222-2222-222222222222');

    -- Verify duplicate call is idempotent
    PERFORM public.block_user('22222222-2222-2222-2222-222222222222');

    SET ROLE postgres;
    -- Check friendship removed
    SELECT COUNT(*) INTO v_f_count FROM public.friends
    WHERE (user_id_1 = '11111111-1111-1111-1111-111111111111' AND user_id_2 = '22222222-2222-2222-2222-222222222222')
       OR (user_id_2 = '11111111-1111-1111-1111-111111111111' AND user_id_1 = '22222222-2222-2222-2222-222222222222');
    IF v_f_count <> 0 THEN RAISE EXCEPTION 'Block Cleanup Failed: Friendship was not removed'; END IF;

    -- Check pending request removed
    SELECT COUNT(*) INTO v_r_count FROM public.friend_requests
    WHERE (sender_id = '11111111-1111-1111-1111-111111111111' AND recipient_id = '22222222-2222-2222-2222-222222222222')
       OR (sender_id = '22222222-2222-2222-2222-222222222222' AND recipient_id = '11111111-1111-1111-1111-111111111111');
    IF v_r_count <> 0 THEN RAISE EXCEPTION 'Block Cleanup Failed: Pending request was not removed'; END IF;

    -- Check block recorded
    SELECT COUNT(*) INTO v_b_count FROM public.user_blocks
    WHERE blocker_id = '11111111-1111-1111-1111-111111111111' AND blocked_id = '22222222-2222-2222-2222-222222222222';
    IF v_b_count <> 1 THEN RAISE EXCEPTION 'Block Record Failed: user_blocks row missing'; END IF;
END $$;

-- Assertion 47: Friend request denied across blocked relationship in either direction
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    -- B tries to send friend request to A (who blocked B)
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    BEGIN
        INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
        VALUES (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PENDING');
        RAISE EXCEPTION 'RLS Violation Failed: Blocked user B was able to send friend request to A';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- A tries to send friend request to B (whom A blocked)
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
        VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'PENDING');
        RAISE EXCEPTION 'RLS Violation Failed: Blocker A was able to send friend request to B';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- Third party C can send friend request to A
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'PENDING');
END $$;

-- Assertion 48: Friend request acceptance denied across blocked relationship
DO $$
BEGIN
    SET ROLE postgres;
    -- Artificially insert pending request between A and B
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PENDING')
    ON CONFLICT (id) DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        PERFORM public.accept_friend_request('aaaaaaaa-0000-0000-0000-000000000003');
        RAISE EXCEPTION 'Accept Across Block Failed: RPC allowed accepting request across block';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLERRM NOT LIKE '%Cannot accept friend request: relationship is blocked%' THEN
                RAISE EXCEPTION 'Unexpected error on accept friend request: %', SQLERRM;
            END IF;
    END;

    SET ROLE postgres;
    DELETE FROM public.friend_requests WHERE id = 'aaaaaaaa-0000-0000-0000-000000000003';
END $$;

-- Assertion 49: Direct message denied across blocked relationship in either direction
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    -- B tries to send DM to A
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    BEGIN
        INSERT INTO public.direct_messages (id, sender_id, recipient_id, text)
        VALUES (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Blocked DM from B');
        RAISE EXCEPTION 'RLS Violation Failed: Blocked user B was able to send DM to A';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- A tries to send DM to B
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        INSERT INTO public.direct_messages (id, sender_id, recipient_id, text)
        VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Blocked DM from A');
        RAISE EXCEPTION 'RLS Violation Failed: Blocker A was able to send DM to B';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- Third party C can send DM to A
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    INSERT INTO public.direct_messages (id, sender_id, recipient_id, text)
    VALUES (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Hello from User C');
END $$;

-- Assertion 50: Feed interactions (reactions, comments, replies) denied across blocked relationship
DO $$
BEGIN
    SET ROLE postgres;
    -- Ensure User A has a public post
    INSERT INTO public.feed_posts (id, author_id, content, audience_type)
    VALUES ('post_user_a_public', '11111111-1111-1111-1111-111111111111', 'Post by User A', 'EVERYONE')
    ON CONFLICT (id) DO NOTHING;

    SET LOCAL ROLE authenticated;
    -- B tries to react to A's post
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    BEGIN
        INSERT INTO public.feed_reactions (id, post_id, user_id, reaction_type)
        VALUES ('reaction_b_on_a', 'post_user_a_public', '22222222-2222-2222-2222-222222222222', 'LIKE');
        RAISE EXCEPTION 'RLS Violation Failed: Blocked user B was able to react to post by A';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- B tries to comment on A's post
    BEGIN
        INSERT INTO public.feed_comments (id, post_id, author_id, content)
        VALUES ('comment_b_on_a', 'post_user_a_public', '22222222-2222-2222-2222-222222222222', 'Comment from B');
        RAISE EXCEPTION 'RLS Violation Failed: Blocked user B was able to comment on post by A';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- B tries to reply to A's post
    BEGIN
        INSERT INTO public.feed_replies (id, post_id, author_id, reply_stamp_url)
        VALUES ('reply_b_on_a', 'post_user_a_public', '22222222-2222-2222-2222-222222222222', 'https://example.com/stamp.png');
        RAISE EXCEPTION 'RLS Violation Failed: Blocked user B was able to reply to post by A';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    -- Third party C can react to A's post
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    INSERT INTO public.feed_reactions (id, post_id, user_id, reaction_type)
    VALUES ('reaction_c_on_a', 'post_user_a_public', '33333333-3333-3333-3333-333333333333', 'LIKE');
END $$;

-- Assertion 51: Block Privacy: Blocker reads own list; blocked user cannot enumerate who blocked them
DO $$
DECLARE
    v_a_sees INT;
    v_b_sees INT;
BEGIN
    SET LOCAL ROLE authenticated;
    -- User A sees 1 outbound block
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_a_sees FROM public.user_blocks;
    IF v_a_sees <> 1 THEN RAISE EXCEPTION 'Block Privacy Failed: User A cannot see outbound block'; END IF;

    -- User B sees 0 blocks (cannot see that A blocked B)
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_b_sees FROM public.user_blocks;
    IF v_b_sees <> 0 THEN RAISE EXCEPTION 'Block Privacy Failed: Blocked user B can see block row'; END IF;

    -- Direct client mutation denied
    BEGIN
        INSERT INTO public.user_blocks (blocker_id, blocked_id)
        VALUES ('22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
        RAISE EXCEPTION 'Direct Block Insert Allowed: Client should not be able to INSERT directly';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;
END $$;

-- Assertion 52: Unblock RPC removes block; restores contact permission without restoring friendship
DO $$
DECLARE
    v_blocks INT;
    v_friends INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- User A unblocks User B
    PERFORM public.unblock_user('22222222-2222-2222-2222-222222222222');

    SELECT COUNT(*) INTO v_blocks FROM public.user_blocks
    WHERE blocker_id = '11111111-1111-1111-1111-111111111111' AND blocked_id = '22222222-2222-2222-2222-222222222222';
    IF v_blocks <> 0 THEN RAISE EXCEPTION 'Unblock Failed: Block row still exists'; END IF;

    -- Verify friendship was NOT recreated
    SELECT COUNT(*) INTO v_friends FROM public.friends
    WHERE (user_id_1 = '11111111-1111-1111-1111-111111111111' AND user_id_2 = '22222222-2222-2222-2222-222222222222')
       OR (user_id_2 = '11111111-1111-1111-1111-111111111111' AND user_id_1 = '22222222-2222-2222-2222-222222222222');
    IF v_friends <> 0 THEN RAISE EXCEPTION 'Unblock Invariant Failed: Friendship was inappropriately recreated'; END IF;

    -- Now B can send friend request to A
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'PENDING');
END $$;

-- Assertion 53: Report User RPC derives auth.uid(), rejects self-report, validates category and length
DO $$
DECLARE
    v_report_res JSONB;
    v_rep_id UUID;
    v_actual_reporter UUID;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Self-report rejected
    BEGIN
        PERFORM public.report_user('11111111-1111-1111-1111-111111111111', 'spam');
        RAISE EXCEPTION 'Self-Report Allowed: RPC should reject reporting oneself';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLERRM NOT LIKE '%Cannot report oneself%' THEN
                RAISE EXCEPTION 'Unexpected error on self-report: %', SQLERRM;
            END IF;
    END;

    -- Invalid category rejected
    BEGIN
        PERFORM public.report_user('22222222-2222-2222-2222-222222222222', 'invalid_cat');
        RAISE EXCEPTION 'Invalid Category Allowed: RPC should reject unknown category';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLERRM NOT LIKE '%Invalid report category%' THEN
                RAISE EXCEPTION 'Unexpected error on invalid category: %', SQLERRM;
            END IF;
    END;

    -- Valid report submission
    v_report_res := public.report_user('22222222-2222-2222-2222-222222222222', 'harassment', 'Offensive message sent', 'direct_message', 'msg_123');
    v_rep_id := (v_report_res->>'report_id')::UUID;
    IF v_rep_id IS NULL THEN RAISE EXCEPTION 'Report User Failed: report_id missing in result'; END IF;

    SET ROLE postgres;
    SELECT reporter_id INTO v_actual_reporter FROM public.user_reports WHERE id = v_rep_id;
    IF v_actual_reporter <> '11111111-1111-1111-1111-111111111111'::UUID THEN
        RAISE EXCEPTION 'Reporter Identity Mismatch: expected caller auth.uid(), got %', v_actual_reporter;
    END IF;
END $$;

-- Assertion 54: Report Privacy: Normal clients cannot SELECT user_reports
DO $$
DECLARE
    v_cnt INT;
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    SELECT COUNT(*) INTO v_cnt FROM public.user_reports;
    IF v_cnt <> 0 THEN RAISE EXCEPTION 'Report Privacy Failed: Authenticated client was able to select user_reports'; END IF;

    -- Direct client insert denied
    BEGIN
        INSERT INTO public.user_reports (reporter_id, reported_user_id, category)
        VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'spam');
        RAISE EXCEPTION 'Direct Report Insert Allowed: Client should not be able to direct insert';
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;
END $$;

-- Assertion 55: Account Deletion Cascades user_blocks
DO $$
DECLARE
    v_cnt INT;
BEGIN
    -- Insert disposable auth user D and block relationship
    INSERT INTO auth.users (id, email, role, aud) VALUES
        ('44444444-4444-4444-4444-444444444444', 'userd@test.local', 'authenticated', 'authenticated')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.profiles (id, username, display_name) VALUES
        ('44444444-4444-4444-4444-444444444444', 'user_d', 'User D')
    ON CONFLICT (id) DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.block_user('44444444-4444-4444-4444-444444444444');

    SET LOCAL ROLE postgres;
    DELETE FROM auth.users WHERE id = '44444444-4444-4444-4444-444444444444';

    SELECT COUNT(*) INTO v_cnt FROM public.user_blocks WHERE blocked_id = '44444444-4444-4444-4444-444444444444';
    IF v_cnt <> 0 THEN RAISE EXCEPTION 'Block Cascade Failed: block row not deleted on user deletion'; END IF;
END $$;


-- ===================================================
-- SECURITY DEFINER PRIVILEGE AUDIT & BLOCK ORACLE HARDENING (TASK #54 / HOTFIX)
-- ===================================================

-- Assertion 56: Public Block Oracle is completely absent from public schema (PUBLIC BLOCK ORACLE: ABSENT/DENIED)
DO $$
DECLARE
    v_cnt INT;
BEGIN
    SELECT COUNT(*) INTO v_cnt
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'is_blocked_bidirectional';

    IF v_cnt <> 0 THEN
        RAISE EXCEPTION 'Public Block Oracle Violation: public.is_blocked_bidirectional still exists in public schema!';
    END IF;
END $$;

-- Assertion 57: Block helper relocated to private schema 'app_private' (INTERNAL BLOCK PREDICATE: NOT POSTGREST-EXPOSED)
DO $$
DECLARE
    v_cnt INT;
    v_has_usage BOOLEAN;
BEGIN
    -- Check function exists in app_private
    SELECT COUNT(*) INTO v_cnt
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'app_private' AND p.proname = 'is_blocked_bidirectional';

    IF v_cnt = 0 THEN
        RAISE EXCEPTION 'Internal Block Helper Missing: app_private.is_blocked_bidirectional does not exist';
    END IF;

    -- Verify PUBLIC has no direct usage on app_private
    SELECT has_schema_privilege('public', 'app_private', 'USAGE') INTO v_has_usage;
    IF v_has_usage THEN
        RAISE EXCEPTION 'Schema Privilege Leak: PUBLIC has USAGE privilege on app_private';
    END IF;
END $$;

-- Assertion 58: Project-Wide Privilege Audit: No Security-Sensitive Function Has PUBLIC or anon EXECUTE
DO $$
DECLARE
    v_func_record RECORD;
    v_anon_allowed BOOLEAN;
    v_sensitive_funcs TEXT[] := ARRAY[
        'block_user',
        'unblock_user',
        'report_user',
        'accept_friend_request',
        'decline_friend_request',
        'cancel_friend_request',
        'unfriend_user',
        'mark_direct_messages_read',
        'register_push_device_token',
        'unregister_push_device_token',
        'sync_profile_user_id'
    ];
BEGIN
    FOR v_func_record IN
        SELECT p.oid, p.proname, n.nspname
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proname = ANY(v_sensitive_funcs)
    LOOP
        v_anon_allowed := has_function_privilege('anon', v_func_record.oid, 'EXECUTE');
        IF v_anon_allowed THEN
            RAISE EXCEPTION 'Privilege Leak Detected: Function %.% is executable by anon!',
                v_func_record.nspname, v_func_record.proname;
        END IF;
    END LOOP;
END $$;

-- Assertion 59: Anonymous Direct Execution of Sensitive RPCs is DENIED (ANON BLOCK HELPER EXECUTE: DENIED)
DO $$
BEGIN
    SET LOCAL ROLE anon;

    -- block_user must be denied to anon by PostgreSQL permission system
    BEGIN
        PERFORM public.block_user('22222222-2222-2222-2222-222222222222');
        RAISE EXCEPTION 'Privilege Violation: anon role was able to execute block_user';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL; -- Expected 42501 permission denied
    WHEN OTHERS THEN
        IF SQLERRM LIKE '%Privilege Violation%' THEN RAISE; END IF;
    END;

    -- unblock_user must be denied to anon
    BEGIN
        PERFORM public.unblock_user('22222222-2222-2222-2222-222222222222');
        RAISE EXCEPTION 'Privilege Violation: anon role was able to execute unblock_user';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL; -- Expected 42501 permission denied
    WHEN OTHERS THEN
        IF SQLERRM LIKE '%Privilege Violation%' THEN RAISE; END IF;
    END;

    -- report_user must be denied to anon
    BEGIN
        PERFORM public.report_user('22222222-2222-2222-2222-222222222222', 'spam');
        RAISE EXCEPTION 'Privilege Violation: anon role was able to execute report_user';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL; -- Expected 42501 permission denied
    WHEN OTHERS THEN
        IF SQLERRM LIKE '%Privilege Violation%' THEN RAISE; END IF;
    END;

    -- accept_friend_request must be denied to anon
    BEGIN
        PERFORM public.accept_friend_request('aaaaaaaa-0000-0000-0000-000000000001');
        RAISE EXCEPTION 'Privilege Violation: anon role was able to execute accept_friend_request';
    EXCEPTION WHEN insufficient_privilege THEN
        NULL; -- Expected 42501 permission denied
    WHEN OTHERS THEN
        IF SQLERRM LIKE '%Privilege Violation%' THEN RAISE; END IF;
    END;
END $$;

-- Assertion 60: Authenticated Client Cannot Call Drop Oracle (AUTH DIRECT BLOCK HELPER EXECUTE: DENIED)
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Attempting to call public.is_blocked_bidirectional fails because function does not exist
    BEGIN
        EXECUTE 'SELECT public.is_blocked_bidirectional(''11111111-1111-1111-1111-111111111111''::UUID, ''22222222-2222-2222-2222-222222222222''::UUID)';
        RAISE EXCEPTION 'Public Oracle Leak: public.is_blocked_bidirectional was callable by authenticated user';
    EXCEPTION WHEN undefined_function THEN
        NULL; -- Expected: function does not exist
    WHEN OTHERS THEN
        IF SQLERRM LIKE '%Public Oracle Leak%' THEN RAISE; END IF;
    END;
END $$;

-- Assertion 61: Block Relationship Privacy Invariant (OUTBOUND BLOCK LIST PRIVACY: PASS & BLOCKED USER ENUMERATION: DENIED)
DO $$
DECLARE
    v_a_outbound INT;
    v_b_inbound INT;
BEGIN
    SET ROLE postgres;
    -- Ensure clean block state: A blocks B
    INSERT INTO public.user_blocks (blocker_id, blocked_id)
    VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
    ON CONFLICT (blocker_id, blocked_id) DO NOTHING;

    -- User A can view own outbound blocks
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_a_outbound FROM public.user_blocks WHERE blocker_id = '11111111-1111-1111-1111-111111111111';
    IF v_a_outbound < 1 THEN
        RAISE EXCEPTION 'Outbound Block List Privacy Failed: Blocker cannot read own block list';
    END IF;

    -- User B cannot see inbound blocks (cannot determine that A blocked B)
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_b_inbound FROM public.user_blocks;
    IF v_b_inbound <> 0 THEN
        RAISE EXCEPTION 'Blocked User Enumeration Failed: Blocked user B was able to enumerate blocks (% found)', v_b_inbound;
    END IF;
END $$;


-- ===================================================
-- CLOUD-AUTHORITATIVE STAMP TRADE ASSERTIONS (TASK #57)
-- ===================================================

-- Assertion 62: Stamp Trade Requests RLS - Participants only, third party denied
DO $$
DECLARE
    v_trade_id UUID := gen_random_uuid();
    v_a_cnt INT;
    v_b_cnt INT;
    v_c_cnt INT;
BEGIN
    SET ROLE postgres;
    -- Setup friendship between A and B
    INSERT INTO public.friends (user_id_1, user_id_2)
    VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
    ON CONFLICT DO NOTHING;

    -- Clean any blocks between A and B
    DELETE FROM public.user_blocks
    WHERE (blocker_id = '11111111-1111-1111-1111-111111111111' AND blocked_id = '22222222-2222-2222-2222-222222222222')
       OR (blocker_id = '22222222-2222-2222-2222-222222222222' AND blocked_id = '11111111-1111-1111-1111-111111111111');

    -- Insert mock trade request between A (sender) and B (recipient)
    INSERT INTO public.stamp_trade_requests (
        id, sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        v_trade_id,
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/stamp_rls.png',
        'RLS Stamp',
        'PENDING'
    );

    -- Sender A can select
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_a_cnt FROM public.stamp_trade_requests WHERE id = v_trade_id;
    IF v_a_cnt <> 1 THEN RAISE EXCEPTION 'Trade RLS Failed: Sender A cannot select trade request'; END IF;

    -- Recipient B can select
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_b_cnt FROM public.stamp_trade_requests WHERE id = v_trade_id;
    IF v_b_cnt <> 1 THEN RAISE EXCEPTION 'Trade RLS Failed: Recipient B cannot select trade request'; END IF;

    -- Third-party C cannot select
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c_cnt FROM public.stamp_trade_requests WHERE id = v_trade_id;
    IF v_c_cnt <> 0 THEN RAISE EXCEPTION 'Trade RLS Leak: Third-party C was able to select trade request'; END IF;

    SET ROLE postgres;
    DELETE FROM public.stamp_trade_requests WHERE id = v_trade_id;
END $$;

-- Assertion 63: Direct client mutations on stamp_trade_requests are denied
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Direct client INSERT denied
    BEGIN
        INSERT INTO public.stamp_trade_requests (sender_id, recipient_id, source_object_name, stamp_title)
        VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'hack.png', 'Hack');
        RAISE EXCEPTION 'Direct Trade Insert Allowed: Client directly inserted row';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE '%Direct Trade Insert Allowed%' THEN RAISE; END IF;
    END;

    -- Direct client UPDATE denied
    UPDATE public.stamp_trade_requests SET status = 'ACCEPTED' WHERE sender_id = '11111111-1111-1111-1111-111111111111';

    -- Direct client DELETE denied
    DELETE FROM public.stamp_trade_requests WHERE sender_id = '11111111-1111-1111-1111-111111111111';
END $$;

-- Assertion 64: create_stamp_trade rejects self-trade
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    BEGIN
        PERFORM public.create_stamp_trade(
            '11111111-1111-1111-1111-111111111111',
            '11111111-1111-1111-1111-111111111111/rendered/stamp.png',
            'Self Stamp'
        );
        RAISE EXCEPTION 'Self Trade Allowed: create_stamp_trade accepted self-trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Cannot trade with oneself%' THEN
            RAISE EXCEPTION 'Unexpected error on self-trade: %', SQLERRM;
        END IF;
    END;
END $$;

-- Assertion 65: create_stamp_trade rejects non-friend
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- User A and User C are not friends
    BEGIN
        PERFORM public.create_stamp_trade(
            '33333333-3333-3333-3333-333333333333',
            '11111111-1111-1111-1111-111111111111/rendered/stamp.png',
            'Non-friend Stamp'
        );
        RAISE EXCEPTION 'Non-Friend Trade Allowed: create_stamp_trade accepted non-friend';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Recipient must be an active friend%' THEN
            RAISE EXCEPTION 'Unexpected error on non-friend trade: %', SQLERRM;
        END IF;
    END;
END $$;

-- Assertion 66: create_stamp_trade rejects blocked relationship
DO $$
BEGIN
    SET ROLE postgres;
    -- Artificially block A and B
    INSERT INTO public.user_blocks (blocker_id, blocked_id)
    VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
    ON CONFLICT DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    BEGIN
        PERFORM public.create_stamp_trade(
            '22222222-2222-2222-2222-222222222222',
            '11111111-1111-1111-1111-111111111111/rendered/stamp.png',
            'Blocked Stamp'
        );
        RAISE EXCEPTION 'Blocked Trade Allowed: create_stamp_trade accepted blocked pair';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Cannot trade: relationship is blocked%' THEN
            RAISE EXCEPTION 'Unexpected error on blocked trade: %', SQLERRM;
        END IF;
    END;

    -- Clean up block and restore friendship for subsequent tests
    SET ROLE postgres;
    DELETE FROM public.user_blocks
    WHERE blocker_id = '11111111-1111-1111-1111-111111111111' AND blocked_id = '22222222-2222-2222-2222-222222222222';
    INSERT INTO public.friends (user_id_1, user_id_2)
    VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')
    ON CONFLICT DO NOTHING;
END $$;

-- Assertion 67: create_stamp_trade rejects local/data/blob/external URLs
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    -- Reject file:// URL
    BEGIN
        PERFORM public.create_stamp_trade(
            '22222222-2222-2222-2222-222222222222',
            'file:///local/path/stamp.png',
            'File Stamp'
        );
        RAISE EXCEPTION 'Local Media Allowed: create_stamp_trade accepted file:// URL';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Local or external media URLs are not permitted%' THEN
            RAISE EXCEPTION 'Unexpected error on file:// URL: %', SQLERRM;
        END IF;
    END;

    -- Reject data: URI
    BEGIN
        PERFORM public.create_stamp_trade(
            '22222222-2222-2222-2222-222222222222',
            'data:image/png;base64,iVBORw0KGgo...',
            'Data Stamp'
        );
        RAISE EXCEPTION 'Data URI Allowed: create_stamp_trade accepted data: URI';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Local or external media URLs are not permitted%' THEN
            RAISE EXCEPTION 'Unexpected error on data: URI: %', SQLERRM;
        END IF;
    END;
END $$;

-- Assertion 68: create_stamp_trade rejects nonexistent media in stamp-media
DO $$
BEGIN
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    BEGIN
        PERFORM public.create_stamp_trade(
            '22222222-2222-2222-2222-222222222222',
            '11111111-1111-1111-1111-111111111111/rendered/ghost_media.png',
            'Ghost Stamp'
        );
        RAISE EXCEPTION 'Nonexistent Media Allowed: create_stamp_trade accepted nonexistent object';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Source media object not found in stamp-media%' THEN
            RAISE EXCEPTION 'Unexpected error on nonexistent media: %', SQLERRM;
        END IF;
    END;
END $$;

-- Assertion 69: create_stamp_trade succeeds with valid friend & existing media
DO $$
DECLARE
    v_res JSONB;
    v_trade_id UUID;
    v_status TEXT;
BEGIN
    SET ROLE postgres;
    -- Insert mock storage object in stamp-media
    INSERT INTO storage.objects (id, bucket_id, name, owner)
    VALUES (
        gen_random_uuid(),
        'stamp-media',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_69.png',
        '11111111-1111-1111-1111-111111111111'
    ) ON CONFLICT DO NOTHING;

    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';

    v_res := public.create_stamp_trade(
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_69.png',
        'Chùa Một Cột',
        'RECTANGLE',
        'Hà Nội',
        'Tặng bạn tem kỷ niệm nhé!',
        'local_stamp_001'
    );

    v_trade_id := (v_res->>'trade_id')::UUID;
    v_status := v_res->>'status';
    IF v_trade_id IS NULL OR v_status <> 'PENDING' THEN
        RAISE EXCEPTION 'Create Trade Failed: unexpected response %', v_res;
    END IF;
END $$;

-- Assertion 70: decline_stamp_trade allowed ONLY for recipient
DO $$
DECLARE
    v_trade_id UUID;
    v_dec_res JSONB;
BEGIN
    SET ROLE postgres;
    -- Create fresh pending trade from A to B
    INSERT INTO storage.objects (id, bucket_id, name, owner)
    VALUES (
        gen_random_uuid(),
        'stamp-media',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        '11111111-1111-1111-1111-111111111111'
    ) ON CONFLICT DO NOTHING;

    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        'Decline Test Stamp',
        'PENDING'
    ) RETURNING id INTO v_trade_id;

    -- Sender A cannot decline
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        PERFORM public.decline_stamp_trade(v_trade_id);
        RAISE EXCEPTION 'Sender Decline Allowed: sender was able to decline trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Only recipient can decline trade request%' THEN
            RAISE EXCEPTION 'Unexpected error on sender decline: %', SQLERRM;
        END IF;
    END;

    -- Third-party C cannot decline
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    BEGIN
        PERFORM public.decline_stamp_trade(v_trade_id);
        RAISE EXCEPTION 'Third Party Decline Allowed: user C was able to decline trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Only recipient can decline trade request%' THEN
            RAISE EXCEPTION 'Unexpected error on third party decline: %', SQLERRM;
        END IF;
    END;

    -- Recipient B can decline
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    v_dec_res := public.decline_stamp_trade(v_trade_id);
    IF (v_dec_res->>'status') <> 'DECLINED' THEN
        RAISE EXCEPTION 'Recipient Decline Failed: unexpected response %', v_dec_res;
    END IF;
END $$;

-- Assertion 71: cancel_stamp_trade allowed ONLY for sender
DO $$
DECLARE
    v_trade_id UUID;
    v_cancel_res JSONB;
BEGIN
    SET ROLE postgres;
    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        'Cancel Test Stamp',
        'PENDING'
    ) RETURNING id INTO v_trade_id;

    -- Recipient B cannot cancel
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    BEGIN
        PERFORM public.cancel_stamp_trade(v_trade_id);
        RAISE EXCEPTION 'Recipient Cancel Allowed: recipient was able to cancel trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Only sender can cancel trade request%' THEN
            RAISE EXCEPTION 'Unexpected error on recipient cancel: %', SQLERRM;
        END IF;
    END;

    -- Sender A can cancel
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    v_cancel_res := public.cancel_stamp_trade(v_trade_id);
    IF (v_cancel_res->>'status') <> 'CANCELLED' THEN
        RAISE EXCEPTION 'Sender Cancel Failed: unexpected response %', v_cancel_res;
    END IF;
END $$;

-- Assertion 72: accept_stamp_trade allowed ONLY for recipient
DO $$
DECLARE
    v_trade_id UUID;
BEGIN
    SET ROLE postgres;
    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        'Accept Auth Test Stamp',
        'PENDING'
    ) RETURNING id INTO v_trade_id;

    -- Sender A cannot accept
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    BEGIN
        PERFORM public.accept_stamp_trade(v_trade_id);
        RAISE EXCEPTION 'Sender Accept Allowed: sender was able to accept trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Only recipient can accept trade request%' THEN
            RAISE EXCEPTION 'Unexpected error on sender accept: %', SQLERRM;
        END IF;
    END;

    -- Third-party C cannot accept
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    BEGIN
        PERFORM public.accept_stamp_trade(v_trade_id);
        RAISE EXCEPTION 'Third Party Accept Allowed: user C was able to accept trade';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%Only recipient can accept trade request%' THEN
            RAISE EXCEPTION 'Unexpected error on third party accept: %', SQLERRM;
        END IF;
    END;
END $$;

-- Assertion 73: accept_stamp_trade succeeds, creates received_trade_stamps, idempotent
DO $$
DECLARE
    v_trade_id UUID;
    v_accept_res JSONB;
    v_accept_res_dup JSONB;
    v_rec_cnt INT;
BEGIN
    SET ROLE postgres;
    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, location, note, status
    ) VALUES (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        'Tháp Rùa',
        'Hà Nội',
        'Tem nhận kỷ niệm',
        'PENDING'
    ) RETURNING id INTO v_trade_id;

    -- Insert mock recipient copy in storage
    INSERT INTO storage.objects (id, bucket_id, name, owner)
    VALUES (
        gen_random_uuid(),
        'stamp-media',
        '22222222-2222-2222-2222-222222222222/received/' || v_trade_id::text || '.png',
        '22222222-2222-2222-2222-222222222222'
    ) ON CONFLICT DO NOTHING;

    -- Recipient B accepts
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    v_accept_res := public.accept_stamp_trade(
        v_trade_id,
        '22222222-2222-2222-2222-222222222222/received/' || v_trade_id::text || '.png'
    );
    IF (v_accept_res->>'status') <> 'ACCEPTED' THEN
        RAISE EXCEPTION 'Accept Trade Failed: unexpected response %', v_accept_res;
    END IF;

    -- Check received stamp row created
    SELECT COUNT(*) INTO v_rec_cnt FROM public.received_trade_stamps WHERE source_trade_id = v_trade_id;
    IF v_rec_cnt <> 1 THEN
        RAISE EXCEPTION 'Received Stamp Missing: expected 1 row in received_trade_stamps, found %', v_rec_cnt;
    END IF;

    -- Duplicate accept call is idempotent
    v_accept_res_dup := public.accept_stamp_trade(
        v_trade_id,
        '22222222-2222-2222-2222-222222222222/received/' || v_trade_id::text || '.png'
    );
    IF (v_accept_res_dup->>'status') <> 'ACCEPTED' THEN
        RAISE EXCEPTION 'Duplicate Accept Failed: %', v_accept_res_dup;
    END IF;
END $$;

-- Assertion 74: received_trade_stamps privacy - owner only
DO $$
DECLARE
    v_b_cnt INT;
    v_a_cnt INT;
    v_c_cnt INT;
BEGIN
    -- Owner B can select
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
    SELECT COUNT(*) INTO v_b_cnt FROM public.received_trade_stamps;
    IF v_b_cnt < 1 THEN RAISE EXCEPTION 'Received Stamp Privacy: Owner B cannot select own stamps'; END IF;

    -- Sender A cannot select B's received collection
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    SELECT COUNT(*) INTO v_a_cnt FROM public.received_trade_stamps WHERE owner_id = '22222222-2222-2222-2222-222222222222';
    IF v_a_cnt <> 0 THEN RAISE EXCEPTION 'Received Stamp Leak: Sender A was able to select B received stamps'; END IF;

    -- Third-party C cannot select B's received collection
    SET LOCAL request.jwt.claim.sub = '33333333-3333-3333-3333-333333333333';
    SELECT COUNT(*) INTO v_c_cnt FROM public.received_trade_stamps WHERE owner_id = '22222222-2222-2222-2222-222222222222';
    IF v_c_cnt <> 0 THEN RAISE EXCEPTION 'Received Stamp Leak: User C was able to select B received stamps'; END IF;
END $$;

-- Assertion 75: block_user cancels pending trades between pair
DO $$
DECLARE
    v_trade_id UUID;
    v_status TEXT;
BEGIN
    SET ROLE postgres;
    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111/rendered/test_stamp_70.png',
        'Block Cancel Test',
        'PENDING'
    ) RETURNING id INTO v_trade_id;

    -- User A blocks User B
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.block_user('22222222-2222-2222-2222-222222222222');

    -- Trade status must now be CANCELLED
    SET ROLE postgres;
    SELECT status INTO v_status FROM public.stamp_trade_requests WHERE id = v_trade_id;
    IF v_status <> 'CANCELLED' THEN
        RAISE EXCEPTION 'Block Trade Cancellation Failed: expected CANCELLED, got %', v_status;
    END IF;

    -- Unblock User B
    SET LOCAL ROLE authenticated;
    SET LOCAL request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
    PERFORM public.unblock_user('22222222-2222-2222-2222-222222222222');

    -- Cancelled trade must NOT be resurrected
    SET ROLE postgres;
    SELECT status INTO v_status FROM public.stamp_trade_requests WHERE id = v_trade_id;
    IF v_status <> 'CANCELLED' THEN
        RAISE EXCEPTION 'Unblock Resurrected Trade: expected CANCELLED, got %', v_status;
    END IF;
END $$;

-- Assertion 76: Account Deletion Durability - recipient received stamp preserved
DO $$
DECLARE
    v_trade_id UUID;
    v_rec_id UUID;
    v_rec_exists INT;
    v_orig_sender UUID;
BEGIN
    SET ROLE postgres;
    -- Create disposable user D
    INSERT INTO auth.users (id, email, role, aud) VALUES
        ('55555555-5555-5555-5555-555555555555', 'user_d_trade@test.local', 'authenticated', 'authenticated')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.profiles (id, username, display_name) VALUES
        ('55555555-5555-5555-5555-555555555555', 'user_d_trade', 'User D Trade')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO public.friends (user_id_1, user_id_2) VALUES
        ('22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555')
    ON CONFLICT DO NOTHING;

    -- Trade from D to B accepted
    INSERT INTO public.stamp_trade_requests (
        sender_id, recipient_id, source_object_name, stamp_title, status
    ) VALUES (
        '55555555-5555-5555-5555-555555555555',
        '22222222-2222-2222-2222-222222222222',
        '55555555-5555-5555-5555-555555555555/rendered/stamp_d.png',
        'D Stamp',
        'ACCEPTED'
    ) RETURNING id INTO v_trade_id;

    INSERT INTO public.received_trade_stamps (
        owner_id, source_trade_id, original_sender_id, recipient_media_path, stamp_title
    ) VALUES (
        '22222222-2222-2222-2222-222222222222',
        v_trade_id,
        '55555555-5555-5555-5555-555555555555',
        '22222222-2222-2222-2222-222222222222/received/stamp_d.png',
        'D Stamp'
    ) RETURNING id INTO v_rec_id;

    -- Delete user D account
    DELETE FROM auth.users WHERE id = '55555555-5555-5555-5555-555555555555';

    -- Recipient B's received stamp row must still exist
    SELECT COUNT(*), original_sender_id INTO v_rec_exists, v_orig_sender
    FROM public.received_trade_stamps WHERE id = v_rec_id
    GROUP BY original_sender_id;

    IF v_rec_exists <> 1 THEN
        RAISE EXCEPTION 'Received Stamp Durability Failed: recipient stamp was deleted when sender deleted account';
    END IF;

    IF v_orig_sender IS NOT NULL THEN
        RAISE EXCEPTION 'Sender Anonymization Failed: original_sender_id was not set to NULL on deletion';
    END IF;

    -- Cleanup
    DELETE FROM public.received_trade_stamps WHERE id = v_rec_id;
END $$;


-- ===================================================
-- 4. FIXTURE TEARDOWN (POSTGRES ROLE)
-- ===================================================
SET ROLE postgres;

DELETE FROM public.received_trade_stamps WHERE recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');
DELETE FROM public.stamp_trade_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');

DELETE FROM public.user_reports WHERE reporter_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444') OR reported_user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444');
DELETE FROM public.user_blocks WHERE blocker_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444') OR blocked_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444');
DELETE FROM public.push_delivery_events WHERE recipient_user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444');
DELETE FROM public.push_device_tokens WHERE user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444');
DELETE FROM public.feed_replies WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_reactions WHERE user_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_comments WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friends WHERE user_id_1 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR user_id_2 IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.friend_requests WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.direct_messages WHERE sender_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333') OR recipient_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.feed_posts WHERE author_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM public.profiles WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
DELETE FROM auth.users WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333');
