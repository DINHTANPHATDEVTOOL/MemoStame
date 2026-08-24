package com.mipastudio.memostamp.core.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.google.gson.Gson
import java.util.Random

enum class FilterCategory(val displayName: String) {
    EVERYDAY("Everyday"),
    FILM("Film / Nostalgia"),
    MOOD("Mood / Creative"),
    CUSTOM("Custom")
}

data class CameraFilterSpec(
    val id: String = "original",
    val name: String = "Original",
    val category: FilterCategory = FilterCategory.EVERYDAY,
    val exposure: Float = 0f,       // -0.5f .. +0.5f
    val contrast: Float = 0f,       // -0.5f .. +0.5f
    val saturation: Float = 0f,     // -1.0f .. +1.0f
    val warmth: Float = 0f,         // -0.5f .. +0.5f
    val tint: Float = 0f,           // -0.5f .. +0.5f
    val highlights: Float = 0f,     // -0.5f .. +0.5f
    val shadows: Float = 0f,        // -0.5f .. +0.5f
    val fade: Float = 0f,           // 0.0f .. 0.5f
    val grain: Float = 0f,          // 0.0f .. 0.5f
    val vignette: Float = 0f,       // 0.0f .. 0.5f
    val sharpen: Float = 0f,        // 0.0f .. 0.5f
    val softBlur: Float = 0f,       // 0.0f .. 0.5f
    val intensity: Float = 1.0f     // 0.0f .. 1.0f
) {
    fun toColorMatrixArray(): FloatArray {
        if (id == "original") {
            return floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        val effInt = intensity.coerceIn(0f, 1f)
        val exp = exposure * effInt
        val con = contrast * effInt
        val sat = saturation * effInt
        val wrm = warmth * effInt
        val tnt = tint * effInt
        val fde = fade * effInt

        val contrastScale = 1.0f + con
        val contrastTranslate = (-0.5f * (contrastScale - 1.0f)) * 255f

        val satMult = 1.0f + sat
        val invSat = 1.0f - satMult
        val rLum = 0.213f * invSat
        val gLum = 0.715f * invSat
        val bLum = 0.072f * invSat

        val rBase = rLum + satMult
        val gBase = gLum + satMult
        val bBase = bLum + satMult

        val rGain = (1.0f + exp + wrm * 0.15f) * contrastScale
        val gGain = (1.0f + exp - tnt * 0.08f) * contrastScale
        val bGain = (1.0f + exp - wrm * 0.18f + tnt * 0.08f) * contrastScale

        val rOff = (exp * 60f + wrm * 25f + fde * 35f) + contrastTranslate
        val gOff = (exp * 60f + fde * 30f) + contrastTranslate
        val bOff = (exp * 60f - wrm * 20f + fde * 40f) + contrastTranslate

        return floatArrayOf(
            rBase * rGain, gLum * rGain, bLum * rGain, 0f, rOff,
            rLum * gGain, gBase * gGain, bLum * gGain, 0f, gOff,
            rLum * bGain, gLum * bGain, bBase * bGain, 0f, bOff,
            0f, 0f, 0f, 1f, 0f
        )
    }

    fun toComposeColorMatrix(): androidx.compose.ui.graphics.ColorMatrix {
        return androidx.compose.ui.graphics.ColorMatrix(toColorMatrixArray())
    }

    fun toAndroidColorMatrix(): android.graphics.ColorMatrix {
        return android.graphics.ColorMatrix(toColorMatrixArray())
    }

    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String?): CameraFilterSpec? {
            if (json.isNullOrBlank()) return null
            return try {
                Gson().fromJson(json, CameraFilterSpec::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

object FilterPresets {
    val ORIGINAL = CameraFilterSpec("original", "Original", FilterCategory.EVERYDAY)

    val NATURAL = CameraFilterSpec(
        id = "natural",
        name = "Natural",
        category = FilterCategory.EVERYDAY,
        exposure = 0.00f, contrast = 0.04f, saturation = 0.03f, warmth = 0.01f,
        highlights = -0.05f, shadows = 0.04f, fade = 0.00f, grain = 0.00f, vignette = 0.00f
    )

    val WARM = CameraFilterSpec(
        id = "warm",
        name = "Warm",
        category = FilterCategory.EVERYDAY,
        exposure = 0.05f, contrast = 0.03f, saturation = 0.06f, warmth = 0.10f,
        highlights = -0.06f, shadows = 0.05f, fade = 0.02f, vignette = 0.02f
    )

    val SOFT = CameraFilterSpec(
        id = "soft",
        name = "Soft",
        category = FilterCategory.EVERYDAY,
        exposure = 0.08f, contrast = -0.04f, saturation = -0.02f, warmth = 0.03f,
        highlights = -0.10f, shadows = 0.08f, fade = 0.06f, softBlur = 0.02f, vignette = 0.01f
    )

    val BRIGHT = CameraFilterSpec(
        id = "bright",
        name = "Bright",
        category = FilterCategory.EVERYDAY,
        exposure = 0.12f, contrast = 0.02f, saturation = 0.05f, warmth = 0.02f,
        highlights = -0.08f, shadows = 0.06f
    )

    val FILM_35MM = CameraFilterSpec(
        id = "film_35mm",
        name = "Film 35mm",
        category = FilterCategory.FILM,
        exposure = -0.01f, contrast = 0.06f, saturation = -0.02f, warmth = 0.04f,
        highlights = -0.10f, shadows = 0.06f, fade = 0.08f, grain = 0.18f, vignette = 0.08f, sharpen = 0.02f
    )

    val GRAINY_FILM = CameraFilterSpec(
        id = "grainy_film",
        name = "Grainy Film",
        category = FilterCategory.FILM,
        exposure = -0.03f, contrast = 0.08f, saturation = -0.06f, warmth = 0.02f,
        highlights = -0.12f, shadows = 0.05f, fade = 0.10f, grain = 0.30f, vignette = 0.10f
    )

    val VINTAGE_FADE = CameraFilterSpec(
        id = "vintage_fade",
        name = "Vintage Fade",
        category = FilterCategory.FILM,
        exposure = 0.02f, contrast = -0.03f, saturation = -0.10f, warmth = 0.08f,
        highlights = -0.08f, shadows = 0.10f, fade = 0.18f, grain = 0.14f, vignette = 0.06f
    )

    val MONO_FILM = CameraFilterSpec(
        id = "mono_film",
        name = "Mono Film",
        category = FilterCategory.FILM,
        exposure = 0.00f, contrast = 0.12f, saturation = -1.00f, warmth = 0.00f,
        highlights = -0.10f, shadows = 0.08f, fade = 0.08f, grain = 0.20f, vignette = 0.10f
    )

    val CAFE_COZY = CameraFilterSpec(
        id = "cafe_cozy",
        name = "Cafe Cozy",
        category = FilterCategory.MOOD,
        exposure = -0.02f, contrast = 0.05f, saturation = -0.02f, warmth = 0.12f,
        tint = 0.02f, fade = 0.05f, grain = 0.10f, vignette = 0.08f
    )

    val DREAMY = CameraFilterSpec(
        id = "dreamy",
        name = "Dreamy",
        category = FilterCategory.MOOD,
        exposure = 0.10f, contrast = -0.08f, saturation = 0.02f, warmth = 0.03f,
        highlights = -0.12f, shadows = 0.10f, fade = 0.05f, softBlur = 0.03f
    )

    val AIRMAIL_BLUE = CameraFilterSpec(
        id = "airmail_blue",
        name = "Airmail Blue",
        category = FilterCategory.MOOD,
        exposure = 0.02f, contrast = 0.03f, saturation = -0.02f, warmth = -0.05f,
        tint = -0.02f, fade = 0.05f, grain = 0.08f, vignette = 0.05f
    )

    val ALL = listOf(
        ORIGINAL, NATURAL, WARM, SOFT, BRIGHT,
        FILM_35MM, GRAINY_FILM, VINTAGE_FADE, MONO_FILM,
        CAFE_COZY, DREAMY, AIRMAIL_BLUE
    )

    fun getById(id: String?): CameraFilterSpec {
        return ALL.find { it.id == id } ?: ORIGINAL
    }
}
