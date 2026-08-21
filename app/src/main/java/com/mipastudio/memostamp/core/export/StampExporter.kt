package com.mipastudio.memostamp.core.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StampExporter {

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String = "MemoStamp"): Uri? {
        val filename = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MemoStamp")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val memoStampDir = File(imagesDir, "MemoStamp")
                if (!memoStampDir.exists()) {
                    memoStampDir.mkdirs()
                }
                val imageFile = File(memoStampDir, filename)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                imageUri = Uri.fromFile(imageFile)
            }

            Toast.makeText(context, "Stamp saved to Gallery! 🖼️", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save stamp: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return imageUri
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, caption: String = "Check out my MemoStamp memory! 📮") {
        try {
            val shareCacheDir = File(context.cacheDir, "shares")
            if (!shareCacheDir.exists()) {
                shareCacheDir.mkdirs()
            }
            val shareFile = File(shareCacheDir, "shared_memostamp_${System.currentTimeMillis()}.png")
            FileOutputStream(shareFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, shareFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share MemoStamp 📮")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share stamp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
