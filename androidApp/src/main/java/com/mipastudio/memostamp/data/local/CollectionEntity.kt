package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val iconEmoji: String? = "📁",
    val coverStampId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val collectionType: String = "NORMAL", // NORMAL, CHALLENGE
    val targetCount: Int = 12
)
