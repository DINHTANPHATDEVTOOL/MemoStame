package com.mipastudio.memostamp.data.local

import com.mipastudio.memostamp.data.remote.supabase.SupabaseAuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

data class RecoverySessionData(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val email: String
)

sealed interface PasswordRecoveryState {
    object Idle : PasswordRecoveryState
    object Validating : PasswordRecoveryState
    data class Ready(val userId: String, val email: String) : PasswordRecoveryState
    object Updating : PasswordRecoveryState
    object Success : PasswordRecoveryState
    data class Invalid(val message: String) : PasswordRecoveryState
}

object PasswordRecoveryParser {
    const val CANONICAL_SCHEME = "memostamp"
    const val CANONICAL_HOST = "auth"
    const val CANONICAL_PATH = "/recovery"
    const val CANONICAL_REDIRECT_URL = "memostamp://auth/recovery"

    fun parseUri(uriString: String?): Result<Pair<String, String?>> {
        if (uriString.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("URI không được để trống"))
        }

        val trimmed = uriString.trim()
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("URI không đúng định dạng: ${e.message}"))
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != CANONICAL_SCHEME) {
            return Result.failure(IllegalArgumentException("Scheme không hợp lệ: $scheme (yêu cầu $CANONICAL_SCHEME)"))
        }

        val host = uri.host?.lowercase()
        if (host != CANONICAL_HOST) {
            return Result.failure(IllegalArgumentException("Host không hợp lệ: $host (yêu cầu $CANONICAL_HOST)"))
        }

        val path = uri.path
        if (path != CANONICAL_PATH && path != "$CANONICAL_PATH/") {
            return Result.failure(IllegalArgumentException("Path không hợp lệ: $path (yêu cầu $CANONICAL_PATH)"))
        }

        val paramMap = mutableMapOf<String, String>()

        // 1. Parse fragment parameters (#access_token=...&type=recovery)
        val fragment = uri.rawFragment
        if (!fragment.isNullOrBlank()) {
            parseQueryStringInto(fragment, paramMap)
        }

        // 2. Parse query parameters (?access_token=...&type=recovery)
        val query = uri.rawQuery
        if (!query.isNullOrBlank()) {
            parseQueryStringInto(query, paramMap)
        }

        // 3. Validate recovery type if present
        val type = paramMap["type"]
        if (type != null && !type.equals("recovery", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Loại token không phải recovery: $type"))
        }

        // 4. Extract token
        val accessToken = paramMap["access_token"] ?: paramMap["token"]
        if (accessToken.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Thiếu token khôi phục trong URI"))
        }

        val refreshToken = paramMap["refresh_token"]

        return Result.success(Pair(accessToken, refreshToken))
    }

    private fun parseQueryStringInto(queryOrFragment: String, targetMap: MutableMap<String, String>) {
        val pairs = queryOrFragment.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = try {
                    java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(0, idx)
                }
                val value = try {
                    java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(idx + 1)
                }
                if (!targetMap.containsKey(key)) {
                    targetMap[key] = value
                }
            }
        }
    }

    fun sanitizeForLogging(uriString: String?): String {
        if (uriString.isNullOrBlank()) return "[empty]"
        return try {
            val uri = URI(uriString.trim())
            val scheme = uri.scheme ?: "unknown"
            val host = uri.host ?: "unknown"
            val path = uri.path ?: ""
            "$scheme://$host$path [credentials redacted]"
        } catch (_: Exception) {
            "[malformed-uri]"
        }
    }

    fun isValidCanonicalAuthUid(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        val trimmed = uid.trim()
        val lower = trimmed.lowercase()
        if (lower == "user_me" || lower == "guest" || lower.startsWith("guest_") || lower.startsWith("guest")) {
            return false
        }
        return try {
            java.util.UUID.fromString(trimmed)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun validatePassword(password: String, confirm: String): Result<Unit> {
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự"))
        }
        if (password != confirm) {
            return Result.failure(IllegalArgumentException("Mật khẩu xác nhận không khớp"))
        }
        return Result.success(Unit)
    }
}

class PasswordRecoveryCoordinator private constructor() {

    private val _recoveryState = MutableStateFlow<PasswordRecoveryState>(PasswordRecoveryState.Idle)
    val recoveryState: StateFlow<PasswordRecoveryState> = _recoveryState.asStateFlow()

    // Ephemeral in-memory only recovery session
    @Volatile
    private var ephemeralSession: RecoverySessionData? = null

    // Track consumed tokens to prevent replay in same process
    private val consumedTokens = mutableSetOf<String>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun handleDeepLink(uriString: String?, authService: SupabaseAuthService = SupabaseAuthService.getInstance()) {
        if (uriString.isNullOrBlank()) return

        val parseResult = PasswordRecoveryParser.parseUri(uriString)
        val (accessToken, refreshToken) = parseResult.getOrElse { error ->
            _recoveryState.value = PasswordRecoveryState.Invalid(error.message ?: "Liên kết không hợp lệ")
            return
        }

        if (consumedTokens.contains(accessToken)) {
            _recoveryState.value = PasswordRecoveryState.Invalid("Liên kết này đã được sử dụng. Vui lòng yêu cầu liên kết mới.")
            return
        }

        _recoveryState.value = PasswordRecoveryState.Validating

        coroutineScope.launch {
            val userResult = authService.validateRecoveryUser(accessToken)
            userResult.fold(
                onSuccess = { userInfo ->
                    ephemeralSession = RecoverySessionData(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        userId = userInfo.userId,
                        email = userInfo.email
                    )
                    _recoveryState.value = PasswordRecoveryState.Ready(
                        userId = userInfo.userId,
                        email = userInfo.email
                    )
                },
                onFailure = { err ->
                    ephemeralSession = null
                    _recoveryState.value = PasswordRecoveryState.Invalid(
                        err.message ?: "Không thể xác thực thông tin tài khoản từ liên kết"
                    )
                }
            )
        }
    }

    suspend fun updatePassword(
        newPassword: String,
        confirmPassword: String,
        authService: SupabaseAuthService = SupabaseAuthService.getInstance()
    ): Result<Unit> {
        val policyResult = PasswordRecoveryParser.validatePassword(newPassword, confirmPassword)
        if (policyResult.isFailure) {
            return policyResult
        }

        val session = ephemeralSession
            ?: return Result.failure(IllegalStateException("Không có phiên khôi phục hợp lệ"))

        if (consumedTokens.contains(session.accessToken)) {
            return Result.failure(IllegalStateException("Phiên khôi phục đã hết hạn hoặc đã được sử dụng"))
        }

        _recoveryState.value = PasswordRecoveryState.Updating

        val updateResult = authService.updateUserPassword(session.accessToken, newPassword)

        return updateResult.fold(
            onSuccess = {
                // Immediately consume and destroy ephemeral session
                consumedTokens.add(session.accessToken)
                ephemeralSession = null
                _recoveryState.value = PasswordRecoveryState.Success
                Result.success(Unit)
            },
            onFailure = { error ->
                _recoveryState.value = PasswordRecoveryState.Ready(
                    userId = session.userId,
                    email = session.email
                )
                Result.failure(error)
            }
        )
    }

    fun resetState() {
        ephemeralSession = null
        _recoveryState.value = PasswordRecoveryState.Idle
    }

    fun getVerifiedEmail(): String? = ephemeralSession?.email

    companion object {
        @Volatile
        private var instance: PasswordRecoveryCoordinator? = null

        fun getInstance(): PasswordRecoveryCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PasswordRecoveryCoordinator().also { instance = it }
            }
        }
    }
}
