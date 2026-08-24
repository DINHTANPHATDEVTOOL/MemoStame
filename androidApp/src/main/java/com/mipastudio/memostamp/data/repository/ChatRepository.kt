package com.mipastudio.memostamp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mipastudio.memostamp.core.notification.InAppBanner
import com.mipastudio.memostamp.core.notification.InAppNotificationManager
import com.mipastudio.memostamp.core.notification.MemoStampNotificationManager
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.data.remote.supabase.SupabaseClient
import com.mipastudio.memostamp.domain.model.ChatConversation
import com.mipastudio.memostamp.domain.model.DirectMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ChatRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("memostamp_chat_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val authRepo = UserAuthRepository.getInstance(context)
    private val supabaseClient = SupabaseClient.getInstance(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val notifiedMsgIds = mutableSetOf<String>()

    private val _messages = MutableStateFlow<List<DirectMessage>>(emptyList())
    val messages: StateFlow<List<DirectMessage>> = _messages.asStateFlow()

    // The user ID currently open in ChatScreen (to avoid redundant banners while looking at that chat)
    var activeChattingUserId: String? = null

    init {
        val initialList = loadMessages()
        _messages.value = initialList
        notifiedMsgIds.addAll(initialList.map { it.id })
        coroutineScope.launch {
            syncMessagesLoop()
        }
    }

    private suspend fun syncMessagesLoop() {
        while (coroutineScope.isActive) {
            try {
                val currentUid = authRepo.currentUser.value.userId
                if (currentUid.isNotBlank() && authRepo.isUserLoggedIn()) {
                    val cloudMessages = supabaseClient.getMessagesForUser(currentUid)
                    if (cloudMessages.isNotEmpty()) {
                        val localMap = _messages.value.associateBy { it.id }.toMutableMap()

                        // Identify newly arrived messages for current user
                        val newIncomingMessages = cloudMessages.filter { msg ->
                            msg.recipientId == currentUid &&
                            msg.senderId != currentUid &&
                            !msg.isRead &&
                            !notifiedMsgIds.contains(msg.id)
                        }

                        cloudMessages.forEach { localMap[it.id] = it }
                        val merged = localMap.values.sortedBy { it.createdAt }
                        if (merged != _messages.value) {
                            saveMessages(merged)
                        }

                        // Dispatch notifications for incoming messages
                        newIncomingMessages.forEach { msg ->
                            notifiedMsgIds.add(msg.id)
                            if (activeChattingUserId != msg.senderId) {
                                // System Notification
                                MemoStampNotificationManager.sendNewMessageNotification(context, msg)

                                // In-App Floating Banner
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
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(3500) // Poll Supabase every 3.5 seconds
        }
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

    private fun loadMessages(): List<DirectMessage> {
        val json = prefs.getString("direct_messages_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DirectMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMessages(list: List<DirectMessage>) {
        _messages.value = list
        prefs.edit().putString("direct_messages_json", gson.toJson(list)).apply()
    }

    fun getMessagesBetween(userId1: String, userId2: String): List<DirectMessage> {
        return _messages.value
            .filter { (it.senderId == userId1 && it.recipientId == userId2) || (it.senderId == userId2 && it.recipientId == userId1) }
            .sortedBy { it.createdAt }
    }

    fun encodeLocalImageToBase64IfNeeded(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("data:image/")) {
            return pathOrUrl
        }
        return try {
            val file = File(pathOrUrl)
            if (!file.exists() || file.length() == 0L) return pathOrUrl
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            
            // Optimal display size for mobile postage stamps (480px width/height gives crisp detail on Retina while saving ~85% bandwidth)
            val targetMaxDim = 480
            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= targetMaxDim || boundsOptions.outHeight / (sampleSize * 2) >= targetMaxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { 
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 16-bit color reduces memory footprint by 50%
            }
            val sourceBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return pathOrUrl
            
            // Accurate high-quality bicubic scale down to target bounding box
            val srcW = sourceBitmap.width
            val srcH = sourceBitmap.height
            val scale = (targetMaxDim.toFloat() / Math.max(srcW, srcH)).coerceAtMost(1.0f)
            val finalW = (srcW * scale).toInt().coerceAtLeast(1)
            val finalH = (srcH * scale).toInt().coerceAtLeast(1)
            
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(sourceBitmap, finalW, finalH, true)
            } else {
                sourceBitmap
            }

            val baos = ByteArrayOutputStream()
            // Compress with WebP Lossy (or modern WebP) if available for extreme size reduction (15-35KB), fallback to JPEG 68%
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 68, baos)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 68, baos)
            }
            val bytes = baos.toByteArray()
            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle()
            }
            sourceBitmap.recycle()

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/webp;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            pathOrUrl
        }
    }

    fun findMessageByStampId(stampId: String): DirectMessage? {
        return _messages.value.find { it.stampId == stampId }
    }

    fun sendMessage(
        recipient: UserProfile,
        text: String,
        stampId: String? = null,
        stampTitle: String? = null,
        stampImageUrl: String? = null,
        stampLocation: String? = null
    ): DirectMessage {
        val current = authRepo.currentUser.value
        val fallbackText = if (text.isBlank() && !stampTitle.isNullOrBlank()) {
            "📮 Đã gửi con tem: $stampTitle"
        } else if (text.isBlank() && !stampImageUrl.isNullOrBlank()) {
            "📮 Đã gửi một con tem kỷ niệm"
        } else {
            text
        }

        // Convert local image to Base64 data URI so all remote recipients and Supabase can read and render it
        val resolvedImage = encodeLocalImageToBase64IfNeeded(stampImageUrl) ?: stampImageUrl

        val msg = DirectMessage(
            id = "msg_" + UUID.randomUUID().toString().take(10),
            senderId = current.userId,
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
        val updated = _messages.value + msg
        saveMessages(updated)

        // Send to Supabase Cloud with full image payload
        coroutineScope.launch {
            try {
                supabaseClient.sendDirectMessage(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return msg
    }

    fun markAsRead(otherUserId: String) {
        val currentUid = authRepo.currentUser.value.userId
        val updated = _messages.value.map { msg ->
            if (msg.senderId == otherUserId && msg.recipientId == currentUid && !msg.isRead) {
                msg.copy(isRead = true)
            } else {
                msg
            }
        }
        saveMessages(updated)

        coroutineScope.launch {
            try {
                supabaseClient.markMessagesAsRead(otherUserId, currentUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val updated = _messages.value.filterNot { it.id == messageId }
        saveMessages(updated)

        coroutineScope.launch {
            try {
                supabaseClient.deleteDirectMessage(messageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
}
