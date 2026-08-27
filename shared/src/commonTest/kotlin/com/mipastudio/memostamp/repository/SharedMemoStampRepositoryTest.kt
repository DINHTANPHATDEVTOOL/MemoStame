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
        val incomingReq = com.mipastudio.memostamp.domain.model.FriendRequestItem(
            id = "freq_incoming",
            senderName = "Linh Pham",
            senderUsername = "linh_pham",
            senderAvatar = "https://example.com/linh.jpg",
            status = "PENDING",
            createdAt = 1000L,
            senderId = "user_linh",
            recipientId = "user_me",
            recipientUsername = "phat_memostamp"
        )
        repo.restoreFriendRequests(listOf(incomingReq))

        val accepted = repo.acceptFriendRequest(incomingReq.id)
        assertTrue(accepted)
        assertTrue(repo.friends.value.any { it.username == "linh_pham" })
        assertTrue(repo.friendRequests.value.none { it.id == incomingReq.id })
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
}
