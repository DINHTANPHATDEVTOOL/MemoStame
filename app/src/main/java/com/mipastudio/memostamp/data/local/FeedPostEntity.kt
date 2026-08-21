package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_posts")
data class FeedPostEntity(
    @PrimaryKey val id: String,
    val stampId: String,
    val stampUrl: String,
    val stampTitle: String,
    val shape: String = "classic",
    val authorId: String,
    val authorName: String,
    val authorAvatar: String,
    val caption: String?,
    val audienceType: String, // ONLY_ME, FRIENDS, CIRCLE
    val circleId: String? = null,
    val circleName: String? = null,
    val createdAt: Long,
    val type: String = "MEMORY", // MEMORY, SHARED_MEMORY, RECEIVED_MEMORY, MEMORY_CHAIN
    val coAuthorsJson: String? = null,
    val location: String? = null
)
