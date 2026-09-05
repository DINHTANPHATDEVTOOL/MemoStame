package com.mipastudio.memostamp.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.mipastudio.memostamp.data.remote.supabase.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Production push device token manager for Android.
 * Manages device installation UUID, token rotation, Supabase server registration,
 * unregistration on logout, and privacy-preserving caching with zero token logging.
 */
object PushTokenManager {

    private const val PREFS_NAME = "memostamp_push_prefs"
    private const val KEY_INSTALLATION_ID = "installation_id"
    private const val KEY_CACHED_TOKEN = "cached_fcm_token"
    private const val KEY_LAST_REGISTERED_TOKEN = "last_registered_token"
    private const val KEY_LAST_REGISTERED_UID = "last_registered_uid"

    private var fallbackInstallId: String? = null
    private var fallbackToken: String? = null
    private var fallbackLastToken: String? = null
    private var fallbackLastUid: String? = null

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Retrieves or generates a stable, app-private installation identifier.
     * Never uses hardware identifiers (IMEI, MAC, Android ID).
     */
    fun getInstallationId(context: Context): String {
        val prefs = getPrefs(context)
        if (prefs == null) {
            if (fallbackInstallId == null) {
                fallbackInstallId = UUID.randomUUID().toString()
            }
            return fallbackInstallId!!
        }
        var installId = prefs.getString(KEY_INSTALLATION_ID, null)
        if (installId.isNullOrBlank()) {
            installId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, installId).apply()
        }
        return installId
    }

    /**
     * Safely reads cached FCM token from app-private storage.
     */
    fun getCachedToken(context: Context): String? {
        val prefs = getPrefs(context) ?: return fallbackToken
        return prefs.getString(KEY_CACHED_TOKEN, null) ?: fallbackToken
    }

    /**
     * Stores FCM token in app-private storage without logging.
     */
    fun saveCachedToken(context: Context, token: String) {
        if (token.isBlank()) return
        fallbackToken = token.trim()
        getPrefs(context)?.edit()?.putString(KEY_CACHED_TOKEN, token.trim())?.apply()
    }

    /**
     * Invoked when FCM provides a new device token.
     */
    fun onNewToken(context: Context, token: String) {
        if (token.isBlank()) return
        saveCachedToken(context, token)

        // If an authenticated session is active, synchronize new token with server
        try {
            val authRepo = com.mipastudio.memostamp.data.repository.UserAuthRepository.getInstance(context)
            val authUid = authRepo.authUserId.value
            val accessToken = authRepo.accessToken.value
            if (!authUid.isNullOrBlank() && !accessToken.isNullOrBlank() && !authUid.startsWith("guest_")) {
                registerDeviceTokenWithServer(context, accessToken, authUid, token)
            }
        } catch (_: Throwable) {
            // Best effort; token will sync upon next session restore or login
        }
    }

    /**
     * Registers current device token with Supabase via register_push_device_token RPC.
     * Derives caller identity on server side via auth.uid().
     */
    fun registerCurrentDeviceToken(
        context: Context,
        accessToken: String?,
        userId: String?
    ) {
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank() || userId.startsWith("guest_")) {
            return
        }

        getFcmTokenSafe(context) { token ->
            if (!token.isNullOrBlank()) {
                registerDeviceTokenWithServer(context, accessToken, userId, token)
            }
        }
    }

    /**
     * Unregisters push device token on server before clearing session on logout.
     */
    fun unregisterDeviceToken(
        context: Context,
        accessToken: String?
    ) {
        if (accessToken.isNullOrBlank()) return
        fallbackLastToken = null
        fallbackLastUid = null

        try {
            val installId = getInstallationId(context)
            scope.launch {
                try {
                    executeUnregisterRpc(context, accessToken, installId)
                    getPrefs(context)?.edit()
                        ?.remove(KEY_LAST_REGISTERED_UID)
                        ?.remove(KEY_LAST_REGISTERED_TOKEN)
                        ?.apply()
                } catch (_: Throwable) {
                    // Best effort on logout
                }
            }
        } catch (_: Throwable) {
            // Safe in test/headless context
        }
    }

    /**
     * Clears local push registration records upon account deletion.
     */
    fun onAccountDeleted(context: Context) {
        fallbackLastToken = null
        fallbackLastUid = null
        fallbackToken = null
        try {
            getPrefs(context)?.edit()
                ?.remove(KEY_LAST_REGISTERED_UID)
                ?.remove(KEY_LAST_REGISTERED_TOKEN)
                ?.apply()
        } catch (_: Throwable) {}
        PushEventDeduper.clear()
    }

    /**
     * Safely retrieves FCM device token with fallback for environments lacking Firebase configuration.
     * Prevents crashes on devices or CI runs without google-services.json.
     */
    fun getFcmTokenSafe(context: Context, onToken: (String?) -> Unit) {
        val cached = getCachedToken(context)
        if (!cached.isNullOrBlank()) {
            onToken(cached)
            return
        }

        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                        val token = task.result.trim()
                        saveCachedToken(context, token)
                        onToken(token)
                    } else {
                        onToken(null)
                    }
                }
            } else {
                onToken(null)
            }
        } catch (_: Throwable) {
            onToken(null)
        }
    }

    private fun registerDeviceTokenWithServer(
        context: Context,
        accessToken: String,
        userId: String,
        token: String
    ) {
        val prefs = getPrefs(context)
        val lastToken = prefs?.getString(KEY_LAST_REGISTERED_TOKEN, null) ?: fallbackLastToken
        val lastUid = prefs?.getString(KEY_LAST_REGISTERED_UID, null) ?: fallbackLastUid

        // Coalesce redundant registrations
        if (lastToken == token && lastUid == userId) {
            return
        }

        val installId = getInstallationId(context)
        scope.launch {
            val success = executeRegisterRpc(context, accessToken, token, installId)
            if (success) {
                fallbackLastToken = token
                fallbackLastUid = userId
                prefs?.edit()
                    ?.putString(KEY_LAST_REGISTERED_TOKEN, token)
                    ?.putString(KEY_LAST_REGISTERED_UID, userId)
                    ?.apply()
            }
        }
    }

    private suspend fun executeRegisterRpc(
        context: Context,
        accessToken: String,
        token: String,
        installationId: String
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
            val anonKey = SupabaseConfig.getAnonKey(context)
            val endpoint = "$baseUrl/rest/v1/rpc/register_push_device_token"

            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val body = mapOf(
                "p_platform" to "android",
                "p_provider" to "fcm",
                "p_token" to token,
                "p_installation_id" to installationId,
                "p_environment" to "production"
            )

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(gson.toJson(body))
                writer.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun executeUnregisterRpc(
        context: Context,
        accessToken: String,
        installationId: String
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
            val anonKey = SupabaseConfig.getAnonKey(context)
            val endpoint = "$baseUrl/rest/v1/rpc/unregister_push_device_token"

            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val body = mapOf(
                "p_provider" to "fcm",
                "p_installation_id" to installationId
            )

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(gson.toJson(body))
                writer.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }
}
