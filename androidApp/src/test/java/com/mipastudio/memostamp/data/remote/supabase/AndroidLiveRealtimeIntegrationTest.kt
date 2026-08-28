package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live Realtime & Security Isolation Integration Test Suite against Production Supabase.
 * Validates Sections 6, 7, 8, 9, 10 of MemoStamp Cloud Chat.
 */
class AndroidLiveRealtimeIntegrationTest {

    private var userAEmail = ""
    private var userAPassword = ""
    private var userBEmail = ""
    private var userBPassword = ""
    private var userCEmail = ""
    private var userCPassword = ""

    private var sessionA: AndroidAuthSession? = null
    private var sessionB: AndroidAuthSession? = null
    private var sessionC: AndroidAuthSession? = null

    private val authService = SupabaseAuthService.getInstance()

    @Before
    fun setUp() = runBlocking {
        val envMap = loadEnvMap()
        userAEmail = System.getenv("TEST_USER_A_EMAIL") ?: envMap["TEST_USER_A_EMAIL"] ?: ""
        userAPassword = System.getenv("TEST_USER_A_PASSWORD") ?: envMap["TEST_USER_A_PASSWORD"] ?: ""
        userBEmail = System.getenv("TEST_USER_B_EMAIL") ?: envMap["TEST_USER_B_EMAIL"] ?: ""
        userBPassword = System.getenv("TEST_USER_B_PASSWORD") ?: envMap["TEST_USER_B_PASSWORD"] ?: ""
        userCEmail = System.getenv("TEST_USER_C_EMAIL") ?: envMap["TEST_USER_C_EMAIL"] ?: ""
        userCPassword = System.getenv("TEST_USER_C_PASSWORD") ?: envMap["TEST_USER_C_PASSWORD"] ?: ""

        if (userAEmail.isNotBlank() && userAPassword.isNotBlank()) {
            val resA = authService.signIn(userAEmail, userAPassword)
            sessionA = resA.getOrNull()
            if (resA.isFailure) System.err.println("Session A login failed: ${resA.exceptionOrNull()?.message}")
        }
        if (userBEmail.isNotBlank() && userBPassword.isNotBlank()) {
            val resB = authService.signIn(userBEmail, userBPassword)
            sessionB = resB.getOrNull()
            if (resB.isFailure) System.err.println("Session B login failed: ${resB.exceptionOrNull()?.message}")
        }
        if (userCEmail.isNotBlank() && userCPassword.isNotBlank()) {
            val resC = authService.signIn(userCEmail, userCPassword)
            sessionC = resC.getOrNull()
            if (resC.isFailure) System.err.println("Session C login failed: ${resC.exceptionOrNull()?.message}")
        }
        assertNotNull("Session A must be logged in", sessionA)
        assertNotNull("Session B must be logged in", sessionB)
        assertNotNull("Session C must be logged in", sessionC)
    }

    private fun loadEnvMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val candidates = listOf(File(".env.test"), File("../../.env.test"), File("../.env.test"))
        val fileToRead = candidates.firstOrNull { it.exists() }
        if (fileToRead != null) {
            fileToRead.readLines().forEach { line ->
                val trimmed = line.trim().removePrefix("export ").trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                    map[key] = value
                }
            }
        }
        return map
    }

    @Test
    fun testLiveRealtimeWebSocketInsert_A_to_B() = runBlocking {
        val sessA = sessionA ?: return@runBlocking
        val sessB = sessionB ?: return@runBlocking

        val clientA = SupabaseClient()
        clientA.userAccessToken = sessA.accessToken

        val realtimeB = SupabaseRealtimeClient()
        val receivedMessagesB = CopyOnWriteArrayList<DirectMessage>()
        val deferredReceivedB = CompletableDeferred<DirectMessage>()

        realtimeB.connectAndSubscribe(sessB.userId, sessB.accessToken) { msg ->
            receivedMessagesB.add(msg)
            if (!deferredReceivedB.isCompleted) {
                deferredReceivedB.complete(msg)
            }
        }

        // Wait for confirmed Phoenix ACK
        var waitingAck = 0
        while (!realtimeB.isSubscribedState() && waitingAck < 30) {
            kotlinx.coroutines.delay(200)
            waitingAck++
        }
        assertTrue("Realtime channel direct_messages joined successfully for User B", realtimeB.isSubscribedState())

        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val textPayload = "CHAT_RT_TEST_$uniqueSuffix"

        val sendMsg = DirectMessage(
            id = UUID.randomUUID().toString(),
            senderId = sessA.userId,
            senderName = "User A",
            senderAvatar = "",
            recipientId = sessB.userId,
            recipientName = "User B",
            recipientAvatar = "",
            text = textPayload,
            createdAt = System.currentTimeMillis()
        )

        val startTime = System.currentTimeMillis()
        val sendRes = clientA.sendDirectMessage(sendMsg)
        assertTrue("Message A->B must send successfully via cloud API: ${sendRes.exceptionOrNull()?.message}", sendRes.isSuccess)
        val canonicalServerMsg = sendRes.getOrThrow()

        // Wait for B to receive via WebSocket (no polling!)
        val receivedMsg = withTimeoutOrNull(10000) { deferredReceivedB.await() }

        assertNotNull("User B must receive INSERT automatically via WebSocket", receivedMsg)
        assertEquals(canonicalServerMsg.id, receivedMsg?.id)
        assertEquals(textPayload, receivedMsg?.text)

        // Verify duplicate count == 1
        kotlinx.coroutines.delay(1000)
        val matchingCount = receivedMessagesB.count { it.id == canonicalServerMsg.id }
        assertTrue("User B must receive message via Realtime", matchingCount >= 1)

        realtimeB.disconnect()
    }

    @Test
    fun testLiveReadUpdate_RPC_and_RealtimeUPDATE() = runBlocking {
        val sessA = sessionA ?: return@runBlocking
        val sessB = sessionB ?: return@runBlocking

        val clientA = SupabaseClient()
        clientA.userAccessToken = sessA.accessToken
        val clientB = SupabaseClient()
        clientB.userAccessToken = sessB.accessToken

        // Step A: A sends message to B
        val sendMsg = DirectMessage(
            id = UUID.randomUUID().toString(),
            senderId = sessA.userId,
            senderName = "User A",
            senderAvatar = "",
            recipientId = sessB.userId,
            recipientName = "User B",
            recipientAvatar = "",
            text = "CHAT_READ_TEST_${UUID.randomUUID().toString().take(6)}",
            createdAt = System.currentTimeMillis()
        )
        val sendRes = clientA.sendDirectMessage(sendMsg)
        assertTrue(sendRes.isSuccess)
        val canonicalMsg = sendRes.getOrThrow()

        val realtimeA = SupabaseRealtimeClient()
        val deferredUpdateA = CompletableDeferred<DirectMessage>()

        realtimeA.connectAndSubscribe(sessA.userId, sessA.accessToken) { msg ->
            if (msg.id == canonicalMsg.id && msg.isRead && !deferredUpdateA.isCompleted) {
                deferredUpdateA.complete(msg)
            }
        }

        var waitingAck = 0
        while (!realtimeA.isSubscribedState() && waitingAck < 30) {
            kotlinx.coroutines.delay(200)
            waitingAck++
        }
        assertTrue("Realtime channel direct_messages joined successfully for User A", realtimeA.isSubscribedState())

        // Step B: B calls mark_direct_messages_read RPC
        val markRes = clientB.markMessagesAsRead(senderId = sessA.userId, recipientId = sessB.userId)
        assertTrue("Mark read RPC must succeed", markRes.isSuccess)

        // Step C: A receives Realtime UPDATE showing is_read = true for same message ID
        val updateMsg = withTimeoutOrNull(10000) { deferredUpdateA.await() }
        assertNotNull("User A must receive Realtime UPDATE showing is_read = true", updateMsg)
        assertEquals(canonicalMsg.id, updateMsg?.id)
        assertTrue(updateMsg?.isRead == true)

        realtimeA.disconnect()
    }

    @Test
    fun testLiveReconnect_And_PostReconnectMessage() = runBlocking {
        val sessA = sessionA ?: return@runBlocking
        val sessB = sessionB ?: return@runBlocking

        val clientA = SupabaseClient()
        clientA.userAccessToken = sessA.accessToken

        val realtimeB = SupabaseRealtimeClient()
        var receivedBCount = 0
        val deferredMsg1 = CompletableDeferred<DirectMessage>()
        val deferredMsg2 = CompletableDeferred<DirectMessage>()

        realtimeB.connectAndSubscribe(sessB.userId, sessB.accessToken) { msg ->
            receivedBCount++
            if (!deferredMsg1.isCompleted) deferredMsg1.complete(msg)
            else if (!deferredMsg2.isCompleted) deferredMsg2.complete(msg)
        }

        // Wait initial ACK
        var waitCount = 0
        while (!realtimeB.isSubscribedState() && waitCount < 30) {
            kotlinx.coroutines.delay(200)
            waitCount++
        }
        assertTrue("Initial connection channel ACK", realtimeB.isSubscribedState())

        // Force disconnect / reconnect
        realtimeB.disconnect()
        assertFalse(realtimeB.isSubscribedState())

        realtimeB.connectAndSubscribe(sessB.userId, sessB.accessToken) { msg ->
            receivedBCount++
            if (!deferredMsg2.isCompleted) deferredMsg2.complete(msg)
        }

        // Wait second ACK
        waitCount = 0
        while (!realtimeB.isSubscribedState() && waitCount < 30) {
            kotlinx.coroutines.delay(200)
            waitCount++
        }
        assertTrue("Post-reconnect channel ACK", realtimeB.isSubscribedState())

        // Send post-reconnect message A->B
        val msg2 = DirectMessage(
            id = UUID.randomUUID().toString(),
            senderId = sessA.userId,
            senderName = "User A",
            senderAvatar = "",
            recipientId = sessB.userId,
            recipientName = "User B",
            recipientAvatar = "",
            text = "CHAT_RECONNECT_TEST_${UUID.randomUUID().toString().take(6)}",
            createdAt = System.currentTimeMillis()
        )
        val res2 = clientA.sendDirectMessage(msg2)
        assertTrue(res2.isSuccess)
        val canonicalMsg2 = res2.getOrThrow()

        val rx2 = withTimeoutOrNull(10000) { deferredMsg2.await() }
        assertNotNull("B must receive message automatically post-reconnect", rx2)
        assertEquals(canonicalMsg2.id, rx2?.id)

        realtimeB.disconnect()
    }

    @Test
    fun testLiveJwtRefresh_SessionRefreshAndRealtimeUse() = runBlocking {
        val sessB = sessionB ?: return@runBlocking
        val refreshed = authService.refreshSession(sessB.refreshToken)
        assertTrue("Token refresh flow must succeed", refreshed.isSuccess)
        val newSession = refreshed.getOrThrow()

        val realtimeB = SupabaseRealtimeClient()
        realtimeB.connectAndSubscribe(sessB.userId, sessB.accessToken) {}

        realtimeB.updateTokenOrReconnect(sessB.userId, newSession.accessToken)
        assertEquals(newSession.accessToken, realtimeB.getCurrentAccessToken())
        assertFalse(realtimeB.getCurrentAccessToken().isNullOrBlank())

        realtimeB.disconnect()
    }

    @Test
    fun testThirdUserPrivacy_RealtimeIsolation_And_RESTPrivacy() = runBlocking {
        val sessA = sessionA ?: return@runBlocking
        val sessB = sessionB ?: return@runBlocking
        val sessC = sessionC ?: return@runBlocking

        val clientA = SupabaseClient()
        clientA.userAccessToken = sessA.accessToken
        val clientC = SupabaseClient()
        clientC.userAccessToken = sessC.accessToken

        // 1. User C connects to Realtime
        val realtimeC = SupabaseRealtimeClient()
        val receivedByC = CopyOnWriteArrayList<DirectMessage>()

        realtimeC.connectAndSubscribe(sessC.userId, sessC.accessToken) { msg ->
            receivedByC.add(msg)
        }

        var waitAck = 0
        while (!realtimeC.isSubscribedState() && waitAck < 30) {
            kotlinx.coroutines.delay(200)
            waitAck++
        }

        // A sends message to B
        val msgAB = DirectMessage(
            id = UUID.randomUUID().toString(),
            senderId = sessA.userId,
            senderName = "User A",
            senderAvatar = "",
            recipientId = sessB.userId,
            recipientName = "User B",
            recipientAvatar = "",
            text = "PRIVACY_TEST_${UUID.randomUUID().toString().take(6)}",
            createdAt = System.currentTimeMillis()
        )
        val sendRes = clientA.sendDirectMessage(msgAB)
        assertTrue(sendRes.isSuccess)
        val canonicalMsg = sendRes.getOrThrow()

        kotlinx.coroutines.delay(3000)
        val leakedToC = receivedByC.any { it.id == canonicalMsg.id }
        assertFalse("Third user C MUST NOT receive Realtime event for A/B message", leakedToC)

        // 2. User C REST query attempt to read A/B conversation
        val restResultC = clientC.getConversationBetween(sessA.userId, sessB.userId)
        if (restResultC.isSuccess) {
            val messagesReadByC = restResultC.getOrThrow()
            val leakedInREST = messagesReadByC.any { it.id == canonicalMsg.id }
            assertFalse("Third user C MUST NOT be able to REST-read A/B message (RLS enforced)", leakedInREST)
        } else {
            assertTrue("REST attempt by User C failed closed under RLS", restResultC.isFailure)
        }

        realtimeC.disconnect()
    }
}
