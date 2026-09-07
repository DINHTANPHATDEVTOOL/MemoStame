package com.mipastudio.memostamp.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Supported in-app language modes for MemoStamp.
 * Exactly three modes: SYSTEM, VIETNAMESE, and ENGLISH.
 */
enum class AppLanguageMode(val code: String) {
    SYSTEM("system"),
    VIETNAMESE("vi"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguageMode {
            return when (code?.trim()?.lowercase(Locale.ROOT)) {
                "vi" -> VIETNAMESE
                "en" -> ENGLISH
                "system" -> SYSTEM
                else -> SYSTEM
            }
        }
    }
}

/**
 * Production language management singleton for Android.
 * Responsible for:
 * - Persisting local language preference (independent of account auth/logout)
 * - Migrating legacy app_prefs / app_lang (vi / en -> VIETNAMESE / ENGLISH; missing/corrupt -> SYSTEM)
 * - Exposing active mode StateFlow to Jetpack Compose
 * - Applying per-app locale via platform LocaleManager (API 33+) and Configuration updates
 */
class AppLanguageManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentMode: MutableStateFlow<AppLanguageMode>
    val currentMode: StateFlow<AppLanguageMode>

    init {
        val migratedMode = migrateAndLoadInitialMode()
        _currentMode = MutableStateFlow(migratedMode)
        currentMode = _currentMode.asStateFlow()
    }

    private fun migrateAndLoadInitialMode(): AppLanguageMode {
        if (prefs.contains(KEY_LANGUAGE_MODE)) {
            val raw = prefs.getString(KEY_LANGUAGE_MODE, null)
            return AppLanguageMode.fromCode(raw)
        }

        // Check legacy key
        if (prefs.contains(KEY_LEGACY_LANG)) {
            val legacy = prefs.getString(KEY_LEGACY_LANG, null)?.trim()?.lowercase(Locale.ROOT)
            val migrated = when (legacy) {
                "vi" -> AppLanguageMode.VIETNAMESE
                "en" -> AppLanguageMode.ENGLISH
                else -> AppLanguageMode.SYSTEM
            }
            // Persist migrated key cleanly
            prefs.edit().putString(KEY_LANGUAGE_MODE, migrated.code).apply()
            return migrated
        }

        // New install / no preference: MUST default to SYSTEM (never silently force Vietnamese)
        return AppLanguageMode.SYSTEM
    }

    fun setLanguageMode(mode: AppLanguageMode) {
        _currentMode.value = mode
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.code).apply()

        // Apply to Android 13+ (API 33+) LocaleManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = appContext.getSystemService(LocaleManager::class.java)
                val localeList = when (mode) {
                    AppLanguageMode.SYSTEM -> LocaleList.getEmptyLocaleList()
                    AppLanguageMode.VIETNAMESE -> LocaleList.forLanguageTags("vi")
                    AppLanguageMode.ENGLISH -> LocaleList.forLanguageTags("en")
                }
                localeManager?.applicationLocales = localeList
            } catch (_: Throwable) {
                // Ignore platform manager failure in restricted or test environments
            }
        }

        // Update application resources configuration defensively
        applyConfigurationToResources(appContext, mode)
    }

    fun resolveEffectiveLocale(mode: AppLanguageMode = _currentMode.value): Locale {
        return when (mode) {
            AppLanguageMode.VIETNAMESE -> Locale("vi")
            AppLanguageMode.ENGLISH -> Locale.ENGLISH
            AppLanguageMode.SYSTEM -> resolveSystemLocale()
        }
    }

    private fun resolveSystemLocale(): Locale {
        val systemLocales = Resources.getSystem().configuration.locales
        val primary = if (!systemLocales.isEmpty) systemLocales[0] else Locale.getDefault()
        return if (primary.language.equals("vi", ignoreCase = true)) {
            Locale("vi")
        } else {
            // Default base fallback is English
            Locale.ENGLISH
        }
    }

    fun createLocalizedConfiguration(baseConfig: Configuration, mode: AppLanguageMode = _currentMode.value): Configuration {
        val config = Configuration(baseConfig)
        val targetLocale = resolveEffectiveLocale(mode)
        config.setLocales(LocaleList(targetLocale))
        return config
    }

    private fun applyConfigurationToResources(context: Context, mode: AppLanguageMode) {
        try {
            val resources = context.resources
            val config = createLocalizedConfiguration(resources.configuration, mode)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (_: Throwable) {
            // Ignore in unit test mocks
        }
    }

    companion object {
        const val PREFS_NAME = "app_prefs"
        const val KEY_LANGUAGE_MODE = "app_language_mode"
        const val KEY_LEGACY_LANG = "app_lang"

        @Volatile
        private var instance: AppLanguageManager? = null

        fun getInstance(context: Context): AppLanguageManager {
            return instance ?: synchronized(this) {
                instance ?: AppLanguageManager(context).also { instance = it }
            }
        }
    }
}
