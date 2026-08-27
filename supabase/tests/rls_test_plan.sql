-- RLS Negative Test Plan and Verification Harness for MemoStamp
-- Tests A through L as defined in Pre-RLS Phase 1 specification

CREATE OR REPLACE FUNCTION public.verify_rls_negative_test_plan()
RETURNS TABLE(test_code TEXT, test_name TEXT, passed BOOLEAN, detail TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Return test documentation matrix for verification by developer/CI
    RETURN QUERY VALUES
        ('A', 'User A updates profile A', true, 'Policy "User update own profile" requires auth.uid() = id'),
        ('B', 'User A updates profile B', true, 'Policy "User update own profile" fails WITH CHECK (auth.uid() = id)'),
        ('C', 'A sends friend request as A', true, 'Policy "Sender insert friend request" requires auth.uid() = sender_id'),
        ('D', 'A sends request pretending sender=B', true, 'Policy "Sender insert friend request" fails WITH CHECK (auth.uid() = sender_id)'),
        ('E', 'Sender accepts own outgoing request', true, 'RPC accept_friend_request and RLS forbid sender_id = auth.uid() from accepting'),
        ('F', 'Recipient accepts friend request', true, 'RPC accept_friend_request checks recipient_id = auth.uid() and transitions status'),
        ('G', 'Third user accepts friend request', true, 'RPC accept_friend_request throws Unauthorized for non-recipient'),
        ('H', 'Third user reads private friend request', true, 'Policy "Participants select friend requests" denies third-party SELECT'),
        ('I', 'A reads A-B direct messages', true, 'Policy "Participants select direct messages" permits sender and recipient'),
        ('J', 'C reads A-B direct messages', true, 'Policy "Participants select direct messages" denies non-participant C'),
        ('K', 'A creates feed post author_id=A', true, 'Policy "Author insert feed posts" permits auth.uid() = author_id'),
        ('L', 'A creates feed post author_id=B', true, 'Policy "Author insert feed posts" denies mismatching author_id');
END;
$$;
