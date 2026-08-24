package com.mipastudio.memostamp.feature.camera.renderer

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

object StampCropper {
    /**
     * Crops a bitmap based on screen coordinates of the mold opening window,
     * accurately handling PreviewView.ScaleType.FILL_CENTER scaling transform between phone screen and camera sensor.
     */
    fun cropToStampRect(
        sourceBitmap: Bitmap,
        openingRect: Rect,
        screenWidth: Float,
        screenHeight: Float,
        rotationDegrees: Int = 0
    ): Bitmap {
        var rotatedBitmap = sourceBitmap
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            rotatedBitmap = Bitmap.createBitmap(
                sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true
            )
        }

        val bitmapWidth = rotatedBitmap.width.toFloat()
        val bitmapHeight = rotatedBitmap.height.toFloat()

        if (screenWidth <= 0f || screenHeight <= 0f || bitmapWidth <= 0f || bitmapHeight <= 0f) {
            return rotatedBitmap
        }

        val arSensor = bitmapWidth / bitmapHeight
        val arScreen = screenWidth / screenHeight

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (arSensor > arScreen) {
            // Sensor is wider than screen relative to height: height fits screen height, left/right cropped off screen
            scale = screenHeight / bitmapHeight
            val scaledWidth = bitmapWidth * scale
            offsetX = (scaledWidth - screenWidth) / 2f
            offsetY = 0f
        } else {
            // Sensor is narrower/taller than screen: width fits screen width, top/bottom cropped off screen
            scale = screenWidth / bitmapWidth
            val scaledHeight = bitmapHeight * scale
            offsetX = 0f
            offsetY = (scaledHeight - screenHeight) / 2f
        }

        // Map screen pixel bounds to sensor bitmap pixel bounds
        val sensorLeft = ((openingRect.left + offsetX) / scale).toInt().coerceIn(0, rotatedBitmap.width - 1)
        val sensorTop = ((openingRect.top + offsetY) / scale).toInt().coerceIn(0, rotatedBitmap.height - 1)
        val sensorRight = ((openingRect.right + offsetX) / scale).toInt().coerceIn(sensorLeft + 1, rotatedBitmap.width)
        val sensorBottom = ((openingRect.bottom + offsetY) / scale).toInt().coerceIn(sensorTop + 1, rotatedBitmap.height)

        val cropWidth = max(1, sensorRight - sensorLeft)
        val cropHeight = max(1, sensorBottom - sensorTop)

        return Bitmap.createBitmap(
            rotatedBitmap,
            sensorLeft,
            sensorTop,
            cropWidth,
            cropHeight
        )
    }

    /**
     * Fallback crop method using normalized screen coordinates (0.0 to 1.0).
     */
    fun cropToNormalizedRect(
        sourceBitmap: Bitmap,
        rect: StampCaptureRect = StampCaptureRect(),
        rotationDegrees: Int = 0
    ): Bitmap {
        var rotatedBitmap = sourceBitmap
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            rotatedBitmap = Bitmap.createBitmap(
                sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true
            )
        }

        val bitmapWidth = rotatedBitmap.width
        val bitmapHeight = rotatedBitmap.height

        val cropLeft = (bitmapWidth * rect.left).toInt().coerceIn(0, bitmapWidth - 1)
        val cropTop = (bitmapHeight * rect.top).toInt().coerceIn(0, bitmapHeight - 1)
        val cropRight = (bitmapWidth * rect.right).toInt().coerceIn(cropLeft + 1, bitmapWidth)
        val cropBottom = (bitmapHeight * rect.bottom).toInt().coerceIn(cropTop + 1, bitmapHeight)

        val cropWidth = max(1, cropRight - cropLeft)
        val cropHeight = max(1, cropBottom - cropTop)

        return Bitmap.createBitmap(
            rotatedBitmap,
            cropLeft,
            cropTop,
            cropWidth,
            cropHeight
        )
    }
}

