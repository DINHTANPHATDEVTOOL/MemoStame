package com.mipastudio.memostamp.domain.model

enum class AudienceType(val label: String, val icon: String, val description: String) {
    FRIENDS("Tất cả bạn bè", "👥", "Chỉ tất cả bạn bè xem được"),
    SPECIFIC_FRIENDS("Bạn bè chọn lọc", "🎯", "Chỉ hiển thị với bạn bè được chọn"),
    ONLY_ME("Chỉ mình tôi", "🔒", "Chỉ mình tôi xem được");

    companion object {
        fun fromString(value: String?): AudienceType {
            if (value == null) return FRIENDS
            return try {
                valueOf(value)
            } catch (e: Exception) {
                when (value.uppercase()) {
                    "EVERYONE", "PUBLIC", "ALL", "FRIENDS" -> FRIENDS
                    "SPECIFIC_FRIENDS", "SPECIFIC", "CUSTOM" -> SPECIFIC_FRIENDS
                    "ONLY_ME", "PRIVATE" -> ONLY_ME
                    else -> FRIENDS
                }
            }
        }
    }
}

enum class FeedPostType {
    MEMORY,
    STAMP_REPLY,
    COLLECTION_MILESTONE
}

data class FeedReaction(
    val id: String,
    val postId: String,
    val userId: String,
    val userName: String,
    val emoji: String = "❤️",
    val createdAt: Long
)

data class FeedComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val createdAt: Long
)

data class FeedReply(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val replyStampId: String,
    val replyStampUrl: String?,
    val shape: String = "classic",
    val note: String? = null,
    val createdAt: Long
)

data class Circle(
    val id: String,
    val ownerId: String,
    val name: String,
    val icon: String = "⭕",
    val memberIds: List<String> = emptyList(),
    val createdAt: Long
)

data class FeedPost(
    val id: String,
    val stampId: String,
    val stampUrl: String,
    val stampTitle: String,
    val shape: String = "classic",
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val caption: String? = null,
    val audienceType: AudienceType = AudienceType.FRIENDS,
    val targetFriendIds: List<String> = emptyList(),
    val circleId: String? = null,
    val circleName: String? = null,
    val createdAt: Long,
    val type: FeedPostType = FeedPostType.MEMORY,
    val location: String? = null,
    val reactionCount: Int = 0,
    val commentCount: Int = 0,
    val replyCount: Int = 0,
    val reactions: List<FeedReaction> = emptyList(),
    val comments: List<FeedComment> = emptyList(),
    val replies: List<FeedReply> = emptyList(),
    val isLikedByMe: Boolean = false,
    val isSeen: Boolean = false
)
