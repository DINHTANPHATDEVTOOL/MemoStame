package com.mipastudio.memostamp.core.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeStyle(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val previewPrimary: Color,
    val previewBg: Color,
    val isDark: Boolean
) {
    VINTAGE_POSTAL(
        id = "vintage",
        title = "Tem Thư Hoài Cổ",
        subtitle = "Giấy ngả vàng, mực bưu chính & phong thư cổ điển",
        emoji = "📜",
        previewPrimary = Color(0xFFE54B4B),
        previewBg = Color(0xFFF9F6F0),
        isDark = false
    ),
    CHERRY_BLOSSOM(
        id = "cherry",
        title = "Anh Đào Mộng Mơ",
        subtitle = "Tông hồng phớt nhẹ nhàng, tím mộng mơ & hoa hoa",
        emoji = "🌸",
        previewPrimary = Color(0xFFE86A92),
        previewBg = Color(0xFFFFF5F7),
        isDark = false
    ),
    BOTANICAL_SAGE(
        id = "sage",
        title = "Xô Thơm Tự Nhiên",
        subtitle = "Tông xanh lá mộc mạc, giấy linen & đất nung",
        emoji = "🌿",
        previewPrimary = Color(0xFF3B7A57),
        previewBg = Color(0xFFF4F6F2),
        isDark = false
    ),
    MIDNIGHT_VAULT(
        id = "midnight",
        title = "Đêm Huyền Bí Luxury",
        subtitle = "Giao diện tối huyền bí, tím dạ quang & kim loại",
        emoji = "🌙",
        previewPrimary = Color(0xFFA78BFA),
        previewBg = Color(0xFF0F172A),
        isDark = true
    ),
    OCEAN_BREEZE(
        id = "ocean",
        title = "Bưu Chính Biển Xanh",
        subtitle = "Tông xanh biển tươi mát, ngọc bích & bọt sóng",
        emoji = "🌊",
        previewPrimary = Color(0xFF0284C7),
        previewBg = Color(0xFFF0F9FF),
        isDark = false
    )
}

object ThemeManager {
    private const val PREFS_NAME = "memostamp_theme_prefs"
    private const val KEY_THEME_ID = "selected_theme_id"

    private val _currentTheme = MutableStateFlow(AppThemeStyle.VINTAGE_POSTAL)
    val currentTheme: StateFlow<AppThemeStyle> = _currentTheme.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_THEME_ID, AppThemeStyle.VINTAGE_POSTAL.id)
        val matched = AppThemeStyle.entries.find { it.id == savedId } ?: AppThemeStyle.VINTAGE_POSTAL
        _currentTheme.value = matched
    }

    fun setTheme(context: Context, theme: AppThemeStyle) {
        _currentTheme.value = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    fun getColorScheme(theme: AppThemeStyle): ColorScheme {
        return when (theme) {
            AppThemeStyle.VINTAGE_POSTAL -> lightColorScheme(
                primary = Color(0xFFE54B4B),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE8E8),
                onPrimaryContainer = Color(0xFF8C1D1D),
                secondary = Color(0xFF2B4C7E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE3EBF7),
                onSecondaryContainer = Color(0xFF132847),
                tertiary = Color(0xFFD4A359),
                background = Color(0xFFF9F6F0),
                onBackground = Color(0xFF1C1B1F),
                surface = Color(0xFFFFFDF9),
                onSurface = Color(0xFF1C1B1F),
                surfaceVariant = Color(0xFFF0EDE6),
                onSurfaceVariant = Color(0xFF6E6A63),
                outline = Color(0xFFE2DDD5),
                outlineVariant = Color(0xFFE2DDD5)
            )

            AppThemeStyle.CHERRY_BLOSSOM -> lightColorScheme(
                primary = Color(0xFFE86A92),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFEEF3),
                onPrimaryContainer = Color(0xFF871E42),
                secondary = Color(0xFF9C6ADE),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF2E8FF),
                onSecondaryContainer = Color(0xFF4C2280),
                tertiary = Color(0xFFF4A261),
                background = Color(0xFFFFF5F7),
                onBackground = Color(0xFF2B1D28),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF2B1D28),
                surfaceVariant = Color(0xFFF9EBF0),
                onSurfaceVariant = Color(0xFF7A5A6D),
                outline = Color(0xFFEAD2DC),
                outlineVariant = Color(0xFFEAD2DC)
            )

            AppThemeStyle.BOTANICAL_SAGE -> lightColorScheme(
                primary = Color(0xFF3B7A57),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2F0E8),
                onPrimaryContainer = Color(0xFF144026),
                secondary = Color(0xFFC86D51),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFBECE6),
                onSecondaryContainer = Color(0xFF6B2B18),
                tertiary = Color(0xFFD4A359),
                background = Color(0xFFF4F6F2),
                onBackground = Color(0xFF1E2721),
                surface = Color(0xFFFAFCF8),
                onSurface = Color(0xFF1E2721),
                surfaceVariant = Color(0xFFE6EBE2),
                onSurfaceVariant = Color(0xFF58655C),
                outline = Color(0xFFD3DDD5),
                outlineVariant = Color(0xFFD3DDD5)
            )

            AppThemeStyle.MIDNIGHT_VAULT -> darkColorScheme(
                primary = Color(0xFFA78BFA),
                onPrimary = Color(0xFF0F172A),
                primaryContainer = Color(0xFF3B0764),
                onPrimaryContainer = Color(0xFFF3E8FF),
                secondary = Color(0xFF38BDF8),
                onSecondary = Color(0xFF0F172A),
                secondaryContainer = Color(0xFF0C4A6E),
                onSecondaryContainer = Color(0xFFE0F2FE),
                tertiary = Color(0xFFFBBF24),
                background = Color(0xFF0F172A),
                onBackground = Color(0xFFF8FAFC),
                surface = Color(0xFF1E293B),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF334155),
                onSurfaceVariant = Color(0xFFCBD5E1),
                outline = Color(0xFF475569),
                outlineVariant = Color(0xFF475569)
            )

            AppThemeStyle.OCEAN_BREEZE -> lightColorScheme(
                primary = Color(0xFF0284C7),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0F2FE),
                onPrimaryContainer = Color(0xFF0369A1),
                secondary = Color(0xFF0D9488),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCFBF1),
                onSecondaryContainer = Color(0xFF115E59),
                tertiary = Color(0xFFF97316),
                background = Color(0xFFF0F9FF),
                onBackground = Color(0xFF0C4A6E),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0C4A6E),
                surfaceVariant = Color(0xFFE2F1F8),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFBAE6FD),
                outlineVariant = Color(0xFFBAE6FD)
            )
        }
    }
}

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.VINTAGE_POSTAL }
