package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.mipastudio.memostamp.BuildConfig

object SupabaseConfig {
    private val DEFAULT_SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val DEFAULT_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    
    val DEFAULT_PROJECT_ID = BuildConfig.SUPABASE_PROJECT_ID
    val DEFAULT_REGION = BuildConfig.SUPABASE_REGION

    private const val RETIRED_PROJECT_HOST = "mghmhhbyhmuvherlyrqa.supabase.co"
    // Base64url fragment of {"ref":"mghmhhbyhmuvherlyrqa"...} inside legacy anon JWTs.
    private const val RETIRED_ANON_REF_FRAGMENT = "bWdobWhoYnlobXV2aGVybHlycWE"

    private const val PREFS_NAME = "memostamp_supabase_config"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON_KEY = "supabase_anon_key"

    fun getSupabaseUrl(context: Context?): String {
        if (!BuildConfig.DEBUG || context == null) return DEFAULT_SUPABASE_URL
        val prefs = getPrefs(context) ?: return DEFAULT_SUPABASE_URL
        val saved = prefs.getString(KEY_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
        // Drop stale overrides that still point at the retired project.
        if (saved.contains(RETIRED_PROJECT_HOST)) return DEFAULT_SUPABASE_URL
        return saved
    }

    fun getAnonKey(context: Context?): String {
        if (!BuildConfig.DEBUG || context == null) return DEFAULT_ANON_KEY
        val prefs = getPrefs(context) ?: return DEFAULT_ANON_KEY
        val saved = prefs.getString(KEY_ANON_KEY, "")
        if (saved.isNullOrBlank() || saved.startsWith("sb_publishable")) return DEFAULT_ANON_KEY
        if (saved.contains(RETIRED_ANON_REF_FRAGMENT)) return DEFAULT_ANON_KEY
        return saved
    }

    fun saveConfig(context: Context, url: String, anonKey: String): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }
        val prefs = getPrefs(context) ?: return false
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_ANON_KEY, anonKey.trim())
            .apply()
        return true
    }

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }
}
