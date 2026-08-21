package com.mipastudio.memostamp.core.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val MemoStampShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MemoStampTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentThemeStyle by ThemeManager.currentTheme.collectAsState()
    val colorScheme = ThemeManager.getColorScheme(currentThemeStyle)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !currentThemeStyle.isDark
                isAppearanceLightNavigationBars = !currentThemeStyle.isDark
            }
        }
    }

    CompositionLocalProvider(LocalAppThemeStyle provides currentThemeStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MemoStampTypography,
            shapes = MemoStampShapes,
            content = content
        )
    }
}

