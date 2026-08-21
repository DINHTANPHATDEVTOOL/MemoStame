package com.mipastudio.memostamp.domain.model

enum class AudienceType(val label: String, val icon: String) {
    ONLY_ME("Only me", "🔒"),
    FRIENDS("Friends", "👥"),
    CIRCLE("Circle", "⭕")
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

data class StampItem(
    val id: String,
    val originalImagePath: String,
    val stampImagePath: String,
    val title: String,
    val note: String,
    val createdAt: Long,
    val memoryDate: Long,
    val location: String? = null,
    val mood: String? = "✨",
    val collectionId: String? = null,
    val favorite: Boolean = false,
    val filterId: String? = "original",
    val shape: String = "classic",
    val preset: String = "NATURAL"
)

data class CollectionItem(
    val id: String,
    val name: String,
    val description: String?,
    val iconEmoji: String = "📁",
    val collectionType: String = "NORMAL",
    val targetCount: Int = 12,
    val stampsCount: Int = 0
)

data class UserProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String = "Capturing life, one stamp at a time ✨",
    val stampsCreatedCount: Int = 14,
    val stampsCollectedCount: Int = 38,
    val placesVisitedCount: Int = 9
)

data class FriendItem(
    val id: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String,
    val isOnline: Boolean = true,
    val tradeCount: Int = 3
)

data class TradeRequest(
    val id: String,
    val senderName: String,
    val senderAvatar: String,
    val stampTitle: String,
    val stampUrl: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    val createdAt: Long
)

data class StampElement(
    val id: String,
    val type: String, // "text", "sticker", "doodle", "badge", "date", "location"
    val value: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val zIndex: Int = 1,
    val colorHex: String = "#2C2421"
)

data class StampTemplate(
    val id: String,
    val name: String,
    val descriptionText: String,
    val iconEmoji: String,
    val backgroundAsset: String? = null,
    val frameAsset: String? = null,
    val defaultElements: List<StampElement> = emptyList(),
    val photoInsetLeft: Float = 0f,
    val photoInsetTop: Float = 0f,
    val photoInsetRight: Float = 1f,
    val photoInsetBottom: Float = 1f
)

object StampTemplates {
    val PHOTO_STAMP = StampTemplate("classic_post", "Classic Post", "Authentic die-cut postage stamp edge", "📮")
    val AIRMAIL = StampTemplate("airmail", "Air Mail ✈", "Retro airmail striped border with flight markings", "✈")
    val POLAROID = StampTemplate("polaroid", "Polaroid 📷", "Classic instant film frame", "📷")
    val VINTAGE = StampTemplate("vintage", "Vintage 📜", "Aged parchment paper border", "📜")
    val PASSPORT = StampTemplate("passport", "Passport 🎓", "Travel visa stamp frame", "🎓")
    val SAKURA = StampTemplate("sakura", "Sakura ✿", "Soft spring cherry blossom frame", "✿")

    val ALL = listOf(PHOTO_STAMP, AIRMAIL, POLAROID, VINTAGE, PASSPORT, SAKURA)

    fun getById(id: String): StampTemplate = ALL.firstOrNull { it.id == id } ?: PHOTO_STAMP
}

data class CameraFilterSpec(
    val id: String = "original",
    val name: String = "Original",
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val grain: Float = 0f,
    val vignette: Float = 0f,
    val intensity: Float = 1.0f
)

object FilterPresets {
    val ORIGINAL = CameraFilterSpec("original", "Original")
    val NATURAL = CameraFilterSpec("natural", "Natural", exposure = 0.00f, contrast = 0.04f, saturation = 0.03f)
    val WARM = CameraFilterSpec("warm", "Warm", exposure = 0.05f, contrast = 0.03f, warmth = 0.10f)
    val SOFT = CameraFilterSpec("soft", "Soft", exposure = 0.08f, contrast = -0.04f, warmth = 0.03f)
    val BRIGHT = CameraFilterSpec("bright", "Bright", exposure = 0.12f, contrast = 0.02f)
    val FILM_35MM = CameraFilterSpec("film_35mm", "Film 35mm", contrast = 0.06f, warmth = 0.04f, grain = 0.18f, vignette = 0.08f)
    val GRAINY_FILM = CameraFilterSpec("grainy_film", "Grainy Film", contrast = 0.08f, grain = 0.30f, vignette = 0.10f)
    val VINTAGE_FADE = CameraFilterSpec("vintage_fade", "Vintage Fade", contrast = -0.03f, warmth = 0.08f, grain = 0.14f)
    val MONO_FILM = CameraFilterSpec("mono_film", "Mono Film", contrast = 0.12f, saturation = -1.00f, grain = 0.20f)
    val CAFE_COZY = CameraFilterSpec("cafe_cozy", "Cafe Cozy", contrast = 0.05f, warmth = 0.12f, grain = 0.10f)

    val ALL = listOf(ORIGINAL, NATURAL, WARM, SOFT, BRIGHT, FILM_35MM, GRAINY_FILM, VINTAGE_FADE, MONO_FILM, CAFE_COZY)

    fun getById(id: String?): CameraFilterSpec = ALL.find { it.id == id } ?: ORIGINAL
}

data class StampDesignSpec(
    val filterSpec: CameraFilterSpec = FilterPresets.FILM_35MM,
    val templateId: String = "classic_post",
    val borderStyle: String = "perforated",
    val elements: List<StampElement> = emptyList()
)

data class PassportBadge(
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val isUnlocked: Boolean = true
)
