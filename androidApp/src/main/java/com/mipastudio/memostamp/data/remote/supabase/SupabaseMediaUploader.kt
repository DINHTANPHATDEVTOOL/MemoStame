package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class SupabaseMediaUploader private constructor(private val context: Context) {

    private val authRepo = UserAuthRepository.getInstance(context)

    companion object {
        private const val TAG = "SupabaseMediaUploader"
        private const val BUCKET_NAME = "stamp-media"
        private const val MAX_FILE_SIZE = 8 * 1024 * 1024 // 8 MB

        @Volatile
        private var instance: SupabaseMediaUploader? = null

        fun getInstance(context: Context): SupabaseMediaUploader {
            val safeContext = try { context.applicationContext } catch (_: Throwable) { context }
            return instance ?: synchronized(this) {
                instance ?: SupabaseMediaUploader(safeContext).also { instance = it }
            }
        }

        fun computeSha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun ensureRemoteRenderedStamp(
        ownerUid: String,
        localOrRemotePath: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (localOrRemotePath.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Media path is empty"))
        }

        val cleanPath = localOrRemotePath.trim()

        // 1. If already a valid remote HTTPS/HTTP URL, return as-is
        if (isValidRemoteStampUrl(cleanPath)) {
            return@withContext Result.success(cleanPath)
        }

        // 2. Reject unsupported protocols
        if (cleanPath.startsWith("data:") || cleanPath.startsWith("blob:")) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported data/blob URI for remote media"))
        }

        // 3. Strict identity check: ownerUid must be valid and match active auth session
        val activeUid = authRepo.authUserId.value?.trim()
        if (activeUid.isNullOrBlank() || activeUid == "user_me" || activeUid.startsWith("guest") || activeUid != ownerUid.trim()) {
            return@withContext Result.failure(IllegalStateException("Active authenticated session required for owner $ownerUid"))
        }

        val userJwt = authRepo.accessToken.value?.trim()
        if (userJwt.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("User access token required for media upload"))
        }

        // 4. Resolve local file bytes
        val fileBytes = try {
            readMediaBytes(cleanPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local media bytes for path: $cleanPath", e)
            return@withContext Result.failure(e)
        } ?: return@withContext Result.failure(IllegalArgumentException("Local media file not found or empty: $cleanPath"))

        // 5. Downscale/compress if exceeds 8MB
        val uploadBytes = if (fileBytes.size > MAX_FILE_SIZE) {
            downscaleBytes(fileBytes) ?: fileBytes
        } else {
            fileBytes
        }

        if (uploadBytes.size > MAX_FILE_SIZE) {
            return@withContext Result.failure(IllegalStateException("Rendered image exceeds 8MB limit"))
        }

        // 6. Content hash and storage path
        val contentHash = computeSha256Hex(uploadBytes)
        val ext = detectExtension(cleanPath, uploadBytes)
        val mimeType = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val storagePath = "$activeUid/rendered/$contentHash.$ext"
        val baseUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
        val anonKey = SupabaseConfig.getAnonKey(context).trim()
        val uploadEndpoint = "$baseUrl/storage/v1/object/$BUCKET_NAME/$storagePath"
        val publicUrl = "$baseUrl/storage/v1/object/public/$BUCKET_NAME/$storagePath"

        // 7. Upload to Supabase Storage
        try {
            val url = URL(uploadEndpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $userJwt")
                setRequestProperty("Content-Type", mimeType)
                setRequestProperty("x-upsert", "true")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setFixedLengthStreamingMode(uploadBytes.size)
            }

            conn.outputStream.use { os ->
                os.write(uploadBytes)
                os.flush()
            }

            val code = conn.responseCode
            // 200, 201: upload success. 409: conflict (already exists, idempotent success)
            if (code in 200..299 || code == 409) {
                Log.i(TAG, "Uploaded stamp media: $storagePath (HTTP $code)")
                Result.success(publicUrl)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Storage upload failed [$code]: $err")
                Result.failure(Exception("Supabase storage error ($code): $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network failure uploading stamp media: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun readMediaBytes(path: String): ByteArray? {
        val file = if (path.startsWith("file://")) {
            File(Uri.parse(path).path ?: return null)
        } else if (path.startsWith("/")) {
            File(path)
        } else {
            try {
                val uri = Uri.parse(path)
                if (uri.scheme == "content") {
                    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            } catch (_: Throwable) {}
            File(path)
        }

        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return null
        }
        return file.readBytes()
    }

    private fun detectExtension(path: String, bytes: ByteArray): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "png"
            lower.endsWith(".webp") -> "webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "jpg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "webp"
            else -> "png"
        }
    }

    private fun downscaleBytes(bytes: ByteArray): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 1200
            val srcW = bitmap.width
            val srcH = bitmap.height
            val scale = (maxDim.toFloat() / Math.max(srcW, srcH)).coerceAtMost(1.0f)
            val finalW = (srcW * scale).toInt().coerceAtLeast(1)
            val finalH = (srcH * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, finalW, finalH, true)
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 90, baos)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            baos.toByteArray()
        } catch (_: Throwable) {
            null
        }
    }
}
