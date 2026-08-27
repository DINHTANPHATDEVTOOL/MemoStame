package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidAuthSessionStore(context: Context) {

    private val prefs: SharedPreferences = createEncryptedSharedPreferences(context)

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "memostamp_secure_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            context.getSharedPreferences("memostamp_secure_session", Context.MODE_PRIVATE)
        }
    }

    fun save(session: AndroidAuthSession) {
        prefs.edit()
            .putString("auth_user_id", session.userId)
            .putString("auth_email", session.email)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putLong("expires_at", session.expiresAt)
            .apply()
    }

    fun load(): AndroidAuthSession? {
        val userId = prefs.getString("auth_user_id", null) ?: return null
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        val email = prefs.getString("auth_email", "") ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (userId.isBlank() || accessToken.isBlank()) return null

        return AndroidAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            userId = userId,
            email = email
        )
    }

    fun clear() {
        prefs.edit()
            .remove("auth_user_id")
            .remove("auth_email")
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .apply()
    }
}
