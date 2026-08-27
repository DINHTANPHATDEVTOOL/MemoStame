package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidAuthSessionStore(context: Context) {

    val prefs: SharedPreferences? = try {
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
        System.err.println("SecureSessionStore initialization failed: ${e.message}")
        null
    }

    val isAvailable: Boolean get() = prefs != null

    fun save(session: AndroidAuthSession): Boolean {
        val p = prefs ?: return false
        return try {
            p.edit()
                .putString("auth_user_id", session.userId)
                .putString("auth_email", session.email)
                .putString("access_token", session.accessToken)
                .putString("refresh_token", session.refreshToken)
                .putLong("expires_at", session.expiresAt)
                .commit()
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    fun load(): AndroidAuthSession? {
        val p = prefs ?: return null
        return try {
            val userId = p.getString("auth_user_id", null) ?: return null
            val accessToken = p.getString("access_token", null) ?: return null
            val refreshToken = p.getString("refresh_token", null) ?: return null
            val email = p.getString("auth_email", "") ?: ""
            val expiresAt = p.getLong("expires_at", 0L)

            if (userId.isBlank() || accessToken.isBlank()) return null

            AndroidAuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
                userId = userId,
                email = email
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun clear() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
