package com.mipastudio.memostamp.feature.camera.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampLayoutCalculatorTest {

    @Test
    fun calculateOpeningRect_returnsCorrectBoundsForStandardScreen() {
        val width = 1080f
        val height = 2400f

        val rect = StampLayoutCalculator.calculateOpeningRect(width, height)

        val expectedMoldWidth = 1080f * 0.72f // 777.6f
        val expectedMoldHeight = expectedMoldWidth * (1159f / 881f) // 1022.923f
        val expectedMoldLeft = (1080f - expectedMoldWidth) / 2f // 151.2f
        val expectedMoldTop = (2400f - expectedMoldHeight) / 2f // 688.538f

        val expectedInnerLeft = expectedMoldLeft + expectedMoldWidth * (228f / 881f)
        val expectedInnerTop = expectedMoldTop + expectedMoldHeight * (316.125f / 1159f)

        assertEquals(expectedInnerLeft, rect.left, 0.1f)
        assertEquals(expectedInnerTop, rect.top, 0.1f)
    }

    @Test
    fun calculateNormalizedCaptureRect_returnsNormalizedCoordinates() {
        val width = 1080f
        val height = 2400f

        val normRect = StampLayoutCalculator.calculateNormalizedCaptureRect(width, height)

        assertTrue(normRect.left in 0f..1f)
        assertTrue(normRect.top in 0f..1f)
        assertTrue(normRect.right in 0f..1f)
        assertTrue(normRect.bottom in 0f..1f)
        assertTrue(normRect.left < normRect.right)
        assertTrue(normRect.top < normRect.bottom)
    }
}
