package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.remote.FriendRequest
import com.mipastudio.memostamp.data.remote.UserProfile
import com.mipastudio.memostamp.domain.model.AudienceType
import com.mipastudio.memostamp.domain.model.DirectMessage
import com.mipastudio.memostamp.domain.model.FeedPost
import com.mipastudio.memostamp.domain.model.FeedPostType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * End-to-End Integration Test Suite validating App Codebase Methods (SupabaseClient methods called by UserAuthRepository, ChatRepository & FeedRepository)
 * directly against the live Supabase Cloud Database.
 */
class AppSupabaseClientIntegrationTest {

    private val client = SupabaseClient()

    @Test
    fun testAppAuthRepositoryCloudMethods() = runBlocking {
        val testUid = "user_app_test_" + System.currentTimeMillis()
        val username = "app_tester_" + System.currentTimeMillis()
        val userProfile = UserProfile(
            userId = testUid,
            username = username,
            displayName = "App Codebase Integration Tester",
            email = "tester@memostamp.app",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
            coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200",
            bio = "Được khởi tạo bởi bộ test tự động từ ứng dụng Android",
            city = "Đà Lạt",
            isCloudSynced = true
        )

        // 1. App calls upsertProfile (UserAuthRepository.updateProfile / register)
        val upsertRes = client.upsertProfile(userProfile, passwordHash = "hash123")
        assertTrue("upsertProfile from app code should succeed: ${upsertRes.exceptionOrNull()?.message}", upsertRes.isSuccess)

        // 2. App calls getProfileByUsername (UserAuthRepository.login)
        val fetchedProfile = client.getProfileByUsername(username)
        assertNotNull("getProfileByUsername should return created profile", fetchedProfile)
        assertEquals(userProfile.displayName, fetchedProfile?.displayName)

        // 3. App calls searchProfiles (UserAuthRepository.searchUsers)
        val searchResults = client.searchProfiles(username)
        assertTrue("searchProfiles should contain created user", searchResults.any { it.userId == testUid })

        // 4. App calls getAllProfiles (UserAuthRepository.syncWithCloud)
        val allProfiles = client.getAllProfiles()
        assertTrue("getAllProfiles should contain created user", allProfiles.any { it.userId == testUid })
    }

    @Test
    fun testAppFriendshipRepositoryCloudMethods() = runBlocking {
        val aliceId = "user_alice_app_" + System.currentTimeMillis()
        val bobId = "user_bob_app_" + System.currentTimeMillis()
        val requestId = "freq_app_" + System.currentTimeMillis()

        // 1. Create profiles for Alice and Bob
        client.upsertProfile(UserProfile(userId = aliceId, username = "alice_app", displayName = "Alice App"))
        client.upsertProfile(UserProfile(userId = bobId, username = "bob_app", displayName = "Bob App"))

        // 2. App calls sendFriendRequest (UserAuthRepository.sendFriendRequest)
        val request = FriendRequest(
            id = requestId,
            senderId = aliceId,
            senderUsername = "alice_app",
            senderDisplayName = "Alice App",
            senderAvatar = "https://i.pravatar.cc/150?u=alice",
            recipientId = bobId,
            recipientUsername = "bob_app",
            recipientDisplayName = "Bob App",
            recipientAvatar = "https://i.pravatar.cc/150?u=bob",
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        val sendRes = client.sendFriendRequest(request)
        assertTrue("sendFriendRequest from app should succeed", sendRes.isSuccess)

        // 3. App calls getFriendRequestsForUser (UserAuthRepository.syncWithCloud)
        val bobRequests = client.getFriendRequestsForUser(bobId)
        assertTrue("Bob should see Alice pending request", bobRequests.any { it.id == requestId })

        // 4. App calls updateFriendRequestStatus (UserAuthRepository.acceptFriendRequest)
        val acceptRes = client.updateFriendRequestStatus(requestId, "ACCEPTED")
        assertTrue("updateFriendRequestStatus from app should succeed: ${acceptRes.exceptionOrNull()?.message}", acceptRes.isSuccess)

        // 5. App calls addFriendship (UserAuthRepository.acceptFriendRequest)
        val addFriendRes = client.addFriendship(aliceId, bobId)
        assertTrue("addFriendship from app should succeed: ${addFriendRes.exceptionOrNull()?.message}", addFriendRes.isSuccess)

        // 6. App calls getFriendsForUser (UserAuthRepository.loadFriendIds)
        val aliceFriends = client.getFriendsForUser(aliceId)
        assertTrue("Alice friend list should contain Bob", aliceFriends.contains(bobId))

        // 7. App calls removeFriendship (UserAuthRepository.removeFriend)
        val removeRes = client.removeFriendship(aliceId, bobId)
        assertTrue("removeFriendship from app should succeed: ${removeRes.exceptionOrNull()?.message}", removeRes.isSuccess)
    }

    @Test
    fun testAppChatRepositoryCloudMethods() = runBlocking {
        val senderId = "user_sender_app_" + System.currentTimeMillis()
        val recipientId = "user_recipient_app_" + System.currentTimeMillis()
        val messageId = "msg_app_" + System.currentTimeMillis()

        val msg = DirectMessage(
            id = messageId,
            senderId = senderId,
            senderName = "Người Gửi App",
            senderAvatar = "https://i.pravatar.cc/150?u=sender",
            recipientId = recipientId,
            recipientName = "Người Nhận App",
            recipientAvatar = "https://i.pravatar.cc/150?u=recipient",
            text = "Kỷ niệm gửi trực tiếp từ ChatRepository Android",
            stampId = "stamp_dalat_cloud",
            stampTitle = "Tem Bưu Chính Hồ Tuyền Lâm",
            stampImageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
            stampLocation = "Đà Lạt",
            createdAt = System.currentTimeMillis(),
            isRead = false
        )

        // 1. App calls sendDirectMessage (ChatRepository.sendMessage)
        val sendRes = client.sendDirectMessage(msg)
        assertTrue("sendDirectMessage from app should succeed: ${sendRes.exceptionOrNull()?.message}", sendRes.isSuccess)

        // 2. App calls getMessagesForUser (ChatRepository.syncMessagesLoop)
        val recipientMsgs = client.getMessagesForUser(recipientId)
        assertTrue("Recipient should fetch new message from cloud", recipientMsgs.any { it.id == messageId })

        // 3. App calls markMessagesAsRead (ChatRepository.markAsRead)
        val markRes = client.markMessagesAsRead(senderId, recipientId)
        assertTrue("markMessagesAsRead from app should succeed: ${markRes.exceptionOrNull()?.message}", markRes.isSuccess)

        // 4. App calls deleteDirectMessage (ChatRepository.deleteMessage)
        val deleteRes = client.deleteDirectMessage(messageId)
        assertTrue("deleteDirectMessage from app should succeed", deleteRes.isSuccess)
    }

    @Test
    fun testAppFeedRepositoryCloudMethods() = runBlocking {
        val authorId = "user_author_app_" + System.currentTimeMillis()
        val postId = "post_app_" + System.currentTimeMillis()

        val post = FeedPost(
            id = postId,
            stampId = "stamp_dalat_feed_100",
            stampUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
            stampTitle = "Bình Minh Trên Đỉnh Langbiang",
            shape = "classic",
            authorId = authorId,
            authorName = "Tác Giả Feed App",
            authorAvatar = "https://i.pravatar.cc/150?u=author",
            caption = "Kỷ niệm đăng từ FeedRepository trên ứng dụng Android",
            audienceType = AudienceType.EVERYONE,
            createdAt = System.currentTimeMillis(),
            type = FeedPostType.MEMORY,
            location = "Langbiang, Đà Lạt"
        )

        // 1. App calls createFeedPost (FeedRepository.createPostFromStamp)
        val createRes = client.createFeedPost(post)
        assertTrue("createFeedPost from app should succeed", createRes.isSuccess)

        // 2. App calls getFeedPosts (FeedRepository.syncFeedFromSupabase)
        val feedPosts = client.getFeedPosts()
        assertTrue("getFeedPosts should include created post", feedPosts.any { it.id == postId })

        // 3. App calls addFeedReaction (FeedRepository.toggleLike)
        val reactRecord = SupabaseFeedReactionRecord(
            id = "$postId:$authorId",
            postId = postId,
            userId = authorId,
            userName = "Tác Giả Feed App",
            emoji = "❤️",
            createdAt = System.currentTimeMillis()
        )
        val addReactRes = client.addFeedReaction(reactRecord)
        assertTrue("addFeedReaction from app should succeed", addReactRes.isSuccess)

        // 4. App calls getFeedReactions (FeedRepository.syncFeedFromSupabase)
        val reactions = client.getFeedReactions()
        assertTrue("getFeedReactions should include reaction", reactions.any { it.postId == postId && it.userId == authorId })

        // 5. App calls addFeedComment (FeedRepository.addComment)
        val commentRecord = SupabaseFeedCommentRecord(
            id = UUID.randomUUID().toString(),
            postId = postId,
            authorId = authorId,
            authorName = "Tác Giả Feed App",
            authorAvatar = "https://i.pravatar.cc/150?u=author",
            content = "Tem chụp góc này đẹp lắm!",
            createdAt = System.currentTimeMillis()
        )
        val addCommentRes = client.addFeedComment(commentRecord)
        assertTrue("addFeedComment from app should succeed", addCommentRes.isSuccess)

        // 6. App calls getFeedComments (FeedRepository.syncFeedFromSupabase)
        val comments = client.getFeedComments()
        assertTrue("getFeedComments should include comment", comments.any { it.postId == postId })

        // 7. App calls deleteFeedReaction (FeedRepository.toggleLike untoggle)
        val deleteReactRes = client.deleteFeedReaction(postId, authorId)
        assertTrue("deleteFeedReaction from app should succeed", deleteReactRes.isSuccess)
    }
}
