package com.mipastudio.memostamp.feature.camera.renderer

import androidx.compose.ui.geometry.Rect

import com.mipastudio.memostamp.core.ui.StampGeometry

data class StampCropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object StampLayoutCalculator {
    const val MOLD_WIDTH_RATIO = StampGeometry.MOLD_WIDTH_RATIO
    const val MOLD_ASPECT_RATIO = StampGeometry.MOLD_ASPECT_RATIO

    const val INNER_LEFT_RATIO = StampGeometry.INNER_LEFT_RATIO
    const val INNER_TOP_RATIO = StampGeometry.INNER_TOP_RATIO
    const val INNER_RIGHT_RATIO = StampGeometry.INNER_RIGHT_RATIO
    const val INNER_BOTTOM_RATIO = StampGeometry.INNER_BOTTOM_RATIO

    fun calculateCenterCropBounds(
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): StampCropBounds {
        val sourceRatio = bitmapWidth.toFloat() / bitmapHeight
        val targetRatio = targetWidth.toFloat() / targetHeight

        return if (sourceRatio > targetRatio) {
            val requiredWidth = (bitmapHeight * targetRatio).toInt()
            val left = (bitmapWidth - requiredWidth) / 2
            StampCropBounds(left, 0, left + requiredWidth, bitmapHeight)
        } else {
            val requiredHeight = (bitmapWidth / targetRatio).toInt()
            val top = (bitmapHeight - requiredHeight) / 2
            StampCropBounds(0, top, bitmapWidth, top + requiredHeight)
        }
    }

    /**
     * Calculates the pixel bounds of the inner photo window for a given screen width and height.
     */
    fun calculateOpeningRect(
        width: Float,
        height: Float,
        pressOffsetPx: Float = 0f
    ): Rect {
        if (width <= 0f || height <= 0f) {
            return Rect(0f, 0f, width, height)
        }
        val moldWidth = width * MOLD_WIDTH_RATIO
        val moldHeight = moldWidth * MOLD_ASPECT_RATIO

        val moldLeft = (width - moldWidth) / 2f
        val moldTop = (height - moldHeight) / 2f + pressOffsetPx

        val captureLeft = moldLeft + moldWidth * INNER_LEFT_RATIO
        val captureTop = moldTop + moldHeight * INNER_TOP_RATIO
        val captureRight = moldLeft + moldWidth * INNER_RIGHT_RATIO
        val captureBottom = moldTop + moldHeight * INNER_BOTTOM_RATIO

        return Rect(captureLeft, captureTop, captureRight, captureBottom)
    }

    /**
     * Calculates the normalized (0.0 to 1.0) capture rectangle coordinates for cropping.
     */
    fun calculateNormalizedCaptureRect(
        width: Float,
        height: Float,
        pressOffsetPx: Float = 0f
    ): StampCaptureRect {
        if (width <= 0f || height <= 0f) {
            return StampCaptureRect()
        }
        val rect = calculateOpeningRect(width, height, pressOffsetPx)
        return StampCaptureRect(
            left = (rect.left / width).coerceIn(0f, 1f),
            top = (rect.top / height).coerceIn(0f, 1f),
            right = (rect.right / width).coerceIn(0f, 1f),
            bottom = (rect.bottom / height).coerceIn(0f, 1f)
        )
    }
}
