package com.mipastudio.memostamp.core.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val MemoStampColorScheme = lightColorScheme(
    primary = AccentRed,
    onPrimary = SurfaceWhite,
    primaryContainer = AccentRedSoft,
    onPrimaryContainer = PrimaryText,
    secondary = AccentBlue,
    onSecondary = SurfaceWhite,
    secondaryContainer = AccentBlueSoft,
    onSecondaryContainer = PrimaryText,
    tertiary = SuccessGreen,
    background = WarmPaperBg,
    onBackground = PrimaryText,
    surface = SurfaceWhite,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = SecondaryText,
    outline = UIBorder,
    outlineVariant = UIBorder
)

private val MemoStampShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun MemoStampTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MemoStampColorScheme.background.toArgb()
            window.navigationBarColor = MemoStampColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = MemoStampColorScheme,
        typography = MemoStampTypography,
        shapes = MemoStampShapes,
        content = content
    )
}
