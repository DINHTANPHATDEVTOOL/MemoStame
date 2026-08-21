package com.mipastudio.memostamp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampGeometryTest {

    @Test
    fun verifyStampGeometryConstants() {
        assertEquals(1200, StampGeometry.OUTPUT_WIDTH)
        assertEquals(1500, StampGeometry.OUTPUT_HEIGHT)
        assertEquals(0.8f, StampGeometry.ASPECT_RATIO, 0.001f)

        // Check ratio bounds
        assertTrue(StampGeometry.NOTCH_RADIUS_RATIO in 0.01f..0.05f)
        assertTrue(StampGeometry.NOTCH_SPACING_RATIO in 0.05f..0.10f)
        assertTrue(StampGeometry.MOLD_WIDTH_RATIO in 0.5f..0.9f)
    }

    @Test
    fun verifyInnerWindowBounds() {
        assertTrue(StampGeometry.INNER_LEFT_RATIO < StampGeometry.INNER_RIGHT_RATIO)
        assertTrue(StampGeometry.INNER_TOP_RATIO < StampGeometry.INNER_BOTTOM_RATIO)
    }
}
