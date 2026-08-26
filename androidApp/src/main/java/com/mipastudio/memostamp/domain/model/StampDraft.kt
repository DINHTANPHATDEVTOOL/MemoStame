package com.mipastudio.memostamp.domain.model

data class StampDraft(
    val id: String = "",
    val originalImagePath: String,
    val croppedImagePath: String? = null,
    val renderedImagePath: String,
    val title: String = "",
    val note: String = "",
    val memoryDate: Long = System.currentTimeMillis(),
    val location: String? = null,
    val mood: String? = null,
    val collectionId: String? = null,
    val filterId: String? = "original",
    val filterIntensity: Float = 1.0f,
    val filterSpecJson: String? = null
)
