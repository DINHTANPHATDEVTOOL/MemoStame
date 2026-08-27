package com.mipastudio.memostamp.repository

import com.mipastudio.memostamp.domain.model.AudienceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedMemoStampRepositoryTest {

    @Test
    fun testInitialStateIsEmpty() {
        val repo = SharedMemoStampRepository()
        assertNotNull(repo.currentUser.value)
        assertEquals("user_me", repo.currentUser.value.uid)
        assertTrue(repo.stamps.value.isEmpty())
        assertTrue(repo.feedPosts.value.isEmpty())
        assertTrue(repo.friends.value.isEmpty())
        assertTrue(repo.friendRequests.value.isEmpty())
        assertTrue(repo.tradeRequests.value.isEmpty())
    }

    @Test
    fun testToggleLikeTogglesStateAndCount() {
        val repo = SharedMemoStampRepository()
        repo.loadDemoFixtures()
        val post = repo.feedPosts.value.first()
        val initialLiked = post.isLikedByMe
        val initialCount = post.reactionCount

        repo.toggleLike(post.id)

        val updatedPost = repo.feedPosts.value.first { it.id == post.id }
        assertEquals(!initialLiked, updatedPost.isLikedByMe)
        assertEquals(if (!initialLiked) initialCount + 1 else initialCount - 1, updatedPost.reactionCount)
    }

    @Test
    fun testAddCommentAddsCommentAndIncrementsCount() {
        val repo = SharedMemoStampRepository()
        repo.loadDemoFixtures()
        val post = repo.feedPosts.value.first()
        val initialCommentCount = post.commentCount

        repo.addComment(post.id, "Testing Kotlin Multiplatform Comment")

        val updatedPost = repo.feedPosts.value.first { it.id == post.id }
        assertEquals(initialCommentCount + 1, updatedPost.commentCount)
        assertTrue(updatedPost.comments.any { it.content == "Testing Kotlin Multiplatform Comment" })
    }

    @Test
    fun testDeleteCommentRemovesCommentAndDecrementsCount() {
        val repo = SharedMemoStampRepository()
        repo.loadDemoFixtures()
        val post = repo.feedPosts.value.first { it.comments.isNotEmpty() }
        val commentToDelete = post.comments.first()
        val initialCount = post.commentCount

        repo.deleteComment(post.id, commentToDelete.id)

        val updatedPost = repo.feedPosts.value.first { it.id == post.id }
        assertEquals(initialCount - 1, updatedPost.commentCount)
        assertFalse(updatedPost.comments.any { it.id == commentToDelete.id })
    }

    @Test
    fun testAddStampCreatesNewStampAndUpdatesUserStats() {
        val repo = SharedMemoStampRepository()
        val initialCreatedCount = repo.currentUser.value.stampsCreatedCount
        val initialStampCount = repo.stamps.value.size

        val newStamp = repo.addStamp(
            title = "Test KMP Stamp",
            note = "Testing KMP Stamp Creation",
            location = "Đà Lạt",
            imageUrl = "https://example.com/stamp.jpg",
            shape = "heart",
            collectionId = "col_travel",
            audience = AudienceType.FRIENDS
        )

        assertEquals("Test KMP Stamp", newStamp.title)
        assertEquals(initialStampCount + 1, repo.stamps.value.size)
        assertEquals(initialCreatedCount + 1, repo.currentUser.value.stampsCreatedCount)
    }

    @Test
    fun testSendAndAcceptTradeRequest() {
        val repo = SharedMemoStampRepository()
        val friend = repo.addFriend("Huy Tran", "user_huy")
        val stamp = repo.addStamp(
            title = "Test Trade Stamp",
            note = "Trade Note",
            location = "Saigon",
            imageUrl = "https://example.com/trade_stamp.jpg"
        )

        val success = repo.sendTradeRequest(friendId = friend.id, stampId = stamp.id)
        assertTrue(success)
        val createdTrade = repo.tradeRequests.value.first()

        assertEquals("PENDING", createdTrade.status)
        assertEquals(stamp.title, createdTrade.stampTitle)
        assertEquals(repo.currentUser.value.uid, createdTrade.senderId)

        // Sender cannot accept own outgoing trade
        val accepted = repo.acceptTrade(createdTrade.id)
        assertFalse(accepted)
        assertTrue(repo.tradeRequests.value.any { it.id == createdTrade.id })
    }

    @Test
    fun testSenderCannotAcceptOrRejectOwnFriendRequest() {
        val repo = SharedMemoStampRepository()
        val result = repo.sendFriendRequest("target_user")
        assertTrue(result.success)
        val req = repo.friendRequests.value.first()

        // Current user ("user_me") is sender. Accept and reject must fail.
        assertFalse(repo.acceptFriendRequest(req.id))
        assertFalse(repo.rejectFriendRequest(req.id))
        assertTrue(repo.friendRequests.value.any { it.id == req.id })
    }

    @Test
    fun testSenderCanCancelOwnFriendRequest() {
        val repo = SharedMemoStampRepository()
        val result = repo.sendFriendRequest("target_user")
        assertTrue(result.success)
        val req = repo.friendRequests.value.first()

        // Sender can cancel outgoing request
        val cancelled = repo.cancelOutgoingFriendRequest(req.id)
        assertTrue(cancelled)
        assertTrue(repo.friendRequests.value.none { it.id == req.id })
    }

    @Test
    fun testRecipientCanAcceptAndRejectFriendRequest() {
        val repo = SharedMemoStampRepository()
        val incomingReq1 = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_incoming_1",
            senderName = "Linh Pham",
            senderUsername = "linh_pham",
            senderAvatar = "https://example.com/linh.jpg",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_linh",
            recipientId = "user_me",
            recipientUsername = "phat_memostamp"
        )
        val incomingReq2 = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_incoming_2",
            senderName = "Nam Vo",
            senderUsername = "nam_vo",
            senderAvatar = "https://example.com/nam.jpg",
            status = "PENDING",
            createdAt = 1001L,
            senderId = "user_nam",
            recipientId = "user_me",
            recipientUsername = "phat_memostamp"
        )
        repo.restoreFriendRequests(listOf(incomingReq1, incomingReq2))

        // Recipient can accept
        val accepted = repo.acceptFriendRequest(incomingReq1.id)
        assertTrue(accepted)
        assertTrue(repo.friends.value.any { it.username == "linh_pham" })
        assertTrue(repo.friendRequests.value.none { it.id == incomingReq1.id })

        // Recipient can reject
        val rejected = repo.rejectFriendRequest(incomingReq2.id)
        assertTrue(rejected)
        assertTrue(repo.friendRequests.value.none { it.id == incomingReq2.id })
    }

    @Test
    fun testRecipientCanAcceptAndRejectTradeRequest() {
        val repo = SharedMemoStampRepository()
        val trade1 = com.mipastudio.memostamp.domain.model.TradeRequest(
            id = "trade_in_1",
            senderName = "Linh Pham",
            senderAvatar = "",
            stampTitle = "Stamp 1",
            stampUrl = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_linh",
            recipientId = "user_me"
        )
        val trade2 = com.mipastudio.memostamp.domain.model.TradeRequest(
            id = "trade_in_2",
            senderName = "Nam Vo",
            senderAvatar = "",
            stampTitle = "Stamp 2",
            stampUrl = "",
            status = "PENDING",
            createdAt = 1001L,
            senderId = "user_nam",
            recipientId = "user_me"
        )
        repo.restoreTradeRequests(listOf(trade1, trade2))

        // Recipient accepts trade
        val accepted = repo.acceptTrade(trade1.id)
        assertTrue(accepted)
        assertTrue(repo.tradeRequests.value.none { it.id == trade1.id })

        // Recipient rejects trade
        val rejected = repo.rejectTrade(trade2.id)
        assertTrue(rejected)
        assertTrue(repo.tradeRequests.value.none { it.id == trade2.id })
    }

    @Test
    fun testThirdUserCannotAcceptOrRejectOrCancelFriendRequest() {
        val repo = SharedMemoStampRepository()
        // Request between user_linh (sender) and user_nam (recipient).
        // Current user is "user_me".
        val thirdPartyReq = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_3rd",
            senderName = "Linh Pham",
            senderUsername = "linh_pham",
            senderAvatar = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_linh",
            recipientId = "user_nam",
            recipientUsername = "nam_vo"
        )
        repo.restoreFriendRequests(listOf(thirdPartyReq))

        assertFalse(repo.acceptFriendRequest(thirdPartyReq.id))
        assertFalse(repo.rejectFriendRequest(thirdPartyReq.id))
        assertFalse(repo.cancelOutgoingFriendRequest(thirdPartyReq.id))
        assertTrue(repo.friendRequests.value.any { it.id == thirdPartyReq.id })
    }

    @Test
    fun testThirdUserCannotAcceptOrRejectOrCancelTradeRequest() {
        val repo = SharedMemoStampRepository()
        // Trade between user_linh (sender) and user_nam (recipient).
        // Current user is "user_me".
        val thirdPartyTrade = com.mipastudio.memostamp.domain.model.TradeRequest(
            id = "trade_3rd",
            senderName = "Linh Pham",
            senderAvatar = "",
            stampTitle = "Stamp 3rd",
            stampUrl = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_linh",
            recipientId = "user_nam"
        )
        repo.restoreTradeRequests(listOf(thirdPartyTrade))

        assertFalse(repo.acceptTrade(thirdPartyTrade.id))
        assertFalse(repo.rejectTrade(thirdPartyTrade.id))
        assertFalse(repo.cancelOutgoingTrade(thirdPartyTrade.id))
        assertTrue(repo.tradeRequests.value.any { it.id == thirdPartyTrade.id })
    }

    @Test
    fun testRestoreSocialDataMigratesLegacyBlankIds() {
        val repo = SharedMemoStampRepository()
        val legacyReq = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_legacy",
            senderName = "Legacy User",
            senderUsername = "legacy_user",
            senderAvatar = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "",
            recipientId = "user_me",
            recipientUsername = "me"
        )
        repo.restoreFriendRequests(listOf(legacyReq))

        val restoredReq = repo.friendRequests.value.first()
        assertEquals("user_legacy_user", restoredReq.senderId)
        assertEquals("user_me", restoredReq.recipientId)

        // After migration, current user ("user_me") can accept
        assertTrue(repo.acceptFriendRequest(restoredReq.id))
    }

    @Test
    fun testSenderCanCancelOwnTradeRequest() {
        val repo = SharedMemoStampRepository()
        val friend = repo.addFriend("Huy Tran", "user_huy")
        val stamp = repo.addStamp(
            title = "Test Trade Stamp",
            note = "Trade Note",
            location = "Saigon",
            imageUrl = "https://example.com/trade_stamp.jpg"
        )
        repo.sendTradeRequest(friendId = friend.id, stampId = stamp.id)
        val trade = repo.tradeRequests.value.first()

        val cancelled = repo.cancelOutgoingTrade(trade.id)
        assertTrue(cancelled)
        assertTrue(repo.tradeRequests.value.none { it.id == trade.id })
    }

    @Test
    fun testRestoreSocialData() {
        val repo = SharedMemoStampRepository()
        val friends = listOf(
            com.mipastudio.memostamp.domain.model.FriendItem("user_1", "User One", "user1", "", true, 0)
        )
        repo.restoreFriends(friends)
        assertEquals(1, repo.friends.value.size)
        assertEquals("User One", repo.friends.value.first().displayName)
    }

    @Test
    fun testUpdateProfileUpdatesUserInformation() {
        val repo = SharedMemoStampRepository()

        repo.updateProfile(
            displayName = "Minh Nguyen Updated",
            bio = "New Bio KMP",
            avatarUrl = "https://example.com/new_avatar.jpg"
        )

        val updatedUser = repo.currentUser.value
        assertEquals("Minh Nguyen Updated", updatedUser.displayName)
        assertEquals("New Bio KMP", updatedUser.bio)
        assertEquals("https://example.com/new_avatar.jpg", updatedUser.avatarUrl)
    }

    @Test
    fun testResetUserScopedStateClearsRuntimeData() {
        val repo = SharedMemoStampRepository()
        repo.loadDemoFixtures()
        repo.addFriend("Test Friend", "user_test")
        repo.addStamp("Test Stamp", "Note", "Loc", "https://example.com/img.png")
        repo.sendFriendRequest("target_user")

        assertTrue(repo.stamps.value.isNotEmpty())
        assertTrue(repo.friends.value.isNotEmpty())
        assertTrue(repo.friendRequests.value.isNotEmpty())
        assertTrue(repo.circles.value.isNotEmpty())

        repo.resetUserScopedState()

        assertTrue(repo.stamps.value.isEmpty())
        assertTrue(repo.friends.value.isEmpty())
        assertTrue(repo.friendRequests.value.isEmpty())
        assertTrue(repo.tradeRequests.value.isEmpty())
        assertTrue(repo.feedPosts.value.isEmpty())
        assertTrue(repo.circles.value.isEmpty())
        assertTrue(repo.badges.value.all { !it.isUnlocked })
    }

    @Test
    fun testStrictBlankRecipientDoesNotQualifyAsIncoming() {
        val repo = SharedMemoStampRepository()
        repo.setCurrentUser(
            com.mipastudio.memostamp.domain.model.UserProfile(
                uid = "user_alice",
                username = "alice",
                displayName = "Alice",
                avatarUrl = null,
                bio = "",
                stampsCreatedCount = 0,
                stampsCollectedCount = 0,
                placesVisitedCount = 0
            )
        )

        val unassignedFriendReq = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_blank_recip",
            senderName = "Bob",
            senderUsername = "bob",
            senderAvatar = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_bob",
            recipientId = "",
            recipientUsername = ""
        )
        val unassignedTradeReq = com.mipastudio.memostamp.domain.model.TradeRequest(
            id = "trade_blank_recip",
            senderName = "Bob",
            senderAvatar = "",
            stampTitle = "Stamp",
            stampUrl = "",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_bob",
            recipientId = ""
        )

        repo.restoreFriendRequests(listOf(unassignedFriendReq))
        repo.restoreTradeRequests(listOf(unassignedTradeReq))

        // Since recipientId is blank and current user is "user_alice", actions must be denied
        assertFalse(repo.acceptFriendRequest(unassignedFriendReq.id))
        assertFalse(repo.rejectFriendRequest(unassignedFriendReq.id))
        assertFalse(repo.acceptTrade(unassignedTradeReq.id))
        assertFalse(repo.rejectTrade(unassignedTradeReq.id))
    }

    @Test
    fun testLegacyV1MigrationIdentityIsolationGuards() {
        val aliceUid = "user_alice"
        val bobUid = "user_bob"

        // Helper function mimicking IOSLocalPersistenceStore migration guard
        fun shouldMigrateLegacy(legacyUserUid: String?, targetUserId: String): Boolean {
            return legacyUserUid != null && legacyUserUid == targetUserId
        }

        // 1. Legacy V1 A ("user_alice") must NOT migrate to B ("user_bob")
        assertFalse(shouldMigrateLegacy("user_alice", bobUid))

        // 2. Blank legacy UID must NOT migrate to arbitrary B ("user_bob")
        assertFalse(shouldMigrateLegacy("", bobUid))
        assertFalse(shouldMigrateLegacy(null, bobUid))

        // 3. Matching legacy UID MUST migrate
        assertTrue(shouldMigrateLegacy(aliceUid, aliceUid))
    }
}
