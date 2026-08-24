package com.mipastudio.memostamp.repository

import com.mipastudio.memostamp.domain.model.AudienceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedMemoStampRepositoryTest {

    @Test
    fun testInitialStateHasSampleData() {
        val repo = SharedMemoStampRepository()
        assertNotNull(repo.currentUser.value)
        assertEquals("user_me", repo.currentUser.value.uid)
        assertTrue(repo.stamps.value.isNotEmpty())
        assertTrue(repo.feedPosts.value.isNotEmpty())
        assertTrue(repo.friends.value.isNotEmpty())
        assertTrue(repo.collections.value.isNotEmpty())
    }

    @Test
    fun testToggleLikeTogglesStateAndCount() {
        val repo = SharedMemoStampRepository()
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
        val initialFeedCount = repo.feedPosts.value.size

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
        assertEquals(initialFeedCount + 1, repo.feedPosts.value.size)
    }

    @Test
    fun testAddStampWithOnlyMeAudienceDoesNotPublishToFeed() {
        val repo = SharedMemoStampRepository()
        val initialFeedCount = repo.feedPosts.value.size

        repo.addStamp(
            title = "Private Stamp",
            note = "Private Note",
            location = "Secret",
            imageUrl = "https://example.com/private.jpg",
            audience = AudienceType.ONLY_ME
        )

        assertEquals(initialFeedCount, repo.feedPosts.value.size)
    }

    @Test
    fun testSendAndAcceptTradeRequest() {
        val repo = SharedMemoStampRepository()
        val stamp = repo.stamps.value.first()

        repo.sendTradeRequest(friendId = "user_huy", stampId = stamp.id)
        val createdTrade = repo.tradeRequests.value.first()

        assertEquals("PENDING", createdTrade.status)
        assertEquals(stamp.title, createdTrade.stampTitle)

        repo.acceptTrade(createdTrade.id)
        val acceptedTrade = repo.tradeRequests.value.first { it.id == createdTrade.id }
        assertEquals("ACCEPTED", acceptedTrade.status)
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
