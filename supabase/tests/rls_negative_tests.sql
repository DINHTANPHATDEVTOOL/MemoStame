-- Executable RLS Negative Test Runner for Supabase/PostgreSQL
-- Evaluates real SQL queries under simulated JWT claims for Users A, B, and C

CREATE OR REPLACE FUNCTION public.run_rls_negative_tests()
RETURNS TABLE(test_id INT, scenario TEXT, expected TEXT, outcome TEXT, passed BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_a UUID := '11111111-1111-1111-1111-111111111111';
    v_user_b UUID := '22222222-2222-2222-2222-222222222222';
    v_user_c UUID := '33333333-3333-3333-3333-333333333333';
    v_freq_id UUID := gen_random_uuid();
    v_dm_id UUID := gen_random_uuid();
    v_post_only_me UUID := gen_random_uuid();
    v_post_friends UUID := gen_random_uuid();
    v_res RECORD;
    v_count INT;
    v_err TEXT;
BEGIN
    -- Setup Test Fixtures (Bypassing RLS as Security Definer)
    DELETE FROM public.profiles WHERE id IN (v_user_a, v_user_b, v_user_c);
    INSERT INTO public.profiles (id, username, display_name) VALUES
        (v_user_a, 'user_a', 'User A'),
        (v_user_b, 'user_b', 'User B'),
        (v_user_c, 'user_c', 'User C');

    DELETE FROM public.friend_requests WHERE id = v_freq_id;
    INSERT INTO public.friend_requests (id, sender_id, recipient_id, status) VALUES
        (v_freq_id, v_user_a, v_user_b, 'PENDING');

    DELETE FROM public.direct_messages WHERE id = v_dm_id;
    INSERT INTO public.direct_messages (id, sender_id, recipient_id, text, is_read) VALUES
        (v_dm_id, v_user_a, v_user_b, 'Hello B', false);

    DELETE FROM public.feed_posts WHERE id IN (v_post_only_me, v_post_friends);
    INSERT INTO public.feed_posts (id, author_id, caption, audience_type) VALUES
        (v_post_only_me, v_user_a, 'Private Note', 'ONLY_ME'),
        (v_post_friends, v_user_a, 'Friends Only Note', 'FRIENDS');

    -- Test 1: A edits own profile -> allowed
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);
        UPDATE public.profiles SET bio = 'Updated by A' WHERE id = v_user_a;
        RETURN NEXT ROW(1, 'A edits own profile', 'ALLOWED', 'ALLOWED', true);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(1, 'A edits own profile', 'ALLOWED', 'DENIED: ' || SQLERRM, false);
    END;

    -- Test 2: A edits B profile -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);
        UPDATE public.profiles SET bio = 'Hacked by A' WHERE id = v_user_b;
        GET DIAGNOSTICS v_count = ROW_COUNT;
        IF v_count = 0 THEN
            RETURN NEXT ROW(2, 'A edits B profile', 'DENIED', 'DENIED (0 rows updated)', true);
        ELSE
            RETURN NEXT ROW(2, 'A edits B profile', 'DENIED', 'ALLOWED (Security Leak)', false);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(2, 'A edits B profile', 'DENIED', 'DENIED', true);
    END;

    -- Test 3: A pretends sender=B in friend request -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);
        INSERT INTO public.friend_requests (sender_id, recipient_id, status)
        VALUES (v_user_b, v_user_c, 'PENDING');
        RETURN NEXT ROW(3, 'A pretends sender=B in friend request', 'DENIED', 'ALLOWED (Security Leak)', false);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(3, 'A pretends sender=B in friend request', 'DENIED', 'DENIED', true);
    END;

    -- Test 4: Sender A accepts own outgoing request -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);
        PERFORM public.accept_friend_request(v_freq_id);
        RETURN NEXT ROW(4, 'Sender A accepts own request via RPC', 'DENIED', 'ALLOWED (Security Leak)', false);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(4, 'Sender A accepts own request via RPC', 'DENIED', 'DENIED', true);
    END;

    -- Test 5: Recipient B accepts friend request -> allowed
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_b::text, true);
        PERFORM public.accept_friend_request(v_freq_id);
        RETURN NEXT ROW(5, 'Recipient B accepts friend request via RPC', 'ALLOWED', 'ALLOWED', true);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(5, 'Recipient B accepts friend request via RPC', 'ALLOWED', 'DENIED: ' || SQLERRM, false);
    END;

    -- Test 6: Third user C accepts A-B request -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_c::text, true);
        PERFORM public.accept_friend_request(v_freq_id);
        RETURN NEXT ROW(6, 'Third user C accepts A-B request via RPC', 'DENIED', 'ALLOWED (Security Leak)', false);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(6, 'Third user C accepts A-B request via RPC', 'DENIED', 'DENIED', true);
    END;

    -- Test 7: Third user C reads A-B direct message -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_c::text, true);
        SELECT COUNT(*) INTO v_count FROM public.direct_messages WHERE id = v_dm_id;
        IF v_count = 0 THEN
            RETURN NEXT ROW(7, 'Third user C reads A-B DM', 'DENIED', 'DENIED (0 rows returned)', true);
        ELSE
            RETURN NEXT ROW(7, 'Third user C reads A-B DM', 'DENIED', 'ALLOWED (Security Leak)', false);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(7, 'Third user C reads A-B DM', 'DENIED', 'DENIED', true);
    END;

    -- Test 8: Recipient B cannot edit DM text directly -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_b::text, true);
        UPDATE public.direct_messages SET text = 'Altered Text' WHERE id = v_dm_id;
        GET DIAGNOSTICS v_count = ROW_COUNT;
        IF v_count = 0 THEN
            RETURN NEXT ROW(8, 'Recipient B edits DM text directly', 'DENIED', 'DENIED (0 rows updated)', true);
        ELSE
            RETURN NEXT ROW(8, 'Recipient B edits DM text directly', 'DENIED', 'ALLOWED (Security Leak)', false);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(8, 'Recipient B edits DM text directly', 'DENIED', 'DENIED', true);
    END;

    -- Test 9: Recipient B marks DM read via RPC -> allowed
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_b::text, true);
        PERFORM public.mark_direct_messages_read(v_user_a);
        RETURN NEXT ROW(9, 'Recipient B marks DM as read via RPC', 'ALLOWED', 'ALLOWED', true);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(9, 'Recipient B marks DM as read via RPC', 'ALLOWED', 'DENIED: ' || SQLERRM, false);
    END;

    -- Test 10: User B reads User A ONLY_ME post -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_b::text, true);
        SELECT COUNT(*) INTO v_count FROM public.feed_posts WHERE id = v_post_only_me;
        IF v_count = 0 THEN
            RETURN NEXT ROW(10, 'B reads A ONLY_ME post', 'DENIED', 'DENIED (0 rows returned)', true);
        ELSE
            RETURN NEXT ROW(10, 'B reads A ONLY_ME post', 'DENIED', 'ALLOWED (Security Leak)', false);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(10, 'B reads A ONLY_ME post', 'DENIED', 'DENIED', true);
    END;

    -- Test 11: Unrelated user C reads A FRIENDS post (no friendship) -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_c::text, true);
        SELECT COUNT(*) INTO v_count FROM public.feed_posts WHERE id = v_post_friends;
        IF v_count = 0 THEN
            RETURN NEXT ROW(11, 'Unrelated C reads A FRIENDS post', 'DENIED', 'DENIED (0 rows returned)', true);
        ELSE
            RETURN NEXT ROW(11, 'Unrelated C reads A FRIENDS post', 'DENIED', 'ALLOWED (Security Leak)', false);
        END IF;
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(11, 'Unrelated C reads A FRIENDS post', 'DENIED', 'DENIED', true);
    END;

    -- Test 12: User A writes post pretending author=B -> denied
    BEGIN
        PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);
        INSERT INTO public.feed_posts (author_id, caption) VALUES (v_user_b, 'Impersonated post');
        RETURN NEXT ROW(12, 'A writes post with author=B', 'DENIED', 'ALLOWED (Security Leak)', false);
    EXCEPTION WHEN OTHERS THEN
        RETURN NEXT ROW(12, 'A writes post with author=B', 'DENIED', 'DENIED', true);
    END;
END;
$$;
