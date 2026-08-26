package com.mipastudio.memostamp.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.roundToInt

class PerforatedStampShape(
    private val notchRatio: Float = StampGeometry.NOTCH_RADIUS_RATIO,
    private val spacingRatio: Float = StampGeometry.NOTCH_SPACING_RATIO
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)

        val r = maxOf(1.5f, minDim * notchRatio)

        val path = Path()

        // TOP Edge - Center-aligned notches
        val topCount = max(3, (w / (minDim * spacingRatio)).roundToInt())
        val topSpacing = w / topCount.toFloat()
        path.moveTo(0f, 0f)
        for (i in 0 until topCount) {
            val cx = topSpacing * (i + 0.5f)
            path.lineTo(cx - r, 0f)
            path.quadraticTo(cx, r * 1.8f, cx + r, 0f)
        }
        path.lineTo(w, 0f)

        // RIGHT Edge - Center-aligned notches
        val rightCount = max(3, (h / (minDim * spacingRatio)).roundToInt())
        val rightSpacing = h / rightCount.toFloat()
        for (i in 0 until rightCount) {
            val cy = rightSpacing * (i + 0.5f)
            path.lineTo(w, cy - r)
            path.quadraticTo(w - r * 1.8f, cy, w, cy + r)
        }
        path.lineTo(w, h)

        // BOTTOM Edge - Center-aligned notches
        for (i in (topCount - 1) downTo 0) {
            val cx = topSpacing * (i + 0.5f)
            path.lineTo(cx + r, h)
            path.quadraticTo(cx, h - r * 1.8f, cx - r, h)
        }
        path.lineTo(0f, h)

        // LEFT Edge - Center-aligned notches
        for (i in (rightCount - 1) downTo 0) {
            val cy = rightSpacing * (i + 0.5f)
            path.lineTo(0f, cy + r)
            path.quadraticTo(r * 1.8f, cy, 0f, cy - r)
        }
        path.lineTo(0f, 0f)

        path.close()

        return Outline.Generic(path)
    }
}
