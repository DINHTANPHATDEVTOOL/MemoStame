package com.mipastudio.memostamp.domain.model

import com.mipastudio.memostamp.data.repository.UserProfile

data class DirectMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val recipientId: String,
    val recipientName: String,
    val recipientAvatar: String,
    val text: String,
    val stampId: String? = null,
    val stampTitle: String? = null,
    val stampImageUrl: String? = null,
    val stampLocation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    companion object {
        fun isValidRemoteStampUrl(url: String?): Boolean = com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl(url)
    }
}

fun isValidRemoteStampUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    val lower = trimmed.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return false
    }
    if (lower.startsWith("data:image/") ||
        lower.startsWith("file://") ||
        lower.startsWith("content://") ||
        lower.startsWith("/data/") ||
        lower.startsWith("/storage/")
    ) {
        return false
    }
    return true
}

data class ChatConversation(
    val otherUser: UserProfile,
    val lastMessage: DirectMessage?,
    val unreadCount: Int = 0
)

