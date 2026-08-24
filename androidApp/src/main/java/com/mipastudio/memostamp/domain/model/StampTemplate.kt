package com.mipastudio.memostamp.domain.model

data class StampTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val backgroundAsset: String? = null,
    val frameAsset: String? = null,
    val defaultElements: List<StampElement> = emptyList(),
    val photoInsetLeft: Float = 0f,
    val photoInsetTop: Float = 0f,
    val photoInsetRight: Float = 1f,
    val photoInsetBottom: Float = 1f
)

object StampTemplates {
    val PHOTO_STAMP = StampTemplate(
        id = "classic_post",
        name = "Classic Post",
        description = "Authentic die-cut postage stamp edge with clear memory canvas",
        iconEmoji = "📮"
    )

    val AIRMAIL = StampTemplate(
        id = "airmail",
        name = "Air Mail ✈",
        description = "Retro airmail striped border with vintage flight markings",
        iconEmoji = "✈",
        defaultElements = listOf(
            StampElement("air_1", "badge", "VIA AIR MAIL", 0.5f, 0.88f, colorHex = "#1D4ED8"),
            StampElement("air_2", "text", "PAR AVION", 0.5f, 0.94f, scale = 0.8f, colorHex = "#DC2626")
        )
    )

    val POLAROID = StampTemplate(
        id = "polaroid",
        name = "Polaroid 📷",
        description = "Classic instant film frame with wide bottom caption space",
        iconEmoji = "📷",
        photoInsetLeft = 0.05f,
        photoInsetTop = 0.05f,
        photoInsetRight = 0.95f,
        photoInsetBottom = 0.80f
    )

    val VINTAGE = StampTemplate(
        id = "vintage",
        name = "Vintage 📜",
        description = "Aged parchment paper border with sepia tone markings",
        iconEmoji = "📜",
        defaultElements = listOf(
            StampElement("v_1", "text", "OFFICIAL MEMORY", 0.5f, 0.85f, colorHex = "#78350F")
        )
    )

    val PASSPORT = StampTemplate(
        id = "passport",
        name = "Passport 🎓",
        description = "Travel visa stamp frame with official entry seal",
        iconEmoji = "🎓",
        defaultElements = listOf(
            StampElement("p_1", "badge", "IMMIGRATION • PASSED", 0.5f, 0.85f, colorHex = "#047857")
        )
    )

    val SAKURA = StampTemplate(
        id = "sakura",
        name = "Sakura ✿",
        description = "Soft spring cherry blossom frame and delicate pink stamp trim",
        iconEmoji = "✿",
        defaultElements = listOf(
            StampElement("s_1", "sticker", "✿", 0.15f, 0.15f, rotation = 15f, colorHex = "#EC4899"),
            StampElement("s_2", "sticker", "🌸", 0.85f, 0.85f, rotation = -15f, colorHex = "#EC4899")
        )
    )

    val ALL = listOf(PHOTO_STAMP, AIRMAIL, POLAROID, VINTAGE, PASSPORT, SAKURA)

    fun getById(id: String): StampTemplate {
        return ALL.firstOrNull { it.id == id } ?: PHOTO_STAMP
    }
}
