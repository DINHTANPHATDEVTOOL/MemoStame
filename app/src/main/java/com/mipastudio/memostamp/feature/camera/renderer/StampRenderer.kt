package com.mipastudio.memostamp.feature.camera.renderer

import android.content.Context
import android.graphics.*
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.mipastudio.memostamp.core.model.StampElement
import com.mipastudio.memostamp.core.processor.CameraPreset
import com.mipastudio.memostamp.core.ui.StampGeometry
import java.io.File
import java.io.FileOutputStream

object StampRenderer {

    const val STAMP_WIDTH = StampGeometry.OUTPUT_WIDTH
    const val STAMP_HEIGHT = StampGeometry.OUTPUT_HEIGHT
    const val STAMP_ASPECT_RATIO = StampGeometry.ASPECT_RATIO

    fun createStampPath(
        width: Float,
        height: Float
    ): Path {
        val path = Path()
        val minDimension = minOf(width, height)
        val radius = minDimension * StampGeometry.NOTCH_RADIUS_RATIO
        val spacing = minDimension * StampGeometry.NOTCH_SPACING_RATIO

        path.moveTo(0f, 0f)

        // TOP
        var x = spacing / 2f
        while (x < width - spacing / 2f) {
            path.lineTo(x - radius, 0f)
            path.quadTo(x, radius * 1.8f, x + radius, 0f)
            x += spacing
        }
        path.lineTo(width, 0f)

        // RIGHT
        var y = spacing / 2f
        while (y < height - spacing / 2f) {
            path.lineTo(width, y - radius)
            path.quadTo(width - radius * 1.8f, y, width, y + radius)
            y += spacing
        }
        path.lineTo(width, height)

        // BOTTOM
        x = width - spacing / 2f
        while (x > spacing / 2f) {
            path.lineTo(x + radius, height)
            path.quadTo(x, height - radius * 1.8f, x - radius, height)
            x -= spacing
        }
        path.lineTo(0f, height)

        // LEFT
        y = height - spacing / 2f
        while (y > spacing / 2f) {
            path.lineTo(0f, y + radius)
            path.quadTo(radius * 1.8f, y, 0f, y - radius)
            y -= spacing
        }
        path.lineTo(0f, 0f)

        path.close()
        return path
    }

    fun getColorMatrixForPreset(preset: CameraPreset): ColorMatrix {
        return ColorMatrix(preset.getColorMatrixArray())
    }

    fun calculateCenterCrop(
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Rect {
        val bounds = StampLayoutCalculator.calculateCenterCropBounds(bitmapWidth, bitmapHeight, targetWidth, targetHeight)
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    fun renderStampBitmap(
        context: Context,
        croppedBitmap: Bitmap,
        preset: CameraPreset = CameraPreset.NATURAL,
        elements: List<StampElement> = emptyList(),
        filterSpec: com.mipastudio.memostamp.core.processor.CameraFilterSpec? = null
    ): Bitmap {
        val outputWidth = STAMP_WIDTH
        val outputHeight = STAMP_HEIGHT
        val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val stampPath = createStampPath(outputWidth.toFloat(), outputHeight.toFloat())

        val saveCount = canvas.save()
        canvas.clipPath(stampPath)

        val processedImage = if (filterSpec != null) {
            com.mipastudio.memostamp.core.processor.MemoImageProcessor.applyFilterSpec(croppedBitmap, filterSpec)
        } else {
            com.mipastudio.memostamp.core.processor.MemoImageProcessor.applyPreset(croppedBitmap, preset)
        }

        val srcRect = calculateCenterCrop(
            bitmapWidth = processedImage.width,
            bitmapHeight = processedImage.height,
            targetWidth = outputWidth,
            targetHeight = outputHeight
        )

        val destination = RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(processedImage, srcRect, destination, paint)

        elements.sortedBy { it.zIndex }.forEach { el ->
            canvas.save()
            val px = el.x * outputWidth
            val py = el.y * outputHeight
            canvas.rotate(el.rotation, px, py)

            val parsedColor = try {
                Color.parseColor(el.colorHex)
            } catch (e: Exception) {
                Color.WHITE
            }
            val alphaInt = (el.opacity.coerceIn(0f, 1f) * 255).toInt()
            val colorWithAlpha = (parsedColor and 0x00FFFFFF) or (alphaInt shl 24)

            when (el.type) {
                "badge" -> {
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = colorWithAlpha
                        style = Paint.Style.FILL
                    }
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 36f * el.scale
                        color = Color.WHITE
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val textWidth = textP.measureText(el.value)
                    val bgRect = RectF(px - 10f, py - 36f, px + textWidth + 10f, py + 10f)
                    canvas.drawRoundRect(bgRect, 8f, 8f, badgePaint)
                    canvas.drawText(el.value, px, py, textP)
                }
                else -> {
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 64f * el.scale
                        color = colorWithAlpha
                        typeface = Typeface.DEFAULT_BOLD
                        setShadowLayer(4f, 2f, 2f, Color.argb(128, 0, 0, 0))
                    }
                    canvas.drawText(el.value, px, py, textP)
                }
            }
            canvas.restore()
        }

        canvas.restoreToCount(saveCount)
        return result
    }

    /**
     * Renders a photo into an authentic die-cut perforated Postage Stamp PNG file with WYSIWYG element overlays.
     */
    fun renderStampToPng(
        context: Context,
        croppedBitmap: Bitmap,
        preset: CameraPreset = CameraPreset.NATURAL,
        elements: List<StampElement> = emptyList(),
        filterSpec: com.mipastudio.memostamp.core.processor.CameraFilterSpec? = null,
        outputFileName: String = "stamp_${System.currentTimeMillis()}.png"
    ): File {
        val result = renderStampBitmap(context, croppedBitmap, preset, elements, filterSpec)
        val outputDir = File(context.filesDir, "stamps").apply { if (!exists()) mkdirs() }
        val outputFile = File(outputDir, outputFileName)
        FileOutputStream(outputFile).use { out ->
            result.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        if (!result.isRecycled) {
            result.recycle()
        }

        return outputFile
    }
}
