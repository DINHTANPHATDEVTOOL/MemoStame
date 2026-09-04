package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AndroidAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String
) {
    fun isExpired(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        return nowSeconds >= expiresAt - 30
    }
}

class SupabaseAuthService private constructor(private val context: Context? = null) {

    private val gson = Gson()

    private fun getBaseUrl(): String = SupabaseConfig.getSupabaseUrl(context)
    private fun getApiKey(): String = SupabaseConfig.getAnonKey(context)

    suspend fun signUp(
        email: String,
        password: String
    ): Result<AndroidAuthSession> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/auth/v1/signup"
        val bodyMap = mapOf("email" to email, "password" to password)
        return@withContext postAuthRequest(endpoint, gson.toJson(bodyMap))
    }

    suspend fun signIn(
        email: String,
        password: String
    ): Result<AndroidAuthSession> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/auth/v1/token?grant_type=password"
        val bodyMap = mapOf("email" to email, "password" to password)
        return@withContext postAuthRequest(endpoint, gson.toJson(bodyMap))
    }

    suspend fun refreshSession(
        refreshToken: String
    ): Result<AndroidAuthSession> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/auth/v1/token?grant_type=refresh_token"
        val bodyMap = mapOf("refresh_token" to refreshToken)
        return@withContext postAuthRequest(endpoint, gson.toJson(bodyMap))
    }

    suspend fun signOut(
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) return@withContext Result.success(Unit)
        val endpoint = "${getBaseUrl()}/auth/v1/logout"
        return@withContext try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.responseCode
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit) // Logout local cleanup proceeds even if offline
        }
    }

    suspend fun updateUserPassword(
        accessToken: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = "${getBaseUrl()}/auth/v1/user"
        val bodyMap = mapOf("password" to newPassword)
        return@withContext putAuthRequest(endpoint, accessToken, gson.toJson(bodyMap))
    }

    private fun putAuthRequest(endpoint: String, accessToken: String, jsonBody: String): Result<Unit> {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (!isSuccess) {
                val errorMsg = parseErrorMessage(responseText) ?: "Password update failed [$code]"
                return Result.failure(IllegalStateException(errorMsg))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postAuthRequest(endpoint: String, jsonBody: String): Result<AndroidAuthSession> {
        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (!isSuccess) {
                val errorMsg = parseErrorMessage(responseText) ?: "Supabase auth failed [$code]"
                return Result.failure(IllegalStateException(errorMsg))
            }

            val session = parseAuthSession(responseText)
                ?: return Result.failure(IllegalStateException("Failed to parse auth session"))
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(jsonText: String): String? {
        return try {
            val obj = gson.fromJson(jsonText, JsonObject::class.java)
            obj.get("msg")?.asString
                ?: obj.get("error_description")?.asString
                ?: obj.get("message")?.asString
                ?: obj.get("error")?.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAuthSession(jsonText: String): AndroidAuthSession? {
        return try {
            val obj = gson.fromJson(jsonText, JsonObject::class.java) ?: return null
            val accessToken = obj.get("access_token")?.asString ?: return null
            val refreshToken = obj.get("refresh_token")?.asString ?: return null
            
            val userObj = obj.getAsJsonObject("user") ?: return null
            val userId = userObj.get("id")?.asString ?: return null
            val email = userObj.get("email")?.asString ?: ""

            val expiresIn = obj.get("expires_in")?.asLong ?: 3600L
            val expiresAt = if (obj.has("expires_at")) {
                obj.get("expires_at").asLong
            } else {
                (System.currentTimeMillis() / 1000) + expiresIn
            }

            AndroidAuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userId = userId,
                email = email
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SupabaseAuthService? = null

        fun getInstance(context: Context? = null): SupabaseAuthService {
            return INSTANCE ?: synchronized(this) {
                val instance = SupabaseAuthService(context?.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
