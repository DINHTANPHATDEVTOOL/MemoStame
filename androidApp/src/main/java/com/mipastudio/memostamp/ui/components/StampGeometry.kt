package com.mipastudio.memostamp.ui.components

object StampGeometry {
    const val OUTPUT_WIDTH: Int = 1200
    const val OUTPUT_HEIGHT: Int = 1500

    const val ASPECT_RATIO: Float = 4f / 5f // 0.8f

    // Perforation tooth & notch ratios
    const val NOTCH_RADIUS_RATIO: Float = 0.025f
    const val NOTCH_SPACING_RATIO: Float = 0.072f

    // Standard stamp mold aspect ratio & width scale
    const val MOLD_WIDTH_RATIO: Float = 0.72f
    const val MOLD_ASPECT_RATIO: Float = 1159f / 881f // ~1.3155f

    // Cửa sổ ảnh trung tâm (Inner window ratios - 0.8 aspect ratio matching 1200x1500)
    const val INNER_LEFT_RATIO: Float = 228f / 881f
    const val INNER_TOP_RATIO: Float = 316.125f / 1159f
    const val INNER_RIGHT_RATIO: Float = 651f / 881f
    const val INNER_BOTTOM_RATIO: Float = 844.875f / 1159f
}
