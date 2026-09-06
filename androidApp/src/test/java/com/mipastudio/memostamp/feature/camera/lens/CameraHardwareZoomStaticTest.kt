package com.mipastudio.memostamp.feature.camera.lens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraHardwareZoomStaticTest {

    @Test
    fun testNormalizeOpticalFactorStandardLenses() {
        // Ultra-wide
        assertEquals(0.5f, NativeCameraLensDiscovery.normalizeOpticalFactor(0.48f))
        assertEquals(0.5f, NativeCameraLensDiscovery.normalizeOpticalFactor(0.52f))
        assertEquals(0.6f, NativeCameraLensDiscovery.normalizeOpticalFactor(0.59f))
        assertEquals(0.6f, NativeCameraLensDiscovery.normalizeOpticalFactor(0.63f))

        // Wide (Primary 1x)
        assertEquals(1.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(0.98f))
        assertEquals(1.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(1.05f))

        // Telephoto
        assertEquals(2.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(1.95f))
        assertEquals(2.5f, NativeCameraLensDiscovery.normalizeOpticalFactor(2.48f))
        assertEquals(3.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(3.08f))
        assertEquals(5.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(4.92f))
        assertEquals(10.0f, NativeCameraLensDiscovery.normalizeOpticalFactor(9.85f))
    }

    @Test
    fun testFormatLensLabel() {
        assertEquals("1x", NativeCameraLensDiscovery.formatLensLabel(1.0f))
        assertEquals("3x", NativeCameraLensDiscovery.formatLensLabel(3.0f))
        assertEquals("5x", NativeCameraLensDiscovery.formatLensLabel(5.0f))
        assertEquals("0.5x", NativeCameraLensDiscovery.formatLensLabel(0.5f))
        assertEquals("0.6x", NativeCameraLensDiscovery.formatLensLabel(0.6f))
        assertEquals("2.5x", NativeCameraLensDiscovery.formatLensLabel(2.5f))
    }

    @Test
    fun testFallbackLensesWhenHardwareMultiCameraNotExposed() {
        // Device with ultra-wide zoom support (e.g. 0.5x)
        val uwLenses = NativeCameraLensDiscovery.fallbackLenses(minZoomRatio = 0.5f, maxZoomRatio = 10.0f)
        assertEquals(2, uwLenses.size)
        assertEquals(0.5f, uwLenses[0].factor)
        assertEquals("0.5x", uwLenses[0].label)
        assertEquals(1.0f, uwLenses[1].factor)
        assertEquals("1x", uwLenses[1].label)

        // Device with 0.6x ultra-wide
        val uw06Lenses = NativeCameraLensDiscovery.fallbackLenses(minZoomRatio = 0.6f, maxZoomRatio = 8.0f)
        assertEquals(2, uw06Lenses.size)
        assertEquals(0.6f, uw06Lenses[0].factor)
        assertEquals("0.6x", uw06Lenses[0].label)
        assertEquals(1.0f, uw06Lenses[1].factor)

        // Standard single lens device (1.0x to 5.0x)
        val singleLenses = NativeCameraLensDiscovery.fallbackLenses(minZoomRatio = 1.0f, maxZoomRatio = 5.0f)
        assertEquals(1, singleLenses.size)
        assertEquals(1.0f, singleLenses[0].factor)
        assertEquals("1x", singleLenses[0].label)
    }

    @Test
    fun testNoFixedUniversalZoomPresetArrayInProductionCode() {
        val rootDir = File("..").canonicalFile
        val cameraControlsFile = File(rootDir, "androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/components/CameraControls.kt")
        assertTrue("CameraControls.kt should exist", cameraControlsFile.exists())

        val content = cameraControlsFile.readText()
        assertFalse("CameraControls must not use fixed listOf(2f, 3f, 5f)", content.contains("listOf(2f, 3f, 5f)"))
        assertFalse("CameraControls must not use fixed universal preset array", content.contains("listOf(1f, 2f, 3f, 5f)"))
    }

    @Test
    fun testNoDeviceModelHardcodingInDiscovery() {
        val rootDir = File("..").canonicalFile
        val discoveryFile = File(rootDir, "androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/lens/NativeCameraLensDiscovery.kt")
        assertTrue("NativeCameraLensDiscovery.kt should exist", discoveryFile.exists())

        val content = discoveryFile.readText()
        assertFalse("NativeCameraLensDiscovery must not check Build.MODEL", content.contains("Build.MODEL"))
        assertFalse("NativeCameraLensDiscovery must not check Build.MANUFACTURER", content.contains("Build.MANUFACTURER"))
        assertFalse("NativeCameraLensDiscovery must not hardcode 'samsung'", content.contains("\"samsung\"", ignoreCase = true))
        assertFalse("NativeCameraLensDiscovery must not hardcode 'pixel'", content.contains("\"pixel\"", ignoreCase = true))
    }
}
