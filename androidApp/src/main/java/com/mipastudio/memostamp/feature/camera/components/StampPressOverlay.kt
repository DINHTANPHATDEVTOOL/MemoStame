package com.mipastudio.memostamp.feature.camera.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mipastudio.memostamp.feature.camera.renderer.StampCaptureRect
import com.mipastudio.memostamp.feature.camera.renderer.StampLayoutCalculator

/**
 * Custom Compose Canvas rendering the authentic centered Stamp Press Mold.
 * Renders the compact stamp die mold in the middle of screen with a dark vignette surrounding it.
 */
@Composable
fun StampPressOverlay(
    modifier: Modifier = Modifier,
    pressOffsetPx: Float = 0f,
    onCalculatedRect: ((StampCaptureRect) -> Unit)? = null,
    isImpactState: Boolean = false
) {
    val context = LocalContext.current
    val moldImage = remember(context) {
        BitmapFactory.decodeResource(context.resources, com.mipastudio.memostamp.R.drawable.stamp_press_mold)?.asImageBitmap()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val openingRect = StampLayoutCalculator.calculateOpeningRect(width, height, pressOffsetPx)

        val moldWidth = width * StampLayoutCalculator.MOLD_WIDTH_RATIO
        val moldHeight = moldWidth * StampLayoutCalculator.MOLD_ASPECT_RATIO
        val moldLeft = (width - moldWidth) / 2f
        val moldTop = (height - moldHeight) / 2f + pressOffsetPx

        // 1. Dark translucent vignette mask outside capture opening
        val maskPath = Path().apply {
            addRect(Rect(0f, 0f, width, height))
        }
        val holePath = Path().apply {
            addRect(openingRect)
        }

        val combinedMask = Path.combine(PathOperation.Difference, maskPath, holePath)
        drawPath(
            path = combinedMask,
            color = Color.Black.copy(alpha = 0.5f)
        )

        // 2. Draw authentic compact mold image overlay centered in middle of screen
        if (moldImage != null) {
            drawImage(
                image = moldImage,
                dstOffset = IntOffset(moldLeft.toInt(), moldTop.toInt()),
                dstSize = IntSize(moldWidth.toInt(), moldHeight.toInt())
            )
        }

        // 3. Highlight border during impact press
        if (isImpactState) {
            drawRect(
                color = Color(0xFFE63946),
                topLeft = Offset(openingRect.left, openingRect.top),
                size = Size(openingRect.width, openingRect.height),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}



