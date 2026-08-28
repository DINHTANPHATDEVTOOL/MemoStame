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

/**
 * Real Supabase Realtime WebSocket client establishing channel subscriptions for direct_messages.
 * Implements Phoenix V1/V2 protocol over WebSocket.
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

    @Volatile
    private var isConnected = false

    @Volatile
    private var isSubscribed = false

    @Volatile
    private var currentUserId: String? = null

    @Volatile
    private var currentAccessToken: String? = null

    private var onMessageCallback: ((DirectMessage) -> Unit)? = null
    private val refCounter = AtomicInteger(1)

    fun getCurrentUserId(): String? = currentUserId
    fun getCurrentAccessToken(): String? = currentAccessToken
    fun isSubscribedState(): Boolean = isSubscribed
    fun isConnectedState(): Boolean = isConnected

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
        if (currentUserId != userId) {
            connectAndSubscribe(userId, newAccessToken, onMessageCallback ?: {})
            return
        }
        if (currentAccessToken != newAccessToken) {
            Log.d(TAG, "Access token refreshed for user: $userId")
            this.currentAccessToken = newAccessToken
            if (isConnected) {
                subscribeToDirectMessages()
            } else {
                startWebSocket()
            }
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

        if (currentUserId == userId && isConnected && currentAccessToken != accessToken) {
            Log.d(TAG, "Updating access token for active user: $userId")
            this.currentAccessToken = accessToken
            subscribeToDirectMessages()
            return
        }

        disconnectInternal(clearState = false)

        this.currentUserId = userId
        this.currentAccessToken = accessToken

        startWebSocket()
    }

    private fun startWebSocket() {
        val url = getWsUrl()
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to Supabase Realtime")
                isConnected = true
                startHeartbeat()
                subscribeToDirectMessages()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
                isSubscribed = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isSubscribed = false
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                isSubscribed = false
                scheduleReconnect()
            }
        })
    }

    private fun subscribeToDirectMessages() {
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

        Log.d(TAG, "Subscribing to direct_messages topic=$topic for user=$uid")
        webSocket?.send(gson.toJson(joinMsg))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch {
            while (isActive && isConnected) {
                delay(25000)
                if (isConnected) {
                    val pingRef = refCounter.getAndIncrement().toString()
                    val ping = mapOf(
                        "topic" to "phoenix",
                        "event" to "heartbeat",
                        "payload" to emptyMap<String, Any>(),
                        "ref" to pingRef
                    )
                    webSocket?.send(gson.toJson(ping))
                }
            }
        }
    }

    private fun handleIncomingMessage(jsonText: String) {
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
                        isSubscribed = true
                        Log.i(TAG, "Realtime channel direct_messages joined successfully (ACK received)")
                    }
                } else if (status == "error") {
                    isSubscribed = false
                    Log.e(TAG, "Realtime channel direct_messages join failed: ${payload["response"]}")
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
        if (id.isBlank() || senderId.isBlank() || recipientId.isBlank()) return null

        val senderName = (map["sender_name"] ?: "").toString()
        val senderAvatar = (map["sender_avatar"] ?: "https://i.pravatar.cc/150?u=$senderId").toString()
        val recipientName = (map["recipient_name"] ?: "").toString()
        val recipientAvatar = (map["recipient_avatar"] ?: "https://i.pravatar.cc/150?u=$recipientId").toString()
        val text = (map["text"] ?: "").toString()
        val stampId = map["stamp_id"]?.toString()
        val stampTitle = map["stamp_title"]?.toString()
        val stampImageUrl = map["stamp_image_url"]?.toString()
        val stampLocation = map["stamp_location"]?.toString()
        val isRead = (map["is_read"] as? Boolean) ?: false
        val createdAt = SupabaseClient.parseIsoStringToMillis((map["created_at"] ?: map["createdAt"] ?: System.currentTimeMillis()).toString())

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

    private fun scheduleReconnect() {
        if (currentUserId.isNullOrBlank() || currentUserId?.startsWith("guest_") == true) return
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch {
            delay(3000)
            if (!isConnected && !currentUserId.isNullOrBlank()) {
                Log.i(TAG, "Reconnecting WebSocket for user: $currentUserId")
                startWebSocket()
            }
        }
    }

    fun disconnect() {
        disconnectInternal(clearState = true)
    }

    private fun disconnectInternal(clearState: Boolean) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null

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

        webSocket?.close(1000, "Logout")
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
