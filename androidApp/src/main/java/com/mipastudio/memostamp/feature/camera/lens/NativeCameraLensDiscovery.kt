package com.mipastudio.memostamp.feature.camera.lens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.SizeF
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Represents an authoritative native physical optical lens preset discovered from device hardware.
 * Never hardcodes phone models or synthetic digital zoom steps.
 */
data class OpticalLens(
    val factor: Float,
    val label: String,
    val physicalCameraId: String? = null
)

object NativeCameraLensDiscovery {

    private const val STANDARD_35MM_DIAGONAL = 43.27f
    private const val REFERENCE_WIDE_FOCAL_35MM = 26.0f

    @OptIn(ExperimentalCamera2Interop::class)
    fun discoverOpticalLenses(
        cameraInfo: CameraInfo?,
        context: Context,
        minZoomRatio: Float = 1.0f,
        maxZoomRatio: Float = 5.0f
    ): List<OpticalLens> {
        if (cameraInfo == null) {
            return listOf(OpticalLens(1.0f, "1x"))
        }

        try {
            val c2Info = Camera2CameraInfo.from(cameraInfo)
            val facing = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)

            // Front cameras are authoritative single-lens fixed hardware in smartphones
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return listOf(OpticalLens(1.0f, "1x"))
            }

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return fallbackLenses(minZoomRatio, maxZoomRatio)

            val logicalCharacteristics = try {
                cameraManager.getCameraCharacteristics(c2Info.cameraId)
            } catch (e: Exception) {
                null
            } ?: return fallbackLenses(minZoomRatio, maxZoomRatio)

            val capabilities = logicalCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val isLogicalMultiCamera = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

            val discoveredFactors = mutableListOf<Float>()

            if (isLogicalMultiCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val physicalCameraIds: Set<String> = logicalCharacteristics.physicalCameraIds
                val validPhysicalLenses = mutableListOf<PhysicalLensInfo>()

                for (pId in physicalCameraIds) {
                    try {
                        val pChars = cameraManager.getCameraCharacteristics(pId)
                        val pCaps = pChars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()

                        // Filter out depth / non-backward compatible auxiliary sensors
                        val isBackwardCompatible = pCaps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
                        if (!isBackwardCompatible) continue

                        val focalLengths = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val sensorSize = pChars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

                        if (focalLengths == null || focalLengths.isEmpty() || sensorSize == null || sensorSize.width <= 0f || sensorSize.height <= 0f) {
                            continue
                        }

                        val focal = focalLengths.minOrNull() ?: continue
                        if (focal <= 0f) continue

                        val diagonal = sqrt((sensorSize.width * sensorSize.width) + (sensorSize.height * sensorSize.height))
                        val focal35mm = if (diagonal > 0f) focal * (STANDARD_35MM_DIAGONAL / diagonal) else focal * 6.0f

                        validPhysicalLenses.add(
                            PhysicalLensInfo(
                                id = pId,
                                focalLength = focal,
                                sensorSize = sensorSize,
                                focal35mm = focal35mm
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore unreadable physical cameras
                    }
                }

                if (validPhysicalLenses.isNotEmpty()) {
                    // Identify the primary wide lens (closest to standard 24-28mm focal length)
                    val primaryWide = validPhysicalLenses.minByOrNull { abs(it.focal35mm - REFERENCE_WIDE_FOCAL_35MM) }
                        ?: validPhysicalLenses.first()

                    val baseFocal35 = primaryWide.focal35mm

                    for (lens in validPhysicalLenses) {
                        val rawFactor = lens.focal35mm / baseFocal35
                        val normalized = normalizeOpticalFactor(rawFactor)
                        if (normalized in (minZoomRatio - 0.08f)..(maxZoomRatio + 0.08f)) {
                            discoveredFactors.add(normalized)
                        }
                    }
                }
            }

            // If multi-camera inspection was unavailable or produced no lenses, check CameraX live zoom range
            if (discoveredFactors.isEmpty()) {
                return fallbackLenses(minZoomRatio, maxZoomRatio)
            }

            // Always ensure 1.0x wide is present if within zoom range
            if (1.0f in minZoomRatio..maxZoomRatio && !discoveredFactors.any { abs(it - 1.0f) < 0.1f }) {
                discoveredFactors.add(1.0f)
            }

            return discoveredFactors
                .distinct()
                .sorted()
                .filter { it in minZoomRatio..maxZoomRatio || abs(it - minZoomRatio) < 0.08f }
                .map { factor ->
                    OpticalLens(
                        factor = factor,
                        label = formatLensLabel(factor)
                    )
                }
        } catch (e: Exception) {
            return fallbackLenses(minZoomRatio, maxZoomRatio)
        }
    }

    /**
     * Derives native lens positions when Camera2 physical multi-camera IDs are not directly exposed
     * by the vendor HAL (e.g. virtual devices, single sensors, or custom vendor bridges).
     */
    fun fallbackLenses(minZoomRatio: Float, maxZoomRatio: Float): List<OpticalLens> {
        val result = mutableListOf<Float>()
        if (minZoomRatio < 0.85f) {
            val uwFactor = (minZoomRatio * 10f).roundToInt() / 10f
            result.add(uwFactor)
        }
        if (1.0f in minZoomRatio..maxZoomRatio) {
            result.add(1.0f)
        }
        if (result.isEmpty()) {
            result.add(1.0f)
        }
        return result.distinct().sorted().map { factor ->
            OpticalLens(
                factor = factor,
                label = formatLensLabel(factor)
            )
        }
    }

    /**
     * Normalizes raw physical FOV / focal length ratio into real native optical lens steps:
     * e.g. 0.5x, 0.6x, 1x, 2.5x, 3x, 5x.
     */
    fun normalizeOpticalFactor(rawFactor: Float): Float {
        return when {
            abs(rawFactor - 1.0f) < 0.15f -> 1.0f
            abs(rawFactor - 0.5f) < 0.08f -> 0.5f
            abs(rawFactor - 0.6f) < 0.08f -> 0.6f
            abs(rawFactor - 2.0f) < 0.15f -> 2.0f
            abs(rawFactor - 2.5f) < 0.15f -> 2.5f
            abs(rawFactor - 3.0f) < 0.18f -> 3.0f
            abs(rawFactor - 4.0f) < 0.20f -> 4.0f
            abs(rawFactor - 5.0f) < 0.25f -> 5.0f
            abs(rawFactor - 10.0f) < 0.40f -> 10.0f
            else -> (rawFactor * 10f).roundToInt() / 10f
        }
    }

    fun formatLensLabel(factor: Float): String {
        return if (factor % 1f == 0f) {
            "${factor.toInt()}x"
        } else {
            "${(factor * 10f).roundToInt() / 10f}x"
        }
    }

    private data class PhysicalLensInfo(
        val id: String,
        val focalLength: Float,
        val sensorSize: SizeF,
        val focal35mm: Float
    )
}
