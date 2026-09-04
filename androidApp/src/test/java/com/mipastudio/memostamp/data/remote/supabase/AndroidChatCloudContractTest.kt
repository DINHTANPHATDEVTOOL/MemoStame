package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidChatCloudContractTest {

    private fun createChatRepository(transport: SupabaseHttpTransport = FakeSupabaseHttpTransport()): Pair<ChatRepository, UserAuthRepository> {
        val dummyContext = DummyContext()
        val client = SupabaseClient()
        client.transport = transport
        val sessionStore = AndroidAuthSessionStore(dummyContext)
        val authService = SupabaseAuthService.getInstance()
        val authRepo = UserAuthRepository(dummyContext, supabaseClient = client, sessionStore = sessionStore, supabaseAuthService = authService)
        val chatRepo = ChatRepository(dummyContext, supabaseClient = client, authRepo = authRepo)
        return Pair(chatRepo, authRepo)
    }

    @Test
    fun test1_missingJWTFailsBeforeTransport() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = null // Missing JWT

        val resGet = client.getMessagesForUser("user_a")
        val resConv = client.getConversationBetween("user_a", "user_b")
        val resMark = client.markMessagesAsRead("user_a", "user_b")

        assertTrue(resGet.isFailure)
        assertTrue(resConv.isFailure)
        assertTrue(resMark.isFailure)
        assertTrue(transport.callLogs.isEmpty())
    }

    @Test
    fun test2_sendUsesAuthenticatedSender() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val validUuid = java.util.UUID.randomUUID().toString()
        val representationJson = """
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Hello B","created_at":"2026-08-28T03:11:16.123Z","is_read":false}]
        """.trimIndent()
        transport.defaultResponse = Result.success(representationJson)

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(recipient = userB, text = "Hello B")

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrThrow()
        assertEquals("user_a", sentMsg.senderId)
        assertEquals("user_b", sentMsg.recipientId)
        assertEquals("Hello B", sentMsg.text)
    }

    @Test
    fun test3_blankMessageRejected() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(recipient = userB, text = "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Message content cannot be blank", result.exceptionOrNull()?.message)
    }

    @Test
    fun test4_successfulCloudEmptyClearsStaleServerCache() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["direct_messages"] = Result.success("[]")

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        // Populate a local message
        val staleMsg = DirectMessage(
            id = "msg_stale_1",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Stale message",
            createdAt = 1000L
        )
        chatRepo.handleRealtimeEvent(staleMsg)
        assertEquals(1, chatRepo.getMessagesBetween("user_a", "user_b").size)

        // Load conversation from cloud returning []
        val result = chatRepo.loadConversation("user_b")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertTrue(chatRepo.getMessagesBetween("user_a", "user_b").isEmpty())
    }

    @Test
    fun test5_fetchFailureRetainsOfflineCache() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["direct_messages"] = Result.failure(java.io.IOException("503 Service Unavailable"))

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val offlineMsg = DirectMessage(
            id = "msg_offline_1",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Offline preserved message",
            createdAt = 1000L
        )
        chatRepo.handleRealtimeEvent(offlineMsg)
        assertEquals(1, chatRepo.getMessagesBetween("user_a", "user_b").size)

        val result = chatRepo.loadConversation("user_b")

        assertTrue(result.isFailure)
        assertEquals(1, chatRepo.getMessagesBetween("user_a", "user_b").size)
    }

    @Test
    fun test6_realtimeInsertMergesOnce() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val msg = DirectMessage(
            id = "msg_rt_1",
            senderId = "user_b",
            senderName = "User B",
            senderAvatar = "",
            recipientId = "user_a",
            recipientName = "User A",
            recipientAvatar = "",
            text = "Realtime message",
            createdAt = 2000L
        )

        chatRepo.handleRealtimeEvent(msg)

        assertEquals(1, chatRepo.messages.value.size)
        assertEquals("msg_rt_1", chatRepo.messages.value.first().id)
    }

    @Test
    fun test7_sendResponseAndRealtimeEchoDoesNotDuplicate() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val msg = DirectMessage(
            id = "msg_unique_123",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Sent message",
            createdAt = 3000L
        )

        // First merge (e.g., from send response)
        chatRepo.handleRealtimeEvent(msg)
        assertEquals(1, chatRepo.messages.value.size)

        // Echo merge (from Realtime stream)
        val echoMsg = msg.copy(isRead = true)
        chatRepo.handleRealtimeEvent(echoMsg)

        assertEquals(1, chatRepo.messages.value.size)
        assertTrue(chatRepo.messages.value.first().isRead)
    }

    @Test
    fun test8_twoIdenticalTextMessagesWithDifferentIDsAreBothPreserved() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val msg1 = DirectMessage(
            id = "msg_id_1",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Hello!",
            createdAt = 5000L
        )
        val msg2 = DirectMessage(
            id = "msg_id_2",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Hello!",
            createdAt = 5000L
        )

        chatRepo.handleRealtimeEvent(msg1)
        chatRepo.handleRealtimeEvent(msg2)

        assertEquals(2, chatRepo.messages.value.size)
    }

    @Test
    fun test9_unrelatedThirdUserMessageIgnored() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val thirdUserMsg = DirectMessage(
            id = "msg_third_1",
            senderId = "user_x",
            senderName = "User X",
            senderAvatar = "",
            recipientId = "user_y",
            recipientName = "User Y",
            recipientAvatar = "",
            text = "Eavesdropping target",
            createdAt = 6000L
        )

        chatRepo.handleRealtimeEvent(thirdUserMsg)

        assertTrue(chatRepo.messages.value.isEmpty())
    }

    @Test
    fun test10_logoutRemovesSubscriptionAndClearsState() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val msg = DirectMessage(
            id = "msg_1",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "Secret message",
            createdAt = 7000L
        )
        chatRepo.handleRealtimeEvent(msg)
        assertEquals(1, chatRepo.messages.value.size)

        chatRepo.onLogout()

        assertTrue(chatRepo.messages.value.isEmpty())
    }

    @Test
    fun test11_loginAsDifferentUserCannotSeePreviousAccountChatCache() = runBlocking {
        val (chatRepo, authRepo) = createChatRepository()

        // User A logs in and has chat messages
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val msgA = DirectMessage(
            id = "msg_a_1",
            senderId = "user_a",
            senderName = "User A",
            senderAvatar = "",
            recipientId = "user_b",
            recipientName = "User B",
            recipientAvatar = "",
            text = "User A's private chat",
            createdAt = 8000L
        )
        chatRepo.handleRealtimeEvent(msgA)
        assertEquals(1, chatRepo.messages.value.size)

        // Logout
        authRepo.logout()
        chatRepo.onLogout()
        assertTrue(chatRepo.messages.value.isEmpty())

        // User C logs in
        val userC = UserProfile(userId = "user_c", username = "userc", displayName = "User C")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userC)
        chatRepo.onUserChanged("user_c")

        assertTrue(chatRepo.messages.value.isEmpty())
    }

    @Test
    fun test12_markReadUsesRPC() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/mark_direct_messages_read"] = Result.success("true")

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)
        chatRepo.onUserChanged("user_a")

        val unreadMsg = DirectMessage(
            id = "msg_unread_1",
            senderId = "user_b",
            senderName = "User B",
            senderAvatar = "",
            recipientId = "user_a",
            recipientName = "User A",
            recipientAvatar = "",
            text = "Unread message",
            createdAt = 9000L,
            isRead = false
        )
        chatRepo.handleRealtimeEvent(unreadMsg)
        assertFalse(chatRepo.messages.value.first().isRead)

        val result = chatRepo.markAsReadCloud("user_b")

        assertTrue(result.isSuccess)
        assertTrue(chatRepo.messages.value.first().isRead)
        assertTrue(transport.callLogs.any { it.contains("rpc/mark_direct_messages_read") })
    }

    @Test
    fun test13_arbitraryBroadPATCHFallbackDoesNotExist() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        chatRepo.markAsReadCloud("user_b")

        // Assert no PATCH method was executed against direct_messages table
        val hasPatchCall = transport.callLogs.any { it.startsWith("PATCH") && it.contains("direct_messages") }
        assertFalse(hasPatchCall)
    }

    @Test
    fun test14_outgoingDirectMessageIdIsValidUUIDString() = runBlocking {
        val validUuid = java.util.UUID.randomUUID().toString()
        val representationJson = """
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Test UUID","created_at":"2026-08-28T03:11:16.123Z","is_read":false}]
        """.trimIndent()
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success(representationJson)

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(recipient = userB, text = "Test UUID")

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrThrow()
        assertFalse(sentMsg.id.startsWith("msg_"))
        assertTrue(SupabaseClient.isValidUuid(sentMsg.id))
        assertEquals(validUuid, sentMsg.id)
    }

    @Test
    fun test15_realtimeIsoTimestampParsingAndChronologicalOrdering() {
        val isoMicro = "2026-08-28T03:11:16.123456+00:00"
        val isoZ = "2026-08-28T03:12:16Z"

        val millisMicro = SupabaseClient.parseIsoStringToMillis(isoMicro)
        val millisZ = SupabaseClient.parseIsoStringToMillis(isoZ)

        assertTrue(millisMicro > 0L)
        assertTrue(millisZ > millisMicro)
    }

    @Test
    fun test16_realtimeJoinWithoutJwtFailsClosed() {
        val realtimeClient = SupabaseRealtimeClient()
        var received = false

        realtimeClient.connectAndSubscribe("user_a", accessToken = null) {
            received = true
        }

        assertFalse(received)
    }

    @Test
    fun test17_realtimeJwtRefreshUpdatesCredentialsAndPreventsStaleToken() {
        val realtimeClient = SupabaseRealtimeClient()
        realtimeClient.connectAndSubscribe("user_a", "old_token_123") {}

        assertEquals("old_token_123", realtimeClient.getCurrentAccessToken())
        assertEquals("user_a", realtimeClient.getCurrentUserId())

        realtimeClient.updateTokenOrReconnect("user_a", "new_refreshed_token_456")

        assertEquals("new_refreshed_token_456", realtimeClient.getCurrentAccessToken())
        assertNotEquals("old_token_123", realtimeClient.getCurrentAccessToken())
        assertEquals("user_a", realtimeClient.getCurrentUserId())
        assertFalse("Old subscription cannot continue and state resets on token update", realtimeClient.isSubscribedState())
    }

    @Test
    fun test18_realtimeLogoutClearsTokenAndSubscriptionState() {
        val realtimeClient = SupabaseRealtimeClient()
        realtimeClient.connectAndSubscribe("user_a", "token_xyz") {}

        assertEquals("user_a", realtimeClient.getCurrentUserId())
        assertEquals("token_xyz", realtimeClient.getCurrentAccessToken())

        realtimeClient.disconnect()

        assertNull(realtimeClient.getCurrentUserId())
        assertNull(realtimeClient.getCurrentAccessToken())
        assertFalse(realtimeClient.isSubscribedState())
        assertFalse(realtimeClient.isConnectedState())
    }

    @Test
    fun test19_realtimeUserSwitchGetsNewUserTokenOnly() {
        val realtimeClient = SupabaseRealtimeClient()
        realtimeClient.connectAndSubscribe("user_a", "token_a") {}

        assertEquals("user_a", realtimeClient.getCurrentUserId())
        assertEquals("token_a", realtimeClient.getCurrentAccessToken())

        realtimeClient.connectAndSubscribe("user_b", "token_b") {}

        assertEquals("user_b", realtimeClient.getCurrentUserId())
        assertEquals("token_b", realtimeClient.getCurrentAccessToken())
        assertNotEquals("token_a", realtimeClient.getCurrentAccessToken())
    }

    @Test
    fun test20_validServerRepresentationReturnsCanonicalRow() = runBlocking {
        val validUuid = java.util.UUID.randomUUID().toString()
        val json = """
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Canonical Server Text","created_at":"2026-08-28T03:30:00.000Z","is_read":false}]
        """.trimIndent()
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success(json)

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Local draft")
        assertTrue(res.isSuccess)
        val serverMsg = res.getOrThrow()
        assertEquals(validUuid, serverMsg.id)
        assertEquals("Canonical Server Text", serverMsg.text)
    }

    @Test
    fun test21_httpSuccessWithEmptyArrayFailsClosed() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("[]")

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Hello")
        assertTrue(res.isFailure)
    }

    @Test
    fun test22_httpSuccessWithEmptyObjectFailsClosed() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("{}")

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Hello")
        assertTrue(res.isFailure)
    }

    @Test
    fun test23_malformedServerResponseFailsClosed() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success("<html>Error 502 Bad Gateway</html>")

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Hello")
        assertTrue(res.isFailure)
    }

    @Test
    fun test24_invalidUuidServerIdFailsClosed() = runBlocking {
        val json = """
            [{"id":"msg_invalid_123","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Text","created_at":"2026-08-28T03:30:00Z","is_read":false}]
        """.trimIndent()
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success(json)

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Hello")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun test25_mismatchedSenderIdFailsClosed() = runBlocking {
        val validUuid = java.util.UUID.randomUUID().toString()
        val json = """
            [{"id":"$validUuid","sender_id":"attacker_user","sender_name":"Attacker","recipient_id":"user_b","recipient_name":"User B","text":"Spoofed Sender","created_at":"2026-08-28T03:30:00Z","is_read":false}]
        """.trimIndent()
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.success(json)

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Hello")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test26_sendFailureLeavesLocalChatUnchanged() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.defaultResponse = Result.failure(Exception("Network error"))

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val initialMessages = chatRepo.messages.value.size
        val res = chatRepo.sendMessageCloud(recipient = userB, text = "Failure Test")

        assertTrue(res.isFailure)
        assertEquals(initialMessages, chatRepo.messages.value.size)
    }

    @Test
    fun test27_isValidRemoteStampUrlValidationScenarios() {
        // Scenario 1: https URL -> ACCEPTED
        assertTrue(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("https://example.com/stamp.jpg"))
        // Scenario 2: http URL -> ACCEPTED
        assertTrue(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("http://example.com/stamp.jpg"))
        // Scenario 3: /data/user path -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("/data/user/0/com.mipastudio.memostamp/files/stamp.png"))
        // Scenario 4: /storage path -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("/storage/emulated/0/Pictures/stamp.png"))
        // Scenario 5: file:// URL -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("file:///data/user/0/app/stamp.png"))
        // Scenario 6: content:// URI -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("content://media/external/images/media/12345"))
        // Scenario 7: data:image base64 -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="))
        // Scenario 8: raw base64 string -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="))
        // Null / blank -> REJECTED
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(null))
        assertFalse(com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl("  "))
    }

    @Test
    fun test28_sendMessageWithLocalImagePathStripsImageAndPreservesText() = runBlocking {
        // Scenario 9: invalid/local image + text -> stamp_image_url is null, text sends safely
        val validUuid = java.util.UUID.randomUUID().toString()
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["direct_messages"] = Result.success("""
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Check this stamp!","stamp_id":"stamp_123","stamp_title":"My Local Stamp","stamp_image_url":null,"created_at":"2026-08-28T03:11:16.123Z","is_read":false}]
        """.trimIndent())

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(
            recipient = userB,
            text = "Check this stamp!",
            stampId = "stamp_123",
            stampTitle = "My Local Stamp",
            stampImageUrl = "/data/user/0/com.mipastudio.memostamp/files/local_stamp.png",
            stampLocation = "Hà Nội"
        )

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrThrow()
        assertNull(sentMsg.stampImageUrl)
        assertEquals("Check this stamp!", sentMsg.text)
        assertEquals("My Local Stamp", sentMsg.stampTitle)
        assertEquals("stamp_123", sentMsg.stampId)
    }

    @Test
    fun test29_sendMessageWithBase64ImageStripsImageAndPreservesMetadata() = runBlocking {
        // Scenario 10: invalid/local image (base64) + stamp metadata -> metadata remains usable, no base64 URI sent
        val validUuid = java.util.UUID.randomUUID().toString()
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["direct_messages"] = Result.success("""
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"📮 Đã gửi con tem: Vintage Stamp","stamp_id":"stamp_456","stamp_title":"Vintage Stamp","stamp_image_url":null,"stamp_location":"Đà Nẵng","created_at":"2026-08-28T03:11:16.123Z","is_read":false}]
        """.trimIndent())

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(
            recipient = userB,
            text = "",
            stampId = "stamp_456",
            stampTitle = "Vintage Stamp",
            stampImageUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            stampLocation = "Đà Nẵng"
        )

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrThrow()
        assertNull(sentMsg.stampImageUrl)
        assertEquals("stamp_456", sentMsg.stampId)
        assertEquals("Vintage Stamp", sentMsg.stampTitle)
        assertEquals("Đà Nẵng", sentMsg.stampLocation)
    }

    @Test
    fun test30_receivedMessageWithNullOrLocalImageSanitizesToNull() = runBlocking {
        // Scenario 11: received message with null or invalid image in Supabase record is sanitized to null in domain model
        val recordWithLocalPath = SupabaseDirectMessageRecord(
            id = java.util.UUID.randomUUID().toString(),
            senderId = "user_b",
            senderName = "User B",
            recipientId = "user_a",
            recipientName = "User A",
            text = "Sent message with rogue path",
            stampId = "stamp_789",
            stampTitle = "Rogue Stamp",
            stampImageUrl = "/storage/emulated/0/Pictures/rogue.jpg",
            createdAt = "2026-08-28T03:11:16.123Z"
        )

        val domainMsg = recordWithLocalPath.toDomain()
        assertNull("Rogue local path must be sanitized to null", domainMsg.stampImageUrl)
        assertEquals("stamp_789", domainMsg.stampId)
        assertEquals("Rogue Stamp", domainMsg.stampTitle)
    }

    @Test
    fun test31_sendMessageWithValidRemoteUrlPreservesRemoteUrl() = runBlocking {
        // Scenario 12: Valid HTTPS URL is preserved end to end
        val validUuid = java.util.UUID.randomUUID().toString()
        val remoteUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co/storage/v1/object/public/stamps/stamp_1.png"
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["direct_messages"] = Result.success("""
            [{"id":"$validUuid","sender_id":"user_a","sender_name":"User A","recipient_id":"user_b","recipient_name":"User B","text":"Remote Stamp","stamp_id":"stamp_999","stamp_title":"Cloud Stamp","stamp_image_url":"$remoteUrl","created_at":"2026-08-28T03:11:16.123Z","is_read":false}]
        """.trimIndent())

        val (chatRepo, authRepo) = createChatRepository(transport)
        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        val userB = UserProfile(userId = "user_b", username = "userb", displayName = "User B")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val result = chatRepo.sendMessageCloud(
            recipient = userB,
            text = "Remote Stamp",
            stampId = "stamp_999",
            stampTitle = "Cloud Stamp",
            stampImageUrl = remoteUrl,
            stampLocation = "Sài Gòn"
        )

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrThrow()
        assertEquals(remoteUrl, sentMsg.stampImageUrl)
    }
}
