package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.remote.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SupabaseStampDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("stamp_image_url") val stampImageUrl: String?,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("note") val note: String?
)

data class SupabaseProfileDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("email") val email: String,
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("cover_url") val coverUrl: String,
    @SerializedName("bio") val bio: String,
    @SerializedName("city") val city: String
)

class SupabaseSyncService(private val context: Context) {
    private val gson = Gson()

    suspend fun syncProfileToSupabase(profile: UserProfile): Result<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = SupabaseConfig.getSupabaseUrl(context)
        val anonKey = SupabaseConfig.getAnonKey(context)

        if (anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Vui lòng nhập Supabase Anon Key để kết nối."))
        }

        try {
            val dto = SupabaseProfileDto(
                userId = profile.userId,
                username = profile.username,
                displayName = profile.displayName,
                email = profile.email,
                avatarUrl = profile.avatarUrl,
                coverUrl = profile.coverUrl,
                bio = profile.bio,
                city = profile.city
            )
            val jsonPayload = gson.toJson(dto)
            val endpoint = "$baseUrl/rest/v1/profiles?on_conflict=user_id"
            
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $anonKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonPayload) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Result.success(true)
            } else {
                val error = readResponse(connection, isError = true)
                Result.failure(Exception("Supabase HTTP $responseCode: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encodeStampImageForCloud(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("data:image/")) {
            return pathOrUrl
        }
        return try {
            val file = File(pathOrUrl)
            if (!file.exists() || file.length() == 0L) return pathOrUrl
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
            val sourceBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return pathOrUrl

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

            val baos = ByteArrayOutputStream()
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

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/webp;base64,$base64"
        } catch (e: Exception) {
            e.printStackTrace()
            pathOrUrl
        }
    }

    suspend fun syncStampsToSupabase(stamps: List<StampEntity>, userId: String): Result<Int> = withContext(Dispatchers.IO) {
        val baseUrl = SupabaseConfig.getSupabaseUrl(context)
        val anonKey = SupabaseConfig.getAnonKey(context)

        if (anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Vui lòng nhập Supabase Anon Key để kết nối."))
        }

        if (stamps.isEmpty()) {
            return@withContext Result.success(0)
        }

        try {
            val dtoList = stamps.map { stamp ->
                SupabaseStampDto(
                    id = stamp.id,
                    userId = userId,
                    title = stamp.title,
                    category = stamp.templateId ?: "vintage",
                    location = stamp.location ?: "Vietnam",
                    stampImageUrl = encodeStampImageForCloud(stamp.stampImagePath),
                    createdAt = stamp.createdAt,
                    note = stamp.note
                )
            }

            val jsonPayload = gson.toJson(dtoList)
            val endpoint = "$baseUrl/rest/v1/stamps?on_conflict=id"

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $anonKey")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonPayload) }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Result.success(stamps.size)
            } else {
                val error = readResponse(connection, isError = true)
                Result.failure(Exception("Supabase HTTP $responseCode: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readResponse(connection: HttpURLConnection, isError: Boolean): String {
        return try {
            val stream = if (isError) connection.errorStream else connection.inputStream
            stream?.let {
                BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
            } ?: ""
        } catch (e: Exception) {
            "Lỗi kết nối: ${e.message}"
        }
    }
}

