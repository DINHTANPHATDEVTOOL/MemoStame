package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.UserProfile
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
    private val authRepo = com.mipastudio.memostamp.data.repository.UserAuthRepository.getInstance(context)

    suspend fun syncProfileToSupabase(profile: UserProfile): Result<Boolean> = withContext(Dispatchers.IO) {
        val baseUrl = SupabaseConfig.getSupabaseUrl(context)
        val anonKey = SupabaseConfig.getAnonKey(context)

        if (anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Vui lòng nhập Supabase Anon Key để kết nối."))
        }

        val activeUid = authRepo.authUserId.value?.trim()
        if (activeUid.isNullOrBlank() || activeUid == "user_me" || activeUid.startsWith("guest") || activeUid != profile.userId.trim()) {
            return@withContext Result.failure(IllegalStateException("Active authenticated session required for user ${profile.userId}"))
        }

        val userJwt = authRepo.accessToken.value?.trim()
        if (userJwt.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("User access token required for profile sync"))
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
                setRequestProperty("Authorization", "Bearer $userJwt")
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

