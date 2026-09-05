package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Real Supabase Realtime WebSocket client establishing channel subscriptions for direct_messages.
 * Implements Phoenix V1/V2 protocol over WebSocket with connection epoch and exponential backoff.
 */
class SupabaseRealtimeClient(private val context: Context? = null) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Keep-alive WebSocket
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var joinWatchdogJob: Job? = null

    // Generation epoch to isolate stale callbacks and socket closures
    private val connectionGeneration = AtomicLong(0)
    private var reconnectAttempt = 0

    @Volatile
    private var isConnected = false

    @Volatile
    private var isSubscribed = false

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var currentAccessToken: String? = null

    private var onMessageCallback: ((DirectMessage) -> Unit)? = null
    var onSubscriptionReady: ((String) -> Unit)? = null

    private val refCounter = AtomicInteger(1)

    fun getCurrentUserId(): String? = currentUserId
    fun getCurrentAccessToken(): String? = currentAccessToken
    fun isSubscribedState(): Boolean = isSubscribed
    fun isConnectedState(): Boolean = isConnected
    fun getConnectionGeneration(): Long = connectionGeneration.get()

    companion object {
        private const val TAG = "SupabaseRealtime"

        @Volatile
        private var instance: SupabaseRealtimeClient? = null

        fun getInstance(context: Context): SupabaseRealtimeClient {
            val safeContext = try { context.applicationContext } catch (_: Throwable) { context }
            return instance ?: synchronized(this) {
                instance ?: SupabaseRealtimeClient(safeContext).also { instance = it }
            }
        }

        fun calculateReconnectDelay(attempt: Int): Long {
            val baseDelays = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)
            val base = if (attempt < baseDelays.size) baseDelays[attempt] else 30000L
            val jitter = (Math.random() * 0.20 * base).toLong()
            return (base + jitter).coerceAtMost(30000L)
        }
    }

    private fun getWsUrl(): String {
        val httpUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
        val apiKey = SupabaseConfig.getAnonKey(context).trim()
        val baseWs = httpUrl.replace("https://", "wss://").replace("http://", "ws://")
        return "$baseWs/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
    }

    @Synchronized
    fun updateTokenOrReconnect(userId: String, newAccessToken: String?) {
        if (userId.isBlank() || userId.startsWith("guest_") || newAccessToken.isNullOrBlank()) {
            disconnect()
            return
        }
        if (currentUserId != userId || currentAccessToken != newAccessToken || !isConnected || !isSubscribed) {
            connectAndSubscribe(userId, newAccessToken, onMessageCallback ?: {})
        }
    }

    @Synchronized
    fun connectAndSubscribe(
        userId: String,
        accessToken: String?,
        onMessageReceived: (DirectMessage) -> Unit
    ) {
        if (userId.isBlank() || userId.startsWith("guest_") || accessToken.isNullOrBlank()) {
            Log.w(TAG, "Cannot subscribe to Realtime: valid user authentication token required")
            disconnect()
            return
        }

        this.onMessageCallback = onMessageReceived

        // Duplicate subscription & token check
        if (currentUserId == userId && currentAccessToken == accessToken && isConnected && isSubscribed) {
            Log.d(TAG, "Already connected and subscribed for user: $userId")
            return
        }

        disconnectInternal(clearState = false)

        val gen = connectionGeneration.incrementAndGet()
        this.currentUserId = userId
        this.currentAccessToken = accessToken

        startWebSocket(gen)
    }

    private fun startWebSocket(generation: Long) {
        if (connectionGeneration.get() != generation) return

        val url = getWsUrl()
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (connectionGeneration.get() != generation) {
                    ws.close(1000, "Stale generation")
                    return
                }
                Log.i(TAG, "WebSocket connected successfully (generation=$generation)")
                isConnected = true
                startHeartbeat(generation)
                subscribeToDirectMessages(generation)
                startJoinWatchdog(generation)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (connectionGeneration.get() != generation) return
                handleIncomingMessage(text, generation)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (connectionGeneration.get() != generation) return
                Log.w(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
                isSubscribed = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (connectionGeneration.get() != generation) return
                Log.w(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isSubscribed = false
                scheduleReconnect(generation)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (connectionGeneration.get() != generation) return
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                isSubscribed = false
                scheduleReconnect(generation)
            }
        })
    }

    private fun subscribeToDirectMessages(generation: Long) {
        if (connectionGeneration.get() != generation) return
        val uid = currentUserId ?: return
        val token = currentAccessToken
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Cannot subscribe to direct_messages: missing JWT access token")
            return
        }
        val joinRef = refCounter.getAndIncrement().toString()
        val topic = "realtime:public:direct_messages"

        val payloadMap = mapOf(
            "config" to mapOf(
                "postgres_changes" to listOf(
                    mapOf(
                        "event" to "*",
                        "schema" to "public",
                        "table" to "direct_messages"
                    )
                )
            ),
            "access_token" to token
        )

        val joinMsg = mapOf(
            "topic" to topic,
            "event" to "phx_join",
            "payload" to payloadMap,
            "ref" to joinRef
        )

        Log.d(TAG, "Subscribing to direct_messages topic=$topic for user=$uid (gen=$generation)")
        val sent = webSocket?.send(gson.toJson(joinMsg)) ?: false
        if (!sent && connectionGeneration.get() == generation) {
            scheduleReconnect(generation)
        }
    }

    private fun startJoinWatchdog(generation: Long) {
        joinWatchdogJob?.cancel()
        joinWatchdogJob = coroutineScope.launch {
            delay(10000)
            if (connectionGeneration.get() == generation && !isSubscribed) {
                Log.w(TAG, "Join ACK watchdog timed out after 10s for generation $generation")
                scheduleReconnect(generation)
            }
        }
    }

    private fun startHeartbeat(generation: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch {
            while (isActive && isConnected && connectionGeneration.get() == generation) {
                delay(25000)
                if (isConnected && connectionGeneration.get() == generation) {
                    val pingRef = refCounter.getAndIncrement().toString()
                    val ping = mapOf(
                        "topic" to "phoenix",
                        "event" to "heartbeat",
                        "payload" to emptyMap<String, Any>(),
                        "ref" to pingRef
                    )
                    val sent = webSocket?.send(gson.toJson(ping)) ?: false
                    if (!sent && connectionGeneration.get() == generation) {
                        Log.w(TAG, "Heartbeat failed to send. Triggering reconnect.")
                        scheduleReconnect(generation)
                        break
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(jsonText: String, generation: Long) {
        if (connectionGeneration.get() != generation) return

        try {
            val root = gson.fromJson(jsonText, Map::class.java) as? Map<*, *> ?: return
            val topic = (root["topic"] as? String) ?: ""
            val event = root["event"] as? String ?: return
            val payload = root["payload"] as? Map<*, *> ?: return

            // Handle Phoenix Join Acknowledgments
            if (event == "phx_reply") {
                val status = payload["status"] as? String
                if (status == "ok") {
                    if (topic == "realtime:public:direct_messages" || topic.contains("direct_messages")) {
                        joinWatchdogJob?.cancel()
                        isSubscribed = true
                        reconnectAttempt = 0
                        Log.i(TAG, "Realtime channel direct_messages joined successfully (ACK received)")
                        val uid = currentUserId
                        if (uid != null) {
                            onSubscriptionReady?.invoke(uid)
                        }
                    }
                } else if (status == "error") {
                    if (topic == "realtime:public:direct_messages" || topic.contains("direct_messages")) {
                        joinWatchdogJob?.cancel()
                        isSubscribed = false
                        Log.e(TAG, "Realtime channel direct_messages join failed: ${payload["response"]}")
                        scheduleReconnect(generation)
                    }
                }
                return
            }

            if (event == "postgres_changes" || event == "INSERT" || event == "UPDATE") {
                val recordMap = extractRecord(payload) ?: return
                val directMsg = parseDirectMessage(recordMap) ?: return

                // User lifecycle check: only accept messages relevant to current logged in user
                val activeUid = currentUserId
                if (activeUid != null && (directMsg.senderId == activeUid || directMsg.recipientId == activeUid)) {
                    Log.d(TAG, "Realtime direct_message received ($event): ${directMsg.id}")
                    onMessageCallback?.invoke(directMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming WebSocket message: ${e.message}")
        }
    }

    private fun extractRecord(payload: Map<*, *>): Map<*, *>? {
        val data = payload["data"] as? Map<*, *>
        if (data != null) {
            val record = data["record"] as? Map<*, *>
            if (record != null) return record
        }
        val recordDirect = payload["record"] as? Map<*, *>
        if (recordDirect != null) return recordDirect

        val newRecord = payload["new"] as? Map<*, *>
        if (newRecord != null) return newRecord

        return payload
    }

    private fun parseDirectMessage(map: Map<*, *>): DirectMessage? {
        val id = (map["id"] ?: "").toString()
        val senderId = (map["sender_id"] ?: "").toString()
        val recipientId = (map["recipient_id"] ?: "").toString()
        if (id.isBlank() || !SupabaseClient.isValidUuid(id) || senderId.isBlank() || recipientId.isBlank()) return null

        val senderName = (map["sender_name"] ?: "").toString()
        val senderAvatar = (map["sender_avatar"] ?: "https://i.pravatar.cc/150?u=$senderId").toString()
        val recipientName = (map["recipient_name"] ?: "").toString()
        val recipientAvatar = (map["recipient_avatar"] ?: "https://i.pravatar.cc/150?u=$recipientId").toString()
        val text = (map["text"] ?: "").toString()
        val stampId = map["stamp_id"]?.toString()
        val stampTitle = map["stamp_title"]?.toString()
        val rawStampUrl = map["stamp_image_url"]?.toString()
        val stampImageUrl = if (com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(rawStampUrl)) rawStampUrl?.trim() else null
        val stampLocation = map["stamp_location"]?.toString()
        val isRead = (map["is_read"] as? Boolean) ?: false

        val rawCreatedAt = (map["created_at"] ?: map["createdAt"])?.toString()
        val createdAt = SupabaseClient.parseServerMessageTimestampOrNull(rawCreatedAt) ?: return null

        return DirectMessage(
            id = id,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            recipientId = recipientId,
            recipientName = recipientName,
            recipientAvatar = recipientAvatar,
            text = text,
            stampId = stampId,
            stampTitle = stampTitle,
            stampImageUrl = stampImageUrl,
            stampLocation = stampLocation,
            createdAt = createdAt,
            isRead = isRead
        )
    }

    private fun scheduleReconnect(generation: Long) {
        if (connectionGeneration.get() != generation) return
        val uid = currentUserId
        val token = currentAccessToken
        if (uid.isNullOrBlank() || uid.startsWith("guest_") || token.isNullOrBlank()) return

        joinWatchdogJob?.cancel()
        heartbeatJob?.cancel()

        try {
            webSocket?.close(1000, "Reconnect")
        } catch (_: Throwable) {}
        webSocket = null
        isConnected = false
        isSubscribed = false

        reconnectJob?.cancel()
        val delayMs = calculateReconnectDelay(reconnectAttempt)
        reconnectAttempt++

        reconnectJob = coroutineScope.launch {
            delay(delayMs)
            if (connectionGeneration.get() == generation && !isConnected && !currentUserId.isNullOrBlank()) {
                Log.i(TAG, "Reconnecting WebSocket for user: $currentUserId (gen=$generation, attempt=$reconnectAttempt)")
                startWebSocket(generation)
            }
        }
    }

    fun disconnect() {
        disconnectInternal(clearState = true)
    }

    private fun disconnectInternal(clearState: Boolean) {
        connectionGeneration.incrementAndGet()

        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        joinWatchdogJob?.cancel()
        joinWatchdogJob = null
        reconnectAttempt = 0

        if (isSubscribed && isConnected) {
            try {
                val leaveRef = refCounter.getAndIncrement().toString()
                val leaveMsg = mapOf(
                    "topic" to "realtime:public:direct_messages",
                    "event" to "phx_leave",
                    "payload" to emptyMap<String, Any>(),
                    "ref" to leaveRef
                )
                webSocket?.send(gson.toJson(leaveMsg))
            } catch (_: Exception) {}
        }

        try {
            webSocket?.close(1000, "Logout")
        } catch (_: Throwable) {}
        webSocket = null
        isConnected = false
        isSubscribed = false

        if (clearState) {
            currentUserId = null
            currentAccessToken = null
            onMessageCallback = null
        }
    }
}
