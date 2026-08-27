package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.FriendRequest
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSupabaseHttpTransport : SupabaseHttpTransport {
    var defaultResponse: Result<String> = Result.success("[]")
    val endpointResponses = mutableMapOf<String, Result<String>>()
    val callLogs = mutableListOf<String>()

    override fun executeHttp(
        client: SupabaseClient,
        endpoint: String,
        method: String,
        jsonBody: String?,
        prefer: String?,
        requireUserAuth: Boolean
    ): Result<String> {
        callLogs.add("$method $endpoint")
        return endpointResponses.entries.find { endpoint.contains(it.key) }?.value ?: defaultResponse
    }
}

class DummyContext : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this
}

class AndroidAuthSessionAndContractTest {

    private fun createRepository(transport: SupabaseHttpTransport = FakeSupabaseHttpTransport()): UserAuthRepository {
        val dummyContext = DummyContext()
        val client = SupabaseClient()
        client.transport = transport
        val sessionStore = AndroidAuthSessionStore(dummyContext)
        val authService = SupabaseAuthService.getInstance()
        return UserAuthRepository(dummyContext, supabaseClient = client, sessionStore = sessionStore, supabaseAuthService = authService)
    }

    @Test
    fun test1_senderCallingAcceptFriendRequestReturnsFailure() = runBlocking {
        val repo = createRepository()
        val senderUser = UserProfile(userId = "user_sender_123", username = "sender", displayName = "Sender")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_sender_123",
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = "user_recipient_456",
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = senderUser, requests = listOf(req))

        val result = repo.acceptFriendRequest("freq_1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test2_senderCallingDeclineFriendRequestReturnsFailure() = runBlocking {
        val repo = createRepository()
        val senderUser = UserProfile(userId = "user_sender_123", username = "sender", displayName = "Sender")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_sender_123",
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = "user_recipient_456",
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = senderUser, requests = listOf(req))

        val result = repo.declineFriendRequest("freq_1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test3_recipientAcceptReturnsSuccessWithFakeServerSuccess() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val acceptedReqJson = """[{"id":"freq_1","sender_id":"user_sender_123","sender_username":"sender","sender_display_name":"Sender","sender_avatar":"","recipient_id":"user_recipient_456","recipient_username":"recipient","recipient_display_name":"Recipient","recipient_avatar":"","status":"ACCEPTED","created_at":1000}]"""
        val friendsJson = """[{"id":"user_sender_123_user_recipient_456","user_id":"user_recipient_456","friend_id":"user_sender_123"}]"""
        transport.endpointResponses["friend_requests"] = Result.success(acceptedReqJson)
        transport.endpointResponses["friends"] = Result.success(friendsJson)

        val repo = createRepository(transport)
        val recipientUser = UserProfile(userId = "user_recipient_456", username = "recipient", displayName = "Recipient")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_sender_123",
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = "user_recipient_456",
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = recipientUser, requests = listOf(req))

        val result = repo.acceptFriendRequest("freq_1")

        assertTrue(result.isSuccess)
        assertTrue(repo.isFriend("user_sender_123"))
        assertEquals("ACCEPTED", repo.friendRequests.value.first().status)
    }

    @Test
    fun test4_recipientAcceptFailsAndRollsBackWhenFakeServerFails() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/accept_friend_request"] = Result.failure(IllegalStateException("500 Internal Server Error"))

        val repo = createRepository(transport)
        val recipientUser = UserProfile(userId = "user_recipient_456", username = "recipient", displayName = "Recipient")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_sender_123",
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = "user_recipient_456",
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = recipientUser, requests = listOf(req))

        val result = repo.acceptFriendRequest("freq_1")

        assertTrue(result.isFailure)
        assertFalse(repo.isFriend("user_sender_123"))
        assertEquals("PENDING", repo.friendRequests.value.first().status)
    }

    @Test
    fun test5_thirdUserCallingAcceptDeclineOrCancelReturnsFailure() = runBlocking {
        val repo = createRepository()
        val thirdUser = UserProfile(userId = "user_third_789", username = "third", displayName = "Third")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_sender_123",
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = "user_recipient_456",
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = thirdUser, requests = listOf(req))

        val acceptRes = repo.acceptFriendRequest("freq_1")
        val declineRes = repo.declineFriendRequest("freq_1")
        val cancelRes = repo.cancelOutgoingFriendRequest("freq_1")

        assertTrue(acceptRes.isFailure)
        assertTrue(declineRes.isFailure)
        assertTrue(cancelRes.isFailure)
    }

    @Test
    fun test6_guestSendingFriendRequestReturnsFailure() = runBlocking {
        val repo = createRepository()
        val guestUser = repo.createGuestUser()

        repo.setTestAuthState(isLoggedIn = false, authUser = guestUser)

        val targetUser = UserProfile(userId = "user_target", username = "target", displayName = "Target")
        val result = repo.sendFriendRequest(targetUser)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertEquals("Unauthorized: Guest cannot send friend requests", result.exceptionOrNull()?.message)
    }

    @Test
    fun test7_cancelOutgoingFakeServerFailureReturnsFailureAndRestoresState() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/cancel_friend_request"] = Result.failure(IllegalStateException("503 Service Unavailable"))

        val repo = createRepository(transport)
        val senderUser = UserProfile(userId = "user_a", username = "usera", displayName = "A")

        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_a",
            senderUsername = "usera",
            senderDisplayName = "A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientUsername = "userb",
            recipientDisplayName = "B",
            recipientAvatar = "",
            status = "PENDING"
        )

        repo.setTestAuthState(isLoggedIn = true, authUser = senderUser, requests = listOf(req))

        val result = repo.cancelOutgoingFriendRequest("freq_1")

        assertTrue(result.isFailure)
        assertEquals(1, repo.friendRequests.value.size)
        assertEquals("freq_1", repo.friendRequests.value.first().id)
    }

    @Test
    fun test8_unfriendFakeServerFailureReturnsFailureAndRestoresState() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/unfriend_user"] = Result.failure(IllegalStateException("503 Service Unavailable"))

        val repo = createRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "A")

        repo.setTestAuthState(isLoggedIn = true, authUser = userA, friends = setOf("user_b"))

        val result = repo.unfriend("user_b")

        assertTrue(result.isFailure)
        assertTrue(repo.isFriend("user_b"))
    }

    @Test
    fun test9_removeFriendshipSecondHttpStepFailureReturnsFailure() = runBlocking {
        var stepCount = 0

        val customTransport = object : SupabaseHttpTransport {
            override fun executeHttp(
                client: SupabaseClient,
                endpoint: String,
                method: String,
                jsonBody: String?,
                prefer: String?,
                requireUserAuth: Boolean
            ): Result<String> {
                stepCount++
                return if (stepCount == 2) {
                    Result.failure(IllegalStateException("HTTP 500 Internal Error on Step 2"))
                } else {
                    Result.success("[]")
                }
            }
        }

        val client = SupabaseClient()
        client.transport = customTransport
        client.userAccessToken = "valid_jwt"

        val res = client.removeFriendship("user_a", "user_b")

        assertTrue(res.isFailure)
        assertEquals("HTTP 500 Internal Error on Step 2", res.exceptionOrNull()?.message)
    }

    @Test
    fun test10_deleteFeedReactionHttpFailureReturnsFailure() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.failure(IllegalStateException("403 Forbidden"))

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "valid_jwt"

        val res = client.deleteFeedReaction("post_123", "user_456")

        assertTrue(res.isFailure)
        assertEquals("403 Forbidden", res.exceptionOrNull()?.message)
    }

    @Test
    fun test11_sessionStorageUnavailableNoPlaintextPersistence() {
        val brokenContext = DummyContext()
        val store = AndroidAuthSessionStore(context = brokenContext)

        assertFalse(store.isAvailable)
        assertFalse(store.sessionPersistenceAvailable)

        val session = AndroidAuthSession("access", "refresh", 999999L, "uid_123", "email")
        val saved = store.save(session)
        val loaded = store.load()

        assertFalse(saved)
        assertNull(loaded)
    }

    @Test
    fun test12_sessionSaveResultCapturedAndSessionNotPersistent() {
        val repo = createRepository()
        assertFalse(repo.isSessionPersistent.value)
    }

    @Test
    fun test13_localFriendRemovedWhenCloudSuccessIsEmpty() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("[]")

        val repo = createRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "A")
        repo.setTestAuthState(isLoggedIn = true, authUser = userA, friends = setOf("user_b"))

        assertTrue(repo.isFriend("user_b"))

        repo.syncWithSupabaseOnce()

        assertFalse(repo.isFriend("user_b"))
    }

    @Test
    fun test14_localPendingRequestRemovedWhenCloudSuccessNoLongerContainsIt() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("[]")

        val repo = createRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "A")
        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_b",
            senderUsername = "userb",
            senderDisplayName = "B",
            senderAvatar = "",
            recipientId = "user_a",
            recipientUsername = "usera",
            recipientDisplayName = "A",
            recipientAvatar = "",
            status = "PENDING"
        )
        repo.setTestAuthState(isLoggedIn = true, authUser = userA, requests = listOf(req))

        assertEquals(1, repo.friendRequests.value.size)

        repo.syncWithSupabaseOnce()

        assertTrue(repo.friendRequests.value.isEmpty())
    }

    @Test
    fun test15_localCacheRetainedWhenCloudRequestFails() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["friends"] = Result.failure(java.io.IOException("Network error"))
        transport.endpointResponses["friend_requests"] = Result.failure(java.io.IOException("Network error"))

        val repo = createRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "A")
        val req = FriendRequest(
            id = "freq_1",
            senderId = "user_b",
            senderUsername = "userb",
            senderDisplayName = "B",
            senderAvatar = "",
            recipientId = "user_a",
            recipientUsername = "usera",
            recipientDisplayName = "A",
            recipientAvatar = "",
            status = "PENDING"
        )
        repo.setTestAuthState(isLoggedIn = true, authUser = userA, friends = setOf("user_b"), requests = listOf(req))

        repo.syncWithSupabaseOnce()

        assertTrue(repo.isFriend("user_b"))
        assertEquals(1, repo.friendRequests.value.size)
    }

    @Test
    fun test16_authenticatedMutationFailsBeforeTransportWhenJWTIsMissing() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = null // JWT missing

        val res = client.addFriendship("user_a", "user_b")

        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalStateException)
        assertEquals("User authentication token required for RLS mutation", res.exceptionOrNull()?.message)
        assertTrue(transport.callLogs.isEmpty())
    }
}
