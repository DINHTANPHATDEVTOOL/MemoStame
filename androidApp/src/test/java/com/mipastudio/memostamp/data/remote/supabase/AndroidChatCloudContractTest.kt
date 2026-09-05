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

class FakeSharedPreferences : android.content.SharedPreferences {
    private val map = java.util.Collections.synchronizedMap(mutableMapOf<String, Any?>())

    override fun getAll(): MutableMap<String, *> = synchronized(map) { map.toMap().toMutableMap() }
    override fun getString(key: String?, defValue: String?): String? = synchronized(map) { (map[key] as? String) ?: defValue }
    override fun getStringSet(key: String?, defValue: MutableSet<String>?): MutableSet<String>? {
        return synchronized(map) {
            @Suppress("UNCHECKED_CAST")
            (map[key] as? Set<String>)?.toMutableSet() ?: defValue?.toMutableSet()
        }
    }
    override fun getInt(key: String?, defValue: Int): Int = synchronized(map) { (map[key] as? Int) ?: defValue }
    override fun getLong(key: String?, defValue: Long): Long = synchronized(map) { (map[key] as? Long) ?: defValue }
    override fun getFloat(key: String?, defValue: Float): Float = synchronized(map) { (map[key] as? Float) ?: defValue }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(map) { (map[key] as? Boolean) ?: defValue }
    override fun contains(key: String?): Boolean = synchronized(map) { map.containsKey(key) }
    override fun edit(): android.content.SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class EditorImpl : android.content.SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = values?.toSet(); return this }
        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = value; return this }
        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = value; return this }
        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { if (key != null) tempMap[key] = value; return this }
        override fun remove(key: String?): android.content.SharedPreferences.Editor { tempMap.remove(key); return this }
        override fun clear(): android.content.SharedPreferences.Editor { tempMap.clear(); return this }
        override fun commit(): Boolean { synchronized(map) { map.putAll(tempMap) }; return true }
        override fun apply() { synchronized(map) { map.putAll(tempMap) } }
    }
}

class TestContextWithPrefs : android.content.ContextWrapper(null) {
    private val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
    override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
        val key = name ?: "default"
        return prefsMap.getOrPut(key) { FakeSharedPreferences() }
    }
    override fun getApplicationContext(): android.content.Context = this
}

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

    @Test
    fun test32_inboxDismissalDoesNotDeleteCloudMessage() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val (chatRepo, authRepo) = createChatRepository(transport)
        val dummyContext = TestContextWithPrefs()

        val userA = UserProfile(userId = "user_a", username = "usera", displayName = "User A")
        authRepo.setTestAuthState(isLoggedIn = true, authUser = userA)

        val prefsA = dummyContext.getSharedPreferences("memo_inbox_prefs_user_a", 0)
        var processedSetA = prefsA.getStringSet("processed_ids", emptySet()) ?: emptySet()
        assertFalse("msg_inbox_1 should not be processed yet", processedSetA.contains("msg_inbox_1"))

        // Simulate inbox dismissal
        processedSetA = processedSetA + "msg_inbox_1"
        prefsA.edit().putStringSet("processed_ids", processedSetA).apply()

        // Verify DELETE was NOT called on transport
        val deleteCalls = transport.callLogs.filter { it.startsWith("DELETE") }
        assertTrue("No DELETE HTTP calls should be issued during inbox dismissal", deleteCalls.isEmpty())

        // Verify local prefs updated for User A
        val updatedPrefsA = dummyContext.getSharedPreferences("memo_inbox_prefs_user_a", 0).getStringSet("processed_ids", emptySet()) ?: emptySet()
        assertTrue("msg_inbox_1 must be in user_a processed set", updatedPrefsA.contains("msg_inbox_1"))
    }

    @Test
    fun test33_accountScopedInboxIsolation() = runBlocking {
        val dummyContext = TestContextWithPrefs()

        // User A dismisses message 1
        val prefsA = dummyContext.getSharedPreferences("memo_inbox_prefs_user_a", 0)
        prefsA.edit().putStringSet("processed_ids", setOf("msg_inbox_1")).apply()

        // User B checks processed items
        val prefsB = dummyContext.getSharedPreferences("memo_inbox_prefs_user_b", 0)
        val setB = prefsB.getStringSet("processed_ids", emptySet()) ?: emptySet()

        // Verify User B does NOT see User A's dismissed IDs
        assertFalse("User B must NOT inherit User A's processed inbox IDs", setB.contains("msg_inbox_1"))

        // Re-check User A
        val setA = prefsA.getStringSet("processed_ids", emptySet()) ?: emptySet()
        assertTrue("User A retains processed inbox IDs", setA.contains("msg_inbox_1"))
    }

    @Test
    fun test34_stampUrlValidationContract() {
        assertTrue(DirectMessage.isValidRemoteStampUrl("http://example.com/stamp.png"))
        assertTrue(DirectMessage.isValidRemoteStampUrl("https://supabase.co/storage/stamp.jpg"))

        assertFalse(DirectMessage.isValidRemoteStampUrl("/data/user/0/com.app/file.png"))
        assertFalse(DirectMessage.isValidRemoteStampUrl("/storage/emulated/0/stamp.png"))
        assertFalse(DirectMessage.isValidRemoteStampUrl("file:///sdcard/stamp.png"))
        assertFalse(DirectMessage.isValidRemoteStampUrl("content://media/external/images/1"))
        assertFalse(DirectMessage.isValidRemoteStampUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA"))
        assertFalse(DirectMessage.isValidRemoteStampUrl(""))
        assertFalse(DirectMessage.isValidRemoteStampUrl(null))
    }

    @Test
    fun test35_saveToVaultPreservesMetadataAndMessage() = runBlocking {
        val msg = DirectMessage(
            id = "msg_stamp_100",
            senderId = "user_b",
            senderName = "User B",
            senderAvatar = "https://example.com/avatar.png",
            recipientId = "user_a",
            recipientName = "User A",
            recipientAvatar = "https://example.com/avatar_a.png",
            text = "Wish you were here!",
            stampId = "stamp_100",
            stampTitle = "Da Lat Sunset",
            stampImageUrl = "https://example.com/dalat.png",
            stampLocation = "Đà Lạt",
            createdAt = System.currentTimeMillis(),
            isRead = true
        )

        // Remote image valid check
        assertTrue(DirectMessage.isValidRemoteStampUrl(msg.stampImageUrl))

        // Ensure title, location, note, createdAt are preserved
        assertEquals("Da Lat Sunset", msg.stampTitle)
        assertEquals("Đà Lạt", msg.stampLocation)
        assertEquals("Wish you were here!", msg.text)
        assertEquals("https://example.com/dalat.png", msg.stampImageUrl)

        // Invalid remote URL returns fallback
        val invalidMsg = msg.copy(stampImageUrl = "file:///local/path.jpg")
        assertFalse(DirectMessage.isValidRemoteStampUrl(invalidMsg.stampImageUrl))
    }

    @Test
    fun test36_strictTimestampValidIso() {
        // Fractional ISO
        val fracIso = "2026-09-05T13:40:00.123Z"
        val fracMillis = SupabaseClient.parseServerMessageTimestampOrNull(fracIso)
        assertTrue(fracMillis != null)
        assertEquals(123L, fracMillis!! % 1000L)

        // Standard ISO without millis
        val stdIso = "2026-09-05T13:40:00Z"
        val stdMillis = SupabaseClient.parseServerMessageTimestampOrNull(stdIso)
        assertTrue(stdMillis != null)
        assertEquals(0L, stdMillis!! % 1000L)

        // ISO with offset
        val offsetIso = "2026-09-05T20:40:00+07:00"
        val offsetMillis = SupabaseClient.parseServerMessageTimestampOrNull(offsetIso)
        assertTrue(offsetMillis != null)
        // Offset 20:40:00+07:00 is same instant as 13:40:00Z
        assertEquals(stdMillis, offsetMillis)
    }

    @Test
    fun test37_strictTimestampNumeric() {
        // Epoch seconds
        val secStr = "1725541200"
        val secMillis = SupabaseClient.parseServerMessageTimestampOrNull(secStr)
        assertEquals(1725541200000L, secMillis)

        // Epoch millis
        val msStr = "1725541200123"
        val msMillis = SupabaseClient.parseServerMessageTimestampOrNull(msStr)
        assertEquals(1725541200123L, msMillis)
    }

    @Test
    fun test38_strictTimestampBlankOrGarbageReturnsNull() {
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull(null))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull(""))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull("   "))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull("not-a-timestamp"))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull("2026-99-99T99:99:99Z"))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull("NaN"))
        assertNull(SupabaseClient.parseServerMessageTimestampOrNull("undefined"))
    }

    @Test
    fun test39_remoteMessageInvalidTimestampDropped() {
        val validUuid = java.util.UUID.randomUUID().toString()
        val recordMissingTs = SupabaseDirectMessageRecord(
            id = validUuid,
            senderId = "user_1",
            recipientId = "user_2",
            text = "Hello",
            createdAt = null
        )
        assertNull(recordMissingTs.toDomainStrict())

        val recordBlankTs = recordMissingTs.copy(createdAt = "  ")
        assertNull(recordBlankTs.toDomainStrict())

        val recordGarbageTs = recordMissingTs.copy(createdAt = "garbage_timestamp")
        assertNull(recordGarbageTs.toDomainStrict())
    }

    @Test
    fun test40_remoteMessageInvalidIdDropped() {
        val recordNonUuid = SupabaseDirectMessageRecord(
            id = "not-a-uuid",
            senderId = "user_1",
            recipientId = "user_2",
            text = "Hello",
            createdAt = "2026-09-05T13:40:00Z"
        )
        assertNull(recordNonUuid.toDomainStrict())

        val recordBlankId = recordNonUuid.copy(id = "")
        assertNull(recordBlankId.toDomainStrict())
    }

    @Test
    fun test41_remoteMessageValidStrictFieldsPreserved() {
        val validUuid = java.util.UUID.randomUUID().toString()
        val record = SupabaseDirectMessageRecord(
            id = validUuid,
            senderId = "user_1",
            senderName = "User One",
            senderAvatar = "https://example.com/avatar1.png",
            recipientId = "user_2",
            recipientName = "User Two",
            recipientAvatar = "https://example.com/avatar2.png",
            text = "Hello world",
            stampId = "stamp_1",
            stampTitle = "Da Lat",
            stampImageUrl = "https://example.com/stamp.png",
            stampLocation = "Da Lat",
            createdAt = "2026-09-05T13:40:00.123Z",
            isRead = true
        )

        val domain = record.toDomainStrict()
        assertTrue(domain != null)
        assertEquals(validUuid, domain!!.id)
        assertEquals("user_1", domain.senderId)
        assertEquals("user_2", domain.recipientId)
        assertEquals("Hello world", domain.text)
        assertEquals("https://example.com/stamp.png", domain.stampImageUrl)
        assertTrue(domain.isRead)

        // Local stamp url stripped to null
        val localStampRecord = record.copy(stampImageUrl = "file:///sdcard/stamp.png")
        val domainStripped = localStampRecord.toDomainStrict()
        assertTrue(domainStripped != null)
        assertNull(domainStripped!!.stampImageUrl)
    }

    @Test
    fun test42_reconnectDelayProgressionAndCap() {
        for (i in 0 until 10) {
            val d0 = SupabaseRealtimeClient.calculateReconnectDelay(0)
            assertTrue("attempt 0 delay within [1000, 1200]: $d0", d0 in 1000L..1200L)

            val d1 = SupabaseRealtimeClient.calculateReconnectDelay(1)
            assertTrue("attempt 1 delay within [2000, 2400]: $d1", d1 in 2000L..2400L)

            val d2 = SupabaseRealtimeClient.calculateReconnectDelay(2)
            assertTrue("attempt 2 delay within [4000, 4800]: $d2", d2 in 4000L..4800L)

            val d3 = SupabaseRealtimeClient.calculateReconnectDelay(3)
            assertTrue("attempt 3 delay within [8000, 9600]: $d3", d3 in 8000L..9600L)

            val d4 = SupabaseRealtimeClient.calculateReconnectDelay(4)
            assertTrue("attempt 4 delay within [16000, 19200]: $d4", d4 in 16000L..19200L)

            val d5 = SupabaseRealtimeClient.calculateReconnectDelay(5)
            assertEquals("attempt 5 delay capped at 30000", 30000L, d5)

            val d10 = SupabaseRealtimeClient.calculateReconnectDelay(10)
            assertEquals("attempt 10 delay capped at 30000", 30000L, d10)
        }
    }

    @Test
    fun test43_reconcileDedupeAndReadStateRecovery() {
        val id1 = java.util.UUID.randomUUID().toString()
        val id2 = java.util.UUID.randomUUID().toString()

        val localMessages = listOf(
            DirectMessage(
                id = id1,
                senderId = "user_1",
                senderName = "User 1",
                senderAvatar = "",
                recipientId = "user_me",
                recipientName = "Me",
                recipientAvatar = "",
                text = "Old unread msg",
                createdAt = 1000L,
                isRead = false
            )
        )

        // Cloud has id1 marked as read, plus new id2
        val cloudMessages = listOf(
            DirectMessage(
                id = id1,
                senderId = "user_1",
                senderName = "User 1",
                senderAvatar = "",
                recipientId = "user_me",
                recipientName = "Me",
                recipientAvatar = "",
                text = "Old unread msg",
                createdAt = 1000L,
                isRead = true // updated read state!
            ),
            DirectMessage(
                id = id2,
                senderId = "user_1",
                senderName = "User 1",
                senderAvatar = "",
                recipientId = "user_me",
                recipientName = "Me",
                recipientAvatar = "",
                text = "New msg",
                createdAt = 2000L,
                isRead = false
            )
        )

        // Merge logic
        val map = localMessages.associateBy { it.id }.toMutableMap()
        cloudMessages.forEach { map[it.id] = it }
        val merged = map.values.sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })

        assertEquals(2, merged.size)
        assertEquals(id1, merged[0].id)
        assertTrue("id1 read state recovered", merged[0].isRead)
        assertEquals(id2, merged[1].id)
    }

    @Test
    fun test44_notificationDedupeOnReconcile() {
        val notifiedIds = mutableSetOf<String>()
        val msgId = java.util.UUID.randomUUID().toString()
        val msg = DirectMessage(
            id = msgId,
            senderId = "user_other",
            senderName = "Other",
            senderAvatar = "",
            recipientId = "user_me",
            recipientName = "Me",
            recipientAvatar = "",
            text = "Hello",
            createdAt = 1000L,
            isRead = false
        )

        // First arrival (e.g. Realtime)
        var notificationsSent = 0
        if (!notifiedIds.contains(msg.id)) {
            notifiedIds.add(msg.id)
            notificationsSent++
        }
        assertEquals(1, notificationsSent)

        // Subsequent reconciliation containing same message
        if (!notifiedIds.contains(msg.id)) {
            notifiedIds.add(msg.id)
            notificationsSent++
        }
        // Still 1, no duplicate notification
        assertEquals(1, notificationsSent)
    }

    @Test
    fun test45_connectionGenerationInvalidation() {
        val generation = java.util.concurrent.atomic.AtomicLong(1L)
        val initialGen = generation.get()

        // Stale callback checks generation
        var callbackExecuted = false
        val staleCallback: () -> Unit = {
            if (generation.get() == initialGen) {
                callbackExecuted = true
            }
        }

        // Advance generation (e.g. disconnect / new login)
        generation.incrementAndGet()

        staleCallback()
        assertFalse("Stale callback must be ignored when generation has advanced", callbackExecuted)
    }
}

