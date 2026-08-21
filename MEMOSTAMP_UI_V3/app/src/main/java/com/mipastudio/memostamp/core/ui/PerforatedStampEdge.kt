package com.mipastudio.memostamp.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

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

        val r = minOf(w, h) * notchRatio
        val spacing = minOf(w, h) * spacingRatio

        val path = Path()

        // TOP Edge (notches curve inward into image)
        path.moveTo(0f, 0f)
        var x = spacing / 2f
        while (x < w - spacing / 2f) {
            path.lineTo(x - r, 0f)
            path.quadraticTo(
                x,
                r * 1.8f,
                x + r,
                0f
            )
            x += spacing
        }
        path.lineTo(w, 0f)

        // RIGHT Edge
        var y = spacing / 2f
        while (y < h - spacing / 2f) {
            path.lineTo(w, y - r)
            path.quadraticTo(
                w - r * 1.8f,
                y,
                w,
                y + r
            )
            y += spacing
        }
        path.lineTo(w, h)

        // BOTTOM Edge
        x = w - spacing / 2f
        while (x > spacing / 2f) {
            path.lineTo(x + r, h)
            path.quadraticTo(
                x,
                h - r * 1.8f,
                x - r,
                h
            )
            x -= spacing
        }
        path.lineTo(0f, h)

        // LEFT Edge
        y = h - spacing / 2f
        while (y > spacing / 2f) {
            path.lineTo(0f, y + r)
            path.quadraticTo(
                r * 1.8f,
                y,
                0f,
                y - r
            )
            y -= spacing
        }
        path.lineTo(0f, 0f)

        path.close()

        return Outline.Generic(path)
    }
}

