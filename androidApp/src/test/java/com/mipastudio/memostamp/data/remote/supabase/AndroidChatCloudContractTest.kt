package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.ChatRepository
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        transport.defaultResponse = Result.success("{}")

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
}
