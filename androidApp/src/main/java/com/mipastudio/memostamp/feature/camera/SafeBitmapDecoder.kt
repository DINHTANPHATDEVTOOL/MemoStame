package com.mipastudio.memostamp.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object SafeBitmapDecoder {

    fun decodeSampledBitmap(
        context: Context,
        uriOrPath: String,
        reqWidth: Int = 1500,
        reqHeight: Int = 2000
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            openInputStream(context, uriOrPath)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val sampledBitmap = openInputStream(context, uriOrPath)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            val orientation = getExifOrientation(context, uriOrPath)
            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                applyExifOrientation(sampledBitmap, orientation)
            } else {
                sampledBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openInputStream(context: Context, uriOrPath: String): InputStream? {
        return if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(uriOrPath))
        } else {
            val file = File(uriOrPath)
            if (file.exists()) file.inputStream() else null
        }
    }

    private fun getExifOrientation(context: Context, uriOrPath: String): Int {
        return try {
            val inputStream = openInputStream(context, uriOrPath) ?: return ExifInterface.ORIENTATION_NORMAL
            inputStream.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun saveOriginalToAppFiles(
        context: Context,
        sourceUriOrPath: String
    ): String? {
        return try {
            val bitmap = decodeSampledBitmap(context, sourceUriOrPath) ?: return null
            val dir = File(context.filesDir, "originals").apply { if (!exists()) mkdirs() }
            val file = File(dir, "orig_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
