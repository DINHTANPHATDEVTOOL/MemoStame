package com.mipastudio.memostamp.domain.model

enum class StampType {
    PERSONAL,
    FRIEND,
    SHARED_MEMORY,
    EVENT,
    LIMITED
}

data class StampElement(
    val id: String,
    val type: String, // "text", "sticker", "doodle", "badge", "date", "location"
    val value: String,
    val x: Float, // normalized position (0.0 - 1.0)
    val y: Float, // normalized position (0.0 - 1.0)
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val zIndex: Int = 1,
    val colorHex: String = "#2C2421"
)

data class TradeRecord(
    val fromUser: String,
    val toUser: String,
    val date: String,
    val note: String
)

data class Stamp(
    val id: String,
    val stampNumber: String, // e.g. "#DL-2026-00192"
    val title: String,
    val imageUrl: String,
    val creatorId: String,
    val creatorName: String,
    val creatorAvatar: String = "",
    val ownerId: String,
    val ownerName: String,
    val createdDate: String,
    val memoryDate: String,
    val location: String,
    val caption: String,
    val type: StampType = StampType.PERSONAL,
    val templateId: String = "classic_post", // classic_post, polaroid, airmail, vintage, passport, film
    val borderStyle: String = "perforated", // perforated, dashed, double_line, wavy
    val collectionId: String? = null,
    val collectionName: String? = null,
    val edition: String = "Original #001",
    val elements: List<StampElement> = emptyList(),
    val tags: List<String> = emptyList(),
    val isTradable: Boolean = true,
    val tradeHistory: List<TradeRecord> = emptyList(),
    val designJson: String = "{}"
)

data class StampCollection(
    val id: String,
    val title: String,
    val description: String,
    val coverImageUrl: String,
    val iconEmoji: String,
    val stampCount: Int,
    val totalCapacity: Int,
    val stamps: List<Stamp> = emptyList(),
    val category: String = "Travel"
)

data class UserStats(
    val memories: Int,
    val collections: Int,
    val friends: Int,
    val receivedStamps: Int
)

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val bio: String,
    val stats: UserStats,
    val isFriend: Boolean = false
)

data class TradeRequest(
    val tradeId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val receiverId: String,
    val offeredStamp: Stamp,
    val requestedStamp: Stamp,
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val createdAt: String
)

data class PassportVisa(
    val countryOrCity: String,
    val date: String,
    val category: String, // e.g. "🎓 Graduation", "🎂 Birthday", "✈ Da Lat"
    val stampCode: String
)

data class MemoryPassport(
    val ownerName: String,
    val username: String,
    val avatarUrl: String,
    val stats: UserStats,
    val visas: List<PassportVisa>
)
