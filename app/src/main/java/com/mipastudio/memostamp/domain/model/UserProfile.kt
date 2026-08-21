package com.mipastudio.memostamp.domain.model

data class UserProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val displayUsername: String
        get() = "@$username"
}

sealed interface PendingAuthAction {
    data class SendStamp(val stampId: String) : PendingAuthAction
    data object OpenFriends : PendingAuthAction
    data class ReplyPost(val postId: String) : PendingAuthAction
    data class LikePost(val postId: String) : PendingAuthAction
}
