package com.mipastudio.memostamp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.core.notification.InAppBanner
import com.mipastudio.memostamp.core.notification.InAppNotificationManager
import com.mipastudio.memostamp.core.notification.MemoStampNotificationManager
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import com.mipastudio.memostamp.data.remote.supabase.SupabaseRealtimeClient
import com.mipastudio.memostamp.domain.model.ChatConversation
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ChatRepository internal constructor(
    private val context: Context,
    val supabaseClient: SupabaseClient = SupabaseClient.getInstance(context),
    val authRepo: UserAuthRepository = UserAuthRepository.getInstance(context),
    val realtimeClient: SupabaseRealtimeClient = SupabaseRealtimeClient.getInstance(context)
) {

    private val prefs: SharedPreferences? = try { context.getSharedPreferences("memostamp_chat_prefs", Context.MODE_PRIVATE) } catch (_: Throwable) { null }
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val notifiedMsgIds = mutableSetOf<String>()

    @Volatile
    private var isReconcileInFlight = false
    @Volatile
    private var reconcileRequestedWhileBusy = false

    private val _messages = MutableStateFlow<List<DirectMessage>>(emptyList())
    val messages: StateFlow<List<DirectMessage>> = _messages.asStateFlow()

    private var activeUserId: String? = null

    // The user ID currently open in ChatScreen (to avoid redundant banners while looking at that chat)
    var activeChattingUserId: String? = null

    init {
        coroutineScope.launch {
            authRepo.currentUser.collect { user ->
                val newUid = user.userId
                if (newUid != activeUserId) {
                    if (newUid.isBlank() || newUid.startsWith("guest_")) {
                        onLogout()
                    } else {
                        onUserChanged(newUid)
                    }
                }
            }
        }
        coroutineScope.launch {
            authRepo.accessToken.collect { token ->
                val currentUid = activeUserId
                if (!currentUid.isNullOrBlank() && !currentUid.startsWith("guest_")) {
                    if (token.isNullOrBlank()) {
                        onLogout()
                    } else {
                        realtimeClient.updateTokenOrReconnect(currentUid, token)
                    }
                }
            }
        }
        realtimeClient.onSubscriptionReady = { uid ->
            if (activeUserId == uid) {
                reconcileMessagesFromCloud(trigger = "REALTIME_ACK")
            }
        }
    }

    private fun getMessagesPrefKey(userId: String): String = "direct_messages_of_$userId"

    fun onUserChanged(userId: String) {
        activeUserId = userId
        val loaded = loadMessagesForUser(userId)
        _messages.value = loaded
        notifiedMsgIds.clear()
        notifiedMsgIds.addAll(loaded.map { it.id })

        val token = authRepo.accessToken.value ?: supabaseClient.userAccessToken
        realtimeClient.connectAndSubscribe(userId, token) { msg ->
            handleRealtimeEvent(msg)
        }

        reconcileMessagesFromCloud(trigger = "LOGIN")
    }

    fun onLogout() {
        realtimeClient.disconnect()
        activeUserId = null
        activeChattingUserId = null
        _messages.value = emptyList()
        notifiedMsgIds.clear()
    }

    fun onAppForeground() {
        val currentUid = activeUserId ?: authRepo.currentUser.value.userId
        if (currentUid.isBlank() || currentUid.startsWith("guest_") || !authRepo.isUserLoggedIn()) {
            return
        }
        val token = authRepo.accessToken.value ?: supabaseClient.userAccessToken
        if (!token.isNullOrBlank()) {
            realtimeClient.updateTokenOrReconnect(currentUid, token)
            reconcileMessagesFromCloud(trigger = "FOREGROUND")
        }
    }

    fun reconcileMessagesFromCloud(trigger: String = "MANUAL", onComplete: ((Boolean) -> Unit)? = null) {
        val currentUid = activeUserId ?: authRepo.currentUser.value.userId
        if (currentUid.isBlank() || currentUid.startsWith("guest_") || !authRepo.isUserLoggedIn()) {
            onComplete?.invoke(false)
            return
        }

        synchronized(this) {
            if (isReconcileInFlight) {
                reconcileRequestedWhileBusy = true
                return
            }
            isReconcileInFlight = true
        }

        coroutineScope.launch {
            try {
                val res = supabaseClient.getMessagesForUser(currentUid)
                if (res.isSuccess) {
                    val cloudMessages = res.getOrNull() ?: emptyList()
                    val localMap = _messages.value.associateBy { it.id }.toMutableMap()

                    val newIncomingMessages = cloudMessages.filter { msg ->
                        msg.recipientId == currentUid &&
                        msg.senderId != currentUid &&
                        !msg.isRead &&
                        !notifiedMsgIds.contains(msg.id)
                    }

                    cloudMessages.forEach { localMap[it.id] = it }
                    val merged = localMap.values.sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })
                    if (merged != _messages.value) {
                        saveMessagesForUser(currentUid, merged)
                    }

                    newIncomingMessages.forEach { msg ->
                        notifiedMsgIds.add(msg.id)
                        if (activeChattingUserId != msg.senderId) {
                            try {
                                MemoStampNotificationManager.sendNewMessageNotification(context, msg)

                                val bannerText = if (!msg.stampTitle.isNullOrBlank()) {
                                    if (msg.text.isNotBlank()) "📮 [Tem: ${msg.stampTitle}] ${msg.text}" else "📮 Đã gửi cho bạn một con tem: ${msg.stampTitle}"
                                } else {
                                    msg.text
                                }
                                InAppNotificationManager.show(
                                    InAppBanner(
                                        id = "msg_${msg.id}",
                                        title = "💬 Tin nhắn từ ${msg.senderName}",
                                        message = bannerText,
                                        avatarUrl = msg.senderAvatar,
                                        iconEmoji = "💬",
                                        targetRoute = "chat/${msg.senderId}",
                                        senderName = msg.senderName
                                    )
                                )
                            } catch (_: Throwable) {
                                // Ignore notification failures in test/headless context
                            }
                        }
                    }
                    onComplete?.invoke(true)
                } else {
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                onComplete?.invoke(false)
            } finally {
                var rerun = false
                synchronized(this@ChatRepository) {
                    isReconcileInFlight = false
                    if (reconcileRequestedWhileBusy) {
                        reconcileRequestedWhileBusy = false
                        rerun = true
                    }
                }
                if (rerun) {
                    reconcileMessagesFromCloud(trigger = "COALESCED_PENDING", onComplete = null)
                }
            }
        }
    }

    private fun loadMessagesForUser(userId: String): List<DirectMessage> {
        if (userId.isBlank() || userId.startsWith("guest_")) return emptyList()
        val json = prefs?.getString(getMessagesPrefKey(userId), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DirectMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMessagesForUser(userId: String, list: List<DirectMessage>) {
        if (userId.isBlank() || userId.startsWith("guest_")) return
        _messages.value = list
        prefs?.edit()?.putString(getMessagesPrefKey(userId), gson.toJson(list))?.apply()
    }

    fun clearAccountLocalData(userId: String) {
        if (userId.isBlank()) return
        prefs?.edit()?.remove(getMessagesPrefKey(userId))?.apply()
        if (activeUserId == userId) {
            _messages.value = emptyList()
            activeUserId = null
        }
    }

    suspend fun loadConversation(otherUserId: String): Result<List<DirectMessage>> = withContext(Dispatchers.IO) {
        val currentUid = authRepo.currentUser.value.userId
        val token = authRepo.accessToken.value ?: supabaseClient.userAccessToken

        if (currentUid.isBlank() || currentUid.startsWith("guest_") || !authRepo.isUserLoggedIn() || token.isNullOrBlank()) {
            return@withContext Result.failure(SecurityException("User authentication token required for RLS mutation"))
        }

        val res = supabaseClient.getConversationBetween(currentUid, otherUserId)
        if (res.isSuccess) {
            val cloudList = res.getOrNull() ?: emptyList()
            val localMap = _messages.value.associateBy { it.id }.toMutableMap()

            // Remove local server-derived messages between user pair that are no longer present in cloudList (authoritative sync)
            val currentPairMsgIds = _messages.value
                .filter { (it.senderId == currentUid && it.recipientId == otherUserId) || (it.senderId == otherUserId && it.recipientId == currentUid) }
                .map { it.id }
                .toSet()

            val cloudPairMsgIds = cloudList.map { it.id }.toSet()
            val removedIds = currentPairMsgIds - cloudPairMsgIds
            removedIds.forEach { localMap.remove(it) }

            cloudList.forEach { localMap[it.id] = it }

            val merged = localMap.values.sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })
            saveMessagesForUser(currentUid, merged)

            val conversation = getMessagesBetween(currentUid, otherUserId)
            Result.success(conversation)
        } else {
            // Cloud failed: retain offline cache, return failure
            Result.failure(res.exceptionOrNull() ?: Exception("Load conversation failed"))
        }
    }

    fun handleRealtimeEvent(incoming: DirectMessage) {
        val currentUid = authRepo.currentUser.value.userId
        // Ignore third-user messages
        if (incoming.senderId != currentUid && incoming.recipientId != currentUid) {
            return
        }
        val currentList = _messages.value
        val map = currentList.associateBy { it.id }.toMutableMap()
        map[incoming.id] = incoming
        val updated = map.values.sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })
        saveMessagesForUser(currentUid, updated)

        if (incoming.recipientId == currentUid && incoming.senderId != currentUid && !incoming.isRead && !notifiedMsgIds.contains(incoming.id)) {
            notifiedMsgIds.add(incoming.id)
            if (activeChattingUserId != incoming.senderId) {
                try {
                    MemoStampNotificationManager.sendNewMessageNotification(context, incoming)
                    InAppNotificationManager.show(
                        InAppBanner(
                            id = "msg_${incoming.id}",
                            title = "💬 Tin nhắn từ ${incoming.senderName}",
                            message = incoming.text,
                            avatarUrl = incoming.senderAvatar,
                            iconEmoji = "💬",
                            targetRoute = "chat/${incoming.senderId}",
                            senderName = incoming.senderName
                        )
                    )
                } catch (e: Throwable) {
                    // Ignore notification failures in test/headless context
                }
            }
        }
    }

    suspend fun sendMessageCloud(
        recipient: UserProfile,
        text: String,
        stampId: String? = null,
        stampTitle: String? = null,
        stampImageUrl: String? = null,
        stampLocation: String? = null
    ): Result<DirectMessage> = withContext(Dispatchers.IO) {
        val currentUid = authRepo.currentUser.value.userId
        val token = authRepo.accessToken.value ?: supabaseClient.userAccessToken

        if (currentUid.isBlank() || currentUid.startsWith("guest_") || !authRepo.isUserLoggedIn() || token.isNullOrBlank()) {
            return@withContext Result.failure(SecurityException("Authentication required to send direct messages"))
        }

        if (recipient.userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Recipient ID required"))
        }

        val cleanText = text.trim()
        if (cleanText.isBlank() && stampTitle.isNullOrBlank() && stampImageUrl.isNullOrBlank() && stampId.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Message content cannot be blank"))
        }

        val current = authRepo.currentUser.value
        val fallbackText = if (cleanText.isBlank() && !stampTitle.isNullOrBlank()) {
            "📮 Đã gửi con tem: $stampTitle"
        } else if (cleanText.isBlank() && !stampImageUrl.isNullOrBlank()) {
            "📮 Đã gửi một con tem kỷ niệm"
        } else {
            cleanText
        }

        val resolvedImage = if (com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(stampImageUrl)) stampImageUrl?.trim() else null

        val msg = DirectMessage(
            id = UUID.randomUUID().toString(),
            senderId = currentUid,
            senderName = current.displayName,
            senderAvatar = current.avatarUrl,
            recipientId = recipient.userId,
            recipientName = recipient.displayName,
            recipientAvatar = recipient.avatarUrl,
            text = fallbackText,
            stampId = stampId,
            stampTitle = stampTitle,
            stampImageUrl = resolvedImage,
            stampLocation = stampLocation,
            createdAt = System.currentTimeMillis(),
            isRead = false
        )

        // Validate payload before transport & retrieve authoritative server row
        val res = supabaseClient.sendDirectMessage(msg)
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Send direct message failed"))
        }

        val serverMsg = res.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Server response representation missing"))
        val map = _messages.value.associateBy { it.id }.toMutableMap()
        map[serverMsg.id] = serverMsg
        val updated = map.values.sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })
        saveMessagesForUser(currentUid, updated)

        Result.success(serverMsg)
    }



    suspend fun markAsReadCloud(otherUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val currentUid = authRepo.currentUser.value.userId
        val token = authRepo.accessToken.value ?: supabaseClient.userAccessToken

        if (currentUid.isBlank() || currentUid.startsWith("guest_") || !authRepo.isUserLoggedIn() || token.isNullOrBlank()) {
            return@withContext Result.failure(SecurityException("Authentication required"))
        }

        val res = supabaseClient.markMessagesAsRead(otherUserId, currentUid)
        if (res.isSuccess) {
            val updated = _messages.value.map { msg ->
                if (msg.senderId == otherUserId && msg.recipientId == currentUid && !msg.isRead) {
                    msg.copy(isRead = true)
                } else {
                    msg
                }
            }
            saveMessagesForUser(currentUid, updated)
            Result.success(true)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Mark read failed"))
        }
    }

    fun deleteMessage(messageId: String) {
        val currentUid = authRepo.currentUser.value.userId
        val updated = _messages.value.filterNot { it.id == messageId }
        saveMessagesForUser(currentUid, updated)

        coroutineScope.launch {
            try {
                supabaseClient.deleteDirectMessage(messageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getMessagesBetween(userId1: String, userId2: String): List<DirectMessage> {
        return _messages.value
            .filter { (it.senderId == userId1 && it.recipientId == userId2) || (it.senderId == userId2 && it.recipientId == userId1) }
            .sortedWith(compareBy<DirectMessage> { it.createdAt }.thenBy { it.id })
    }

    fun findMessageByStampId(stampId: String): DirectMessage? {
        return _messages.value.find { it.stampId == stampId }
    }

    fun getConversationList(friends: List<UserProfile>): List<ChatConversation> {
        val currentUid = authRepo.currentUser.value.userId
        return friends.map { friend ->
            val chatHistory = _messages.value
                .filter { (it.senderId == currentUid && it.recipientId == friend.userId) || (it.senderId == friend.userId && it.recipientId == currentUid) }
                .sortedByDescending { it.createdAt }
            val last = chatHistory.firstOrNull()
            val unread = chatHistory.count { it.senderId == friend.userId && it.recipientId == currentUid && !it.isRead }
            ChatConversation(
                otherUser = friend,
                lastMessage = last,
                unreadCount = unread
            )
        }.sortedByDescending { it.lastMessage?.createdAt ?: 0L }
    }

    fun encodeLocalImageToBase64IfNeeded(pathOrUrl: String?): String? {
        return if (com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(pathOrUrl)) pathOrUrl?.trim() else null
    }

    companion object {
        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
