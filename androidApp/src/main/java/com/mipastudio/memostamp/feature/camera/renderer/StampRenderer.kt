package com.mipastudio.memostamp.feature.camera.renderer

import android.content.Context
import android.graphics.*
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.mipastudio.memostamp.domain.model.StampElement
import com.mipastudio.memostamp.core.processor.CameraPreset
import com.mipastudio.memostamp.ui.components.StampGeometry
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

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
        val radius = maxOf(1.5f, minDimension * StampGeometry.NOTCH_RADIUS_RATIO)

        val topCount = maxOf(3, (width / (minDimension * StampGeometry.NOTCH_SPACING_RATIO)).roundToInt())
        val topSpacing = width / topCount.toFloat()
        path.moveTo(0f, 0f)
        for (i in 0 until topCount) {
            val cx = topSpacing * (i + 0.5f)
            path.lineTo(cx - radius, 0f)
            path.quadTo(cx, radius * 1.8f, cx + radius, 0f)
        }
        path.lineTo(width, 0f)

        val rightCount = maxOf(3, (height / (minDimension * StampGeometry.NOTCH_SPACING_RATIO)).roundToInt())
        val rightSpacing = height / rightCount.toFloat()
        for (i in 0 until rightCount) {
            val cy = rightSpacing * (i + 0.5f)
            path.lineTo(width, cy - radius)
            path.quadTo(width - radius * 1.8f, cy, width, cy + radius)
        }
        path.lineTo(width, height)

        for (i in (topCount - 1) downTo 0) {
            val cx = topSpacing * (i + 0.5f)
            path.lineTo(cx + radius, height)
            path.quadTo(cx, height - radius * 1.8f, cx - radius, height)
        }
        path.lineTo(0f, height)

        for (i in (rightCount - 1) downTo 0) {
            val cy = rightSpacing * (i + 0.5f)
            path.lineTo(0f, cy + radius)
            path.quadTo(radius * 1.8f, cy, 0f, cy - radius)
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

        // Avoid double-cropping if the input bitmap already matches target aspect ratio
        val currentRatio = processedImage.width.toFloat() / processedImage.height.toFloat()
        val srcRect = if (kotlin.math.abs(currentRatio - STAMP_ASPECT_RATIO) < 0.03f) {
            Rect(0, 0, processedImage.width, processedImage.height)
        } else {
            calculateCenterCrop(
                bitmapWidth = processedImage.width,
                bitmapHeight = processedImage.height,
                targetWidth = outputWidth,
                targetHeight = outputHeight
            )
        }

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
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = (10f / 350f) * outputHeight * el.scale
                        color = Color.WHITE
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = colorWithAlpha
                        style = Paint.Style.FILL
                    }
                    val fm = textP.fontMetrics
                    val textWidth = textP.measureText(el.value)
                    val textHeight = fm.bottom - fm.top
                    val bgRect = RectF(px - 12f, py - 4f, px + textWidth + 12f, py + textHeight + 4f)
                    canvas.drawRoundRect(bgRect, 10f, 10f, badgePaint)
                    val baselineY = py - fm.top
                    canvas.drawText(el.value, px, baselineY, textP)
                }
                "sticker" -> {
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = (24f / 350f) * outputHeight * el.scale
                        color = colorWithAlpha
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val fm = textP.fontMetrics
                    val baselineY = py - fm.top
                    canvas.drawText(el.value, px, baselineY, textP)
                }
                else -> {
                    val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = (12f / 350f) * outputHeight * el.scale
                        color = colorWithAlpha
                        typeface = Typeface.DEFAULT_BOLD
                        setShadowLayer(4f, 2f, 2f, Color.argb(128, 0, 0, 0))
                    }
                    val fm = textP.fontMetrics
                    val baselineY = py - fm.top
                    canvas.drawText(el.value, px, baselineY, textP)
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
