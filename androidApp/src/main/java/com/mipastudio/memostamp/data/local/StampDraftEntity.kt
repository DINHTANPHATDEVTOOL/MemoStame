package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class StampDraftEntity(
    @PrimaryKey
    val id: String,
    val ownerId: String = "",
    val originalImagePath: String,
    val croppedImagePath: String? = null,
    val renderedImagePath: String,
    val createdAt: Long,
    val title: String,
    val note: String,
    val memoryDate: Long,
    val location: String? = null,
    val mood: String? = null,
    val collectionId: String? = null,
    val filterId: String? = "original",
    val filterIntensity: Float = 1.0f,
    val filterSpecJson: String? = null
)
