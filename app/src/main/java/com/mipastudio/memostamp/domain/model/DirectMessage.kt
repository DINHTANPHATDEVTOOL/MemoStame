package com.mipastudio.memostamp.domain.model

import com.mipastudio.memostamp.data.remote.UserProfile

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
)

data class ChatConversation(
    val otherUser: UserProfile,
    val lastMessage: DirectMessage?,
    val unreadCount: Int = 0
)
