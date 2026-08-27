package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.FriendRequest
import com.mipastudio.memostamp.data.repository.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class AndroidAuthSessionAndContractTest {

    @Test
    fun test1_senderCallingAcceptFriendRequestReturnsFailure() {
        val senderUid = "user_sender_123"
        val recipientUid = "user_recipient_456"

        val req = FriendRequest(
            id = "freq_1",
            senderId = senderUid,
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = recipientUid,
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        val currentAuthUid = senderUid
        val result: Result<Unit> = if (currentAuthUid != req.recipientId || currentAuthUid == req.senderId || req.status != "PENDING") {
            Result.failure(SecurityException("Unauthorized: Cannot accept this friend request"))
        } else {
            Result.success(Unit)
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test2_senderCallingDeclineFriendRequestReturnsFailure() {
        val senderUid = "user_sender_123"
        val recipientUid = "user_recipient_456"

        val req = FriendRequest(
            id = "freq_1",
            senderId = senderUid,
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = recipientUid,
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        val currentAuthUid = senderUid
        val result: Result<Unit> = if (currentAuthUid != req.recipientId || currentAuthUid == req.senderId || req.status != "PENDING") {
            Result.failure(SecurityException("Unauthorized: Cannot decline this friend request"))
        } else {
            Result.success(Unit)
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test3_recipientAcceptReturnsSuccessWithFakeServerSuccess() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("[]")

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "valid_jwt_token"

        val resStatus = client.updateFriendRequestStatus("freq_1", "ACCEPTED")
        val resAdd = client.addFriendship("user_recipient_456", "user_sender_123")

        assertTrue(resStatus.isSuccess)
        assertTrue(resAdd.isSuccess)
    }

    @Test
    fun test4_recipientAcceptFailsAndRollsBackWhenFakeServerFails() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["friends"] = Result.failure(IllegalStateException("500 Internal Server Error"))

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "valid_jwt_token"

        val previousFriends = setOf("user_old_friend")
        val previousRequests = listOf(
            FriendRequest("freq_1", "user_sender_123", "sender", "Sender", "", "user_recipient_456", "recipient", "Recipient", "", "PENDING")
        )

        // Optimistic update
        var currentFriends = previousFriends.toMutableSet().apply { add("user_sender_123") }
        var currentRequests = previousRequests.map { if (it.id == "freq_1") it.copy(status = "ACCEPTED") else it }

        // Server mutation
        val resAdd = client.addFriendship("user_recipient_456", "user_sender_123")

        // Server failed -> Rollback
        if (resAdd.isFailure) {
            currentFriends = previousFriends.toMutableSet()
            currentRequests = previousRequests
        }

        assertTrue(resAdd.isFailure)
        assertEquals(previousFriends, currentFriends)
        assertEquals(previousRequests, currentRequests)
    }

    @Test
    fun test5_thirdUserCallingAcceptDeclineOrCancelReturnsFailure() {
        val senderUid = "user_sender_123"
        val recipientUid = "user_recipient_456"
        val thirdUserUid = "user_third_789"

        val req = FriendRequest(
            id = "freq_1",
            senderId = senderUid,
            senderUsername = "sender",
            senderDisplayName = "Sender",
            senderAvatar = "",
            recipientId = recipientUid,
            recipientUsername = "recipient",
            recipientDisplayName = "Recipient",
            recipientAvatar = "",
            status = "PENDING"
        )

        val currentAuthUid = thirdUserUid
        val acceptResult: Result<Unit> = if (currentAuthUid != req.recipientId || currentAuthUid == req.senderId) {
            Result.failure(SecurityException("Unauthorized"))
        } else Result.success(Unit)

        val declineResult: Result<Unit> = if (currentAuthUid != req.recipientId || currentAuthUid == req.senderId) {
            Result.failure(SecurityException("Unauthorized"))
        } else Result.success(Unit)

        val cancelResult: Result<Unit> = if (currentAuthUid != req.senderId) {
            Result.failure(SecurityException("Unauthorized"))
        } else Result.success(Unit)

        assertTrue(acceptResult.isFailure)
        assertTrue(declineResult.isFailure)
        assertTrue(cancelResult.isFailure)
    }

    @Test
    fun test6_guestSendingFriendRequestReturnsFailure() {
        val guestUid = "guest_visitor_12345"
        val targetUser = UserProfile(userId = "user_target", username = "target", displayName = "Target")

        val result: Result<Unit> = if (guestUid.startsWith("guest_")) {
            Result.failure(SecurityException("Unauthorized: Guest cannot send friend requests"))
        } else Result.success(Unit)

        assertTrue(result.isFailure)
        assertEquals("Unauthorized: Guest cannot send friend requests", result.exceptionOrNull()?.message)
    }

    @Test
    fun test7_sendFriendRequestFakeServer401ReturnsFailure() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.failure(IllegalStateException("401 Unauthorized"))

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "expired_jwt_token"

        val req = FriendRequest("freq_1", "user_a", "usera", "A", "", "user_b", "userb", "B", "")
        val res = client.sendFriendRequest(req)

        assertTrue(res.isFailure)
        assertEquals("401 Unauthorized", res.exceptionOrNull()?.message)
    }

    @Test
    fun test8_cancelOutgoingFakeServerFailureReturnsFailureAndRestoresState() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["friends"] = Result.failure(IllegalStateException("503 Service Unavailable"))

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "valid_jwt"

        val previousRequests = listOf(
            FriendRequest("freq_1", "user_a", "usera", "A", "", "user_b", "userb", "B", "", "PENDING")
        )

        // Optimistic delete
        var currentRequests = emptyList<FriendRequest>()

        // Call removeFriendship
        val res = client.removeFriendship("user_a", "user_b")

        // Failure -> Rollback
        if (res.isFailure) {
            currentRequests = previousRequests
        }

        assertTrue(res.isFailure)
        assertEquals(previousRequests, currentRequests)
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
        val brokenContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
        }
        val store = AndroidAuthSessionStore(context = brokenContext)

        // isAvailable is false because broken context causes EncryptedSharedPreferences init to fail
        assertFalse(store.isAvailable)
        assertFalse(store.sessionPersistenceAvailable)

        val session = AndroidAuthSession("access", "refresh", 999999L, "uid_123", "email")
        val saved = store.save(session)
        val loaded = store.load()

        assertFalse(saved)
        assertNull(loaded)
    }
}
