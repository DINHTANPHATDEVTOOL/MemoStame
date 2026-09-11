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

data class RecoveryUserInfo(
    val userId: String,
    val email: String
)

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
        val signUpResult = postAuthRequest(endpoint, gson.toJson(bodyMap), isSignUp = true)
        if (signUpResult.isSuccess) {
            return@withContext signUpResult
        }

        // Signup can return 200 with a user and no session (confirm-email / GoTrue shape).
        // If the account is already usable, password grant recovers the session.
        val signUpMessage = signUpResult.exceptionOrNull()?.message.orEmpty()
        val missingSession = signUpMessage.contains("access_token", ignoreCase = true) ||
            signUpMessage.contains("parse auth session", ignoreCase = true) ||
            signUpMessage.contains("xác nhận tài khoản", ignoreCase = true)
        if (!missingSession) {
            return@withContext signUpResult
        }

        val signInEndpoint = "${getBaseUrl()}/auth/v1/token?grant_type=password"
        val signInResult = postAuthRequest(signInEndpoint, gson.toJson(bodyMap))
        if (signInResult.isSuccess) {
            return@withContext signInResult
        }

        val signInMessage = signInResult.exceptionOrNull()?.message.orEmpty().lowercase()
        if (signInMessage.contains("confirm") || signInMessage.contains("not confirmed")) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản trước khi đăng nhập."
                )
            )
        }

        return@withContext signUpResult
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

    suspend fun requestPasswordRecovery(
        email: String,
        redirectTo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("Địa chỉ email không hợp lệ"))
        }
        val encodedRedirect = java.net.URLEncoder.encode(redirectTo, "UTF-8")
        val endpoint = "${getBaseUrl()}/auth/v1/recover?redirect_to=$encodedRedirect"
        val bodyMap = mapOf(
            "email" to normalizedEmail,
            "redirect_to" to redirectTo
        )
        return@withContext try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("redirect_to", redirectTo)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(gson.toJson(bodyMap))
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (!isSuccess) {
                val errorMsg = parseErrorMessage(responseText) ?: "Yêu cầu khôi phục mật khẩu thất bại [$code]"
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateRecoveryUser(
        accessToken: String
    ): Result<RecoveryUserInfo> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Access token cannot be blank"))
        }
        val endpoint = "${getBaseUrl()}/auth/v1/user"
        return@withContext try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 12000
                readTimeout = 12000
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (!isSuccess) {
                val errorMsg = parseErrorMessage(responseText) ?: "Xác thực phiên khôi phục thất bại [$code]"
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            val userObj = gson.fromJson(responseText, JsonObject::class.java)
            val uid = userObj.get("id")?.asString
            val email = userObj.get("email")?.asString ?: ""

            if (uid.isNullOrBlank() || !com.mipastudio.memostamp.data.local.PasswordRecoveryParser.isValidCanonicalAuthUid(uid)) {
                return@withContext Result.failure(IllegalStateException("Thông tin người dùng không hợp lệ"))
            }

            Result.success(RecoveryUserInfo(userId = uid, email = email))
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun deleteAccount(
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Access token cannot be blank"))
        }
        val endpoint = "${getBaseUrl()}/functions/v1/delete-account"
        return@withContext try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", getApiKey())
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write("{}")
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val isSuccess = code in 200..299
            val stream = if (isSuccess) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

            if (!isSuccess) {
                val errorMsg = parseErrorMessage(responseText) ?: "Account deletion failed [$code]"
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private fun postAuthRequest(
        endpoint: String,
        jsonBody: String,
        isSignUp: Boolean = false
    ): Result<AndroidAuthSession> {
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
                val errorMsg = parseErrorMessage(responseText)?.let { humanizeAuthError(it) }
                    ?: "Supabase auth failed [$code]"
                return Result.failure(IllegalStateException(errorMsg))
            }

            val session = parseAuthSession(responseText)
            if (session != null) {
                return Result.success(session)
            }

            // Email confirmation enabled: signup returns user without access/refresh tokens.
            if (isSignUp && hasSignupUserWithoutSession(responseText)) {
                return Result.failure(
                    IllegalStateException(
                        "Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản trước khi đăng nhập."
                    )
                )
            }

            Result.failure(
                IllegalStateException(
                    "Failed to parse auth session (server không trả access_token). Kiểm tra Confirm email trên đúng project Supabase."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun humanizeAuthError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("rate limit") || lower.contains("over_email_send_rate_limit") ->
                "Đã gửi quá nhiều email xác nhận. Vui lòng đợi vài phút rồi thử lại (hoặc tắt Confirm email trên project đúng)."
            else -> raw
        }
    }

    private fun hasSignupUserWithoutSession(jsonText: String): Boolean {
        return try {
            val obj = gson.fromJson(jsonText, JsonObject::class.java) ?: return false
            if (!obj.get("access_token")?.asString.isNullOrBlank()) return false
            val nestedUserId = obj.getAsJsonObject("user")?.get("id")?.asString
            val rootUserId = obj.get("id")?.asString
            // GoTrue may return either { user: { id } } or the user object at the root.
            !nestedUserId.isNullOrBlank() || !rootUserId.isNullOrBlank()
        } catch (_: Exception) {
            false
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
            val accessToken = obj.get("access_token")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val refreshToken = obj.get("refresh_token")?.asString?.takeIf { it.isNotBlank() } ?: return null

            val userObj = obj.getAsJsonObject("user") ?: return null
            val userId = userObj.get("id")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val email = userObj.get("email")?.asString ?: ""

            val expiresIn = readEpochSeconds(obj.get("expires_in")) ?: 3600L
            val expiresAt = readEpochSeconds(obj.get("expires_at"))
                ?: ((System.currentTimeMillis() / 1000) + expiresIn)

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

    private fun readEpochSeconds(element: com.google.gson.JsonElement?): Long? {
        if (element == null || element.isJsonNull) return null
        return try {
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber ->
                    element.asJsonPrimitive.asDouble.toLong()
                element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                    element.asString.toDoubleOrNull()?.toLong()
                else -> null
            }
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
