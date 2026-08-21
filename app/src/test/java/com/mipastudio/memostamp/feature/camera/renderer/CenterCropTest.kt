package com.mipastudio.memostamp.feature.camera.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class CenterCropTest {

    @Test
    fun centerCrop_landscapeImage_cropsSides() {
        val cropRect = StampLayoutCalculator.calculateCenterCropBounds(
            bitmapWidth = 4000,
            bitmapHeight = 3000, // 4:3 landscape
            targetWidth = 1200,
            targetHeight = 1500  // 4:5 target portrait
        )
        assertEquals(0, cropRect.top)
        assertEquals(3000, cropRect.bottom)
        assertEquals(3000, cropRect.height)
        assertEquals(2400, cropRect.width)
        assertEquals(800, cropRect.left)
    }

    @Test
    fun centerCrop_tallPortraitImage_cropsTopAndBottom() {
        val cropRect = StampLayoutCalculator.calculateCenterCropBounds(
            bitmapWidth = 1080,
            bitmapHeight = 2400, // 9:20 tall portrait
            targetWidth = 1200,
            targetHeight = 1500  // 4:5 target
        )
        assertEquals(0, cropRect.left)
        assertEquals(1080, cropRect.right)
        assertEquals(1080, cropRect.width)
        assertEquals(1350, cropRect.height)
        assertEquals(525, cropRect.top)
    }
}
