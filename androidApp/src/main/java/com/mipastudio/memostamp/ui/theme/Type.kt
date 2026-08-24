package com.mipastudio.memostamp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Consumer-app UI: clean, robust sans-serif font family
val AppSansFontFamily = FontFamily.SansSerif

// Kept for compatibility with existing screens
val AppDisplayFontFamily = AppSansFontFamily

// Stamp and postcard serif styling
val StampSerifFontFamily = FontFamily.Serif

val MemoStampTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
        color = PrimaryText
    ),
    displayMedium = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = PrimaryText
    ),
    displaySmall = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp,
        color = PrimaryText
    ),
    headlineLarge = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
        color = PrimaryText
    ),
    headlineMedium = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = PrimaryText
    ),
    headlineSmall = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = PrimaryText
    ),
    titleLarge = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = PrimaryText
    ),
    titleMedium = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = PrimaryText
    ),
    titleSmall = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SecondaryText
    ),
    bodyLarge = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = PrimaryText
    ),
    bodyMedium = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = PrimaryText
    ),
    bodySmall = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = SecondaryText
    ),
    labelLarge = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = PrimaryText
    ),
    labelMedium = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = SecondaryText
    ),
    labelSmall = TextStyle(
        fontFamily = AppSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        color = SecondaryText
    )
)
