package com.mipastudio.memostamp.core.processor

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraPreset(val displayName: String, val description: String) {
    NATURAL("Memo Natural", "EV -0.3 • Soft natural warmth & shadow depth"),
    WARM("Warm Memory", "Golden sunset glow & vivid autumn tones"),
    FILM("Analog Film", "Kodak film contrast & teal/amber vintage tones"),
    VINTAGE("Retro Sepia", "Classic desaturated sepia memory card"),
    BW("Monochrome", "High-contrast silver postage stamp");

    fun getColorMatrixArray(): FloatArray {
        return when (this) {
            NATURAL -> floatArrayOf(
                1.08f, 0.0f,  0.0f,  0.0f, -4f,
                0.0f,  1.04f, 0.0f,  0.0f, -2f,
                0.0f,  0.0f,  0.95f, 0.0f,  2f,
                0.0f,  0.0f,  0.0f,  1.0f,  0f
            )
            WARM -> floatArrayOf(
                1.22f, 0.06f, 0.0f,  0.0f, 18f,
                0.05f, 1.12f, 0.0f,  0.0f, 10f,
                0.0f,  0.0f,  0.78f, 0.0f, -15f,
                0.0f,  0.0f,  0.0f,  1.0f, 0f
            )
            FILM -> floatArrayOf(
                1.15f, -0.05f, 0.0f,  0.0f, 12f,
                0.0f,   1.08f, 0.04f, 0.0f, 6f,
                -0.04f, 0.04f, 1.18f, 0.0f, 20f,
                0.0f,   0.0f,  0.0f,  1.0f, 0f
            )
            VINTAGE -> floatArrayOf(
                0.45f, 0.70f, 0.15f, 0.0f, 22f,
                0.38f, 0.65f, 0.14f, 0.0f, 12f,
                0.25f, 0.50f, 0.12f, 0.0f, -8f,
                0.0f,  0.0f,  0.0f,  1.0f, 0f
            )
            BW -> floatArrayOf(
                0.41f, 0.73f, 0.11f, 0.0f, -15f,
                0.41f, 0.73f, 0.11f, 0.0f, -15f,
                0.41f, 0.73f, 0.11f, 0.0f, -15f,
                0.0f,  0.0f,  0.0f,  1.0f, 0f
            )
        }
    }
}

data class ProcessedPhotoResult(
    val originalPath: String,
    val processedPath: String,
    val preset: CameraPreset
)

fun getComposeColorMatrix(preset: CameraPreset): androidx.compose.ui.graphics.ColorMatrix {
    return androidx.compose.ui.graphics.ColorMatrix(preset.getColorMatrixArray())
}

object MemoImageProcessor {

    fun applyPreset(sourceBitmap: Bitmap, preset: CameraPreset): Bitmap {
        val spec = FilterPresets.getById(preset.name.lowercase())
        return applyFilterSpec(sourceBitmap, spec)
    }

    fun applyFilterSpec(sourceBitmap: Bitmap, spec: CameraFilterSpec): Bitmap {
        return try {
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            if (width <= 0 || height <= 0) return sourceBitmap

            val processed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(processed)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Step 1: Apply ColorMatrix (Exposure, Contrast, Saturation, Warmth, Tint, Fade)
            val colorMatrix = spec.toAndroidColorMatrix()
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

            val effInt = spec.intensity.coerceIn(0f, 1f)

            // Step 2: Apply Vignette if enabled
            val vignetteVal = spec.vignette * effInt
            if (vignetteVal > 0.01f) {
                val cx = width / 2f
                val cy = height / 2f
                val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
                val vignetteAlpha = (vignetteVal * 180).toInt().coerceIn(0, 220)
                val colors = intArrayOf(Color.TRANSPARENT, Color.argb(vignetteAlpha, 0, 0, 0))
                val stops = floatArrayOf(0.45f, 1.0f)
                val vignetteGradient = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)

                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = vignetteGradient
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
            }

            // Step 3: Apply Analog Film Grain if enabled
            val grainVal = spec.grain * effInt
            if (grainVal > 0.01f) {
                val grainAlpha = (grainVal * 45).toInt().coerceIn(2, 60)
                val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(grainAlpha, 128, 128, 128)
                }
                val random = java.util.Random(42)
                val step = 4
                for (x in 0 until width step step) {
                    for (y in 0 until height step step) {
                        if (random.nextFloat() < 0.25f) {
                            val dotAlpha = random.nextInt(grainAlpha + 1)
                            grainPaint.color = Color.argb(dotAlpha, 255, 255, 255)
                            canvas.drawRect(x.toFloat(), y.toFloat(), (x + step).toFloat(), (y + step).toFloat(), grainPaint)
                        }
                    }
                }
            }

            processed
        } catch (e: Exception) {
            e.printStackTrace()
            sourceBitmap
        }
    }

    fun encodeImageToCloudBase64(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("data:image/")) {
            return pathOrUrl
        }
        return try {
            val cleanPath = pathOrUrl.removePrefix("file://")
            val file = File(cleanPath)
            if (!file.exists() || file.length() == 0L) return "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            val targetMaxDim = 480
            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= targetMaxDim || boundsOptions.outHeight / (sampleSize * 2) >= targetMaxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sourceBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"

            val srcW = sourceBitmap.width
            val srcH = sourceBitmap.height
            val scale = (targetMaxDim.toFloat() / Math.max(srcW, srcH)).coerceAtMost(1.0f)
            val finalW = (srcW * scale).toInt().coerceAtLeast(1)
            val finalH = (srcH * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(sourceBitmap, finalW, finalH, true)
            } else {
                sourceBitmap
            }

            val baos = java.io.ByteArrayOutputStream()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 68, baos)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 68, baos)
            }
            val bytes = baos.toByteArray()
            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle()
            }
            sourceBitmap.recycle()

            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/webp;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"
        }
    }

    fun resolveImageModel(pathOrUrl: String?): Any? {
        if (pathOrUrl.isNullOrBlank()) return "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"
        if (pathOrUrl.startsWith("data:image/")) {
            val commaIdx = pathOrUrl.indexOf(",")
            if (commaIdx != -1) {
                val base64Data = pathOrUrl.substring(commaIdx + 1)
                return try {
                    android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    pathOrUrl
                }
            }
        }
        if (pathOrUrl.startsWith("/") || pathOrUrl.startsWith("file://")) {
            val cleanPath = pathOrUrl.removePrefix("file://")
            val file = File(cleanPath)
            if (!file.exists() || file.length() == 0L) {
                return "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"
            }
        }
        return pathOrUrl
    }
}


