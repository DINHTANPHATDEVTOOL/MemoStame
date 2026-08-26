package com.mipastudio.memostamp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stamps")
data class StampEntity(
    @PrimaryKey
    val id: String,
    val originalImagePath: String,
    val croppedImagePath: String? = null,
    val stampImagePath: String,
    val title: String,
    val note: String,
    val createdAt: Long,
    val memoryDate: Long,
    val location: String? = null,
    val mood: String? = null,
    val collectionId: String? = null,
    val favorite: Boolean = false,
    val preset: String? = "NATURAL",
    val templateId: String? = "classic_post",
    val borderStyle: String? = "perforated",
    val designJson: String? = null,
    val filterId: String? = "original",
    val filterIntensity: Float = 1.0f,
    val filterSpecJson: String? = null
)
