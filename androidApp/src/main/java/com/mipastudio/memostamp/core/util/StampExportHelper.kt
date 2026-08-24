package com.mipastudio.memostamp.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object StampExportHelper {

    enum class ExportMode {
        STAMP_IMAGE,   // Full stamp with die-cut / paper background
        STICKER_ALPHA  // Transparent die-cut cutout sticker
    }

    fun exportToGallery(
        context: Context,
        bitmap: Bitmap,
        filenamePrefix: String = "MEMO_STAMP",
        exportMode: ExportMode = ExportMode.STAMP_IMAGE
    ): Uri? {
        val finalBitmap = if (exportMode == ExportMode.STAMP_IMAGE) {
            val cardW = (bitmap.width * 1.16f).toInt()
            val cardH = (bitmap.height * 1.16f).toInt()
            val result = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)

            val paperPaint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F5F1E8") }
            canvas.drawRect(0f, 0f, cardW.toFloat(), cardH.toFloat(), paperPaint)

            val shadowPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#25000000")
                isAntiAlias = true
            }
            val left = (cardW - bitmap.width) / 2f
            val top = (cardH - bitmap.height) / 2f
            canvas.drawRect(left + 10f, top + 14f, left + bitmap.width + 10f, top + bitmap.height + 14f, shadowPaint)

            canvas.drawBitmap(bitmap, left, top, null)
            result
        } else {
            bitmap
        }

        val filename = "${filenamePrefix}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MemoStamp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (finalBitmap != bitmap && !finalBitmap.isRecycled) {
                finalBitmap.recycle()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            if (finalBitmap != bitmap && !finalBitmap.isRecycled) {
                finalBitmap.recycle()
            }
            return null
        }
    }

    fun shareStamp(
        context: Context,
        bitmap: Bitmap,
        title: String = "MemoStamp Memory"
    ) {
        try {
            val cacheDir = File(context.cacheDir, "shared_stamps")
            cacheDir.mkdirs()
            val file = File(cacheDir, "stamp_share_${System.currentTimeMillis()}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "✦ Memory Stamp: $title ✦")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Stamp Memory")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
