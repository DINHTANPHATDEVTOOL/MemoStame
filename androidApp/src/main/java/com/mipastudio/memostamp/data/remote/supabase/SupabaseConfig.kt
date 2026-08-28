package com.mipastudio.memostamp.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences

object SupabaseConfig {
    const val DEFAULT_PROJECT_ID = "mghmhhbyhmuvherlyrqa"
    const val DEFAULT_REGION = "ap-northeast-1"
    const val DEFAULT_SUPABASE_URL = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    private const val PREFS_NAME = "memostamp_supabase_config"
    private const val KEY_URL = "supabase_url"
    private const val KEY_ANON_KEY = "supabase_anon_key"

    fun getSupabaseUrl(context: Context?): String {
        if (context == null) return DEFAULT_SUPABASE_URL
        val prefs = getPrefs(context) ?: return DEFAULT_SUPABASE_URL
        return prefs.getString(KEY_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
    }

    fun getAnonKey(context: Context?): String {
        if (context == null) return DEFAULT_ANON_KEY
        val prefs = getPrefs(context) ?: return DEFAULT_ANON_KEY
        val saved = prefs.getString(KEY_ANON_KEY, "")
        return if (!saved.isNullOrBlank() && !saved.startsWith("sb_publishable")) saved else DEFAULT_ANON_KEY
    }

    fun saveConfig(context: Context, url: String, anonKey: String) {
        val prefs = getPrefs(context) ?: return
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_ANON_KEY, anonKey.trim())
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }
}
