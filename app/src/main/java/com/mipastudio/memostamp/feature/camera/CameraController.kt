package com.mipastudio.memostamp.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
class CameraController(
    private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    var imageCapture: ImageCapture? = null
        private set

    var isFrontLens: Boolean = false
        private set

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var minZoomRatio: Float by mutableFloatStateOf(1.0f)
        private set

    var maxZoomRatio: Float by mutableFloatStateOf(5.0f)
        private set

    var hasUltraWideCamera: Boolean by mutableStateOf(false)
        private set

    var isUltraWideActive: Boolean by mutableStateOf(false)
        private set

    private var ultraWideCameraInfo: CameraInfo? = null

    fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    fun hasFlash(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        useFrontLens: Boolean = false,
        onCameraBound: () -> Unit = {}
    ) {
        this.isFrontLens = useFrontLens
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                this.cameraProvider = provider

                // Detect back cameras and inspect focal lengths for physical ultra-wide lens
                val backInfos: List<CameraInfo> = provider.availableCameraInfos.filter { info ->
                    try {
                        val c2 = Camera2CameraInfo.from(info)
                        c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    } catch (e: Exception) { false }
                }

                if (backInfos.isNotEmpty()) {
                    val defaultBackInfo = backInfos.firstOrNull()
                    val sortedByFocal = backInfos.sortedBy { info ->
                        try {
                            val c2 = Camera2CameraInfo.from(info)
                            val focals = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                            focals?.minOrNull() ?: 999f
                        } catch (e: Exception) { 999f }
                    }

                    val smallestFocalInfo = sortedByFocal.firstOrNull()
                    val smallestFocal: Float = try {
                        val c2 = smallestFocalInfo?.let { Camera2CameraInfo.from(it) }
                        c2?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 999f
                    } catch (e: Exception) { 999f }

                    val defaultFocal: Float = try {
                        val c2 = defaultBackInfo?.let { Camera2CameraInfo.from(it) }
                        c2?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 999f
                    } catch (e: Exception) { 999f }

                    if (smallestFocal < (defaultFocal * 0.88f) && smallestFocalInfo != null && smallestFocalInfo != defaultBackInfo) {
                        ultraWideCameraInfo = smallestFocalInfo
                        hasUltraWideCamera = true
                    }
                }

                provider.unbindAll()

                val targetLens = try {
                    if (useFrontLens && hasFrontCamera()) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (isUltraWideActive && ultraWideCameraInfo != null) {
                        val uwInfo = ultraWideCameraInfo!!
                        CameraSelector.Builder().addCameraFilter { list -> list.filter { it == uwInfo } }.build()
                    } else if (hasBackCamera()) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (hasFrontCamera()) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                } catch (e: Exception) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()
                this.imageCapture = capture

                try {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        targetLens,
                        preview,
                        capture
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
                    if (zoomState != null) {
                        minZoomRatio = zoomState.minZoomRatio
                        maxZoomRatio = zoomState.maxZoomRatio
                        if (minZoomRatio < 0.9f) {
                            hasUltraWideCamera = true
                        }
                    }
                }

                // Memo Natural Exposure adjustment (-0.2 EV calculated dynamically based on step)
                camera?.cameraInfo?.let { info ->
                    if (!info.hasFlashUnit()) {
                        flashMode = ImageCapture.FLASH_MODE_OFF
                        capture.flashMode = ImageCapture.FLASH_MODE_OFF
                    }
                    val exposure = info.exposureState
                    if (exposure.isExposureCompensationSupported) {
                        val step = exposure.exposureCompensationStep.toFloat()
                        if (step > 0f) {
                            val targetEv = -0.2f
                            val targetIndex = (targetEv / step).roundToInt().coerceIn(
                                exposure.exposureCompensationRange.lower,
                                exposure.exposureCompensationRange.upper
                            )
                            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
                        }
                    }
                }

                onCameraBound()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleCameraLens(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraBound: () -> Unit = {}
    ) {
        if (isFrontLens && !hasBackCamera()) return
        if (!isFrontLens && !hasFrontCamera()) return
        isUltraWideActive = false
        bindCamera(lifecycleOwner, previewView, !isFrontLens, onCameraBound)
    }

    fun toggleFlash(): Int {
        if (!hasFlash()) return ImageCapture.FLASH_MODE_OFF
        val nextMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        flashMode = nextMode
        imageCapture?.flashMode = nextMode
        return nextMode
    }

    fun setZoomRatio(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio.coerceIn(minZoomRatio, maxZoomRatio))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setZoomPreset(
        ratio: Float,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        if (ratio < 1.0f) {
            if (minZoomRatio <= ratio && !isUltraWideActive) {
                setZoomRatio(ratio)
            } else if (ultraWideCameraInfo != null) {
                if (!isUltraWideActive) {
                    isUltraWideActive = true
                    bindCamera(lifecycleOwner, previewView, useFrontLens = false)
                }
            } else {
                setZoomRatio(ratio)
            }
        } else {
            if (isUltraWideActive) {
                isUltraWideActive = false
                bindCamera(lifecycleOwner, previewView, useFrontLens = false) {
                    setZoomRatio(ratio)
                }
            } else {
                setZoomRatio(ratio)
            }
        }
    }

    suspend fun capturePhotoToFile(outputFile: File): File = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("ImageCapture is not bound"))
            return@suspendCancellableCoroutine
        }

        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        capture.takePicture(
            outputOptions,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    suspend fun capturePhoto(): Bitmap = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("ImageCapture is not bound"))
            return@suspendCancellableCoroutine
        }

        val photoFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val exifInterface = androidx.exifinterface.media.ExifInterface(photoFile.absolutePath)
                        val orientation = exifInterface.getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                        )
                        val rotationDegrees = when (orientation) {
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            else -> 0
                        }

                        var finalBitmap = bitmap
                        if (rotationDegrees != 0 || isFrontLens) {
                            val matrix = Matrix()
                            if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
                            if (isFrontLens) matrix.postScale(-1f, 1f)
                            finalBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                            if (finalBitmap != bitmap && !bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                        }

                        continuation.resume(finalBitmap)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        if (photoFile.exists()) {
                            photoFile.delete()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (photoFile.exists()) {
                        photoFile.delete()
                    }
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        cameraProvider?.unbindAll()
        if (!captureExecutor.isShutdown) {
            captureExecutor.shutdown()
        }
    }
}
