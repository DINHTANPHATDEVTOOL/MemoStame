package com.mipastudio.memostamp.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.core.processor.CameraPreset
import com.mipastudio.memostamp.domain.model.StampDraft
import com.mipastudio.memostamp.feature.camera.renderer.StampCaptureRect
import com.mipastudio.memostamp.feature.camera.renderer.StampCropper
import com.mipastudio.memostamp.feature.camera.renderer.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class CaptureState {
    READY,          // Initial state: live preview + active controls
    CAPTURING,      // Taking JPEG picture
    PRESSING,       // Mold pressing down animation + Haptic + Flash
    REVEALING,      // Stamp pops out from mold (600ms)
    ADDING_NOTE,    // Ready to enter note/title/mood/location
    SAVING          // Writing to Room DB
}

class CameraViewModel : ViewModel() {

    private val _captureState = MutableStateFlow(CaptureState.READY)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _capturedDraft = MutableStateFlow<StampDraft?>(null)
    val capturedDraft: StateFlow<StampDraft?> = _capturedDraft.asStateFlow()

    private val _capturedDraftId = MutableStateFlow<String?>(null)
    val capturedDraftId: StateFlow<String?> = _capturedDraftId.asStateFlow()

    private val _frozenBitmap = MutableStateFlow<Bitmap?>(null)
    val frozenBitmap: StateFlow<Bitmap?> = _frozenBitmap.asStateFlow()

    private val _flashEvent = MutableStateFlow(false)
    val flashEvent: StateFlow<Boolean> = _flashEvent.asStateFlow()

    private val _activeFilterSpec = MutableStateFlow(com.mipastudio.memostamp.core.processor.FilterPresets.FILM_35MM)
    val activeFilterSpec: StateFlow<com.mipastudio.memostamp.core.processor.CameraFilterSpec> = _activeFilterSpec.asStateFlow()

    fun selectFilterSpec(spec: com.mipastudio.memostamp.core.processor.CameraFilterSpec) {
        _activeFilterSpec.value = spec
    }

    fun updateFilterIntensity(intensity: Float) {
        _activeFilterSpec.value = _activeFilterSpec.value.copy(intensity = intensity)
    }

    fun updateCustomFilterTune(
        exposure: Float = _activeFilterSpec.value.exposure,
        contrast: Float = _activeFilterSpec.value.contrast,
        saturation: Float = _activeFilterSpec.value.saturation,
        warmth: Float = _activeFilterSpec.value.warmth,
        fade: Float = _activeFilterSpec.value.fade,
        grain: Float = _activeFilterSpec.value.grain,
        vignette: Float = _activeFilterSpec.value.vignette
    ) {
        _activeFilterSpec.value = _activeFilterSpec.value.copy(
            id = "custom_" + System.currentTimeMillis(),
            name = "Custom",
            category = com.mipastudio.memostamp.core.processor.FilterCategory.CUSTOM,
            exposure = exposure,
            contrast = contrast,
            saturation = saturation,
            warmth = warmth,
            fade = fade,
            grain = grain,
            vignette = vignette
        )
    }

    fun onCaptureClick(
        context: Context,
        cameraController: CameraController,
        haptic: HapticFeedback?,
        openingRect: androidx.compose.ui.geometry.Rect,
        screenWidth: Float,
        screenHeight: Float,
        previewSnapshot: Bitmap? = null
    ) {
        if (_captureState.value != CaptureState.READY) return

        // 1. Freeze visual preview & start press animation IMMEDIATELY (0ms UI latency!)
        _frozenBitmap.value = previewSnapshot
        _captureState.value = CaptureState.PRESSING

        viewModelScope.launch {
            try {
                // Haptic feedback & flash timing in sync with press animation (180ms impact)
                delay(180)
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                _flashEvent.value = true
                delay(70)
                _flashEvent.value = false

                // 2. Perform file capture & processing off the main UI thread
                val currentFilter = _activeFilterSpec.value
                val draft = withContext(Dispatchers.IO) {
                    val rawFile = File(context.filesDir, "raw_${System.currentTimeMillis()}.jpg")
                    cameraController.capturePhotoToFile(rawFile)

                    // Safely decode sampled bitmap for rendering without OOM
                    val rawBitmap = SafeBitmapDecoder.decodeSampledBitmap(
                        context = context,
                        uriOrPath = rawFile.absolutePath,
                        reqWidth = 1500,
                        reqHeight = 2000
                    ) ?: throw IllegalStateException("Failed to decode captured photo")

                    val croppedBitmap = StampCropper.cropToStampRect(
                        sourceBitmap = rawBitmap,
                        openingRect = openingRect,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    )

                    val croppedFile = File(context.filesDir, "cropped_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(croppedFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }

                    val renderedFile = StampRenderer.renderStampToPng(
                        context = context,
                        croppedBitmap = croppedBitmap,
                        filterSpec = currentFilter
                    )

                    if (croppedBitmap != rawBitmap && !croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                    if (!rawBitmap.isRecycled) {
                        rawBitmap.recycle()
                    }

                    var capturedLocation: String? = null
                    com.mipastudio.memostamp.core.location.LocationHelper.fetchCurrentLocation(context) { loc ->
                        capturedLocation = loc
                    }

                    StampDraft(
                        originalImagePath = rawFile.absolutePath,
                        croppedImagePath = croppedFile.absolutePath,
                        renderedImagePath = renderedFile.absolutePath,
                        memoryDate = System.currentTimeMillis(),
                        location = capturedLocation,
                        filterId = currentFilter.id,
                        filterIntensity = currentFilter.intensity,
                        filterSpecJson = currentFilter.toJson()
                    )
                }

                val draftId = com.mipastudio.memostamp.data.repository.StampRepository.getInstance(context).saveDraft(draft)
                _capturedDraft.value = draft
                _capturedDraftId.value = draftId

                // 3. Reveal animation phase
                _captureState.value = CaptureState.REVEALING
                delay(550)

                // 4. Clear frozen preview bitmap & navigate to Note screen
                _frozenBitmap.value = null
                _captureState.value = CaptureState.ADDING_NOTE

            } catch (e: Exception) {
                e.printStackTrace()
                _frozenBitmap.value = null
                _captureState.value = CaptureState.READY
            }
        }
    }

    fun processGalleryUri(
        context: Context,
        uri: Uri,
        haptic: HapticFeedback?,
        openingRect: androidx.compose.ui.geometry.Rect,
        screenWidth: Float,
        screenHeight: Float
    ) {
        if (_captureState.value != CaptureState.READY) return

        viewModelScope.launch {
            try {
                val rawBitmap = withContext(Dispatchers.IO) {
                    SafeBitmapDecoder.decodeSampledBitmap(context, uri.toString())
                } ?: throw IllegalStateException("Unable to decode bitmap from URI: $uri")

                _frozenBitmap.value = rawBitmap
                _captureState.value = CaptureState.PRESSING
                delay(300)
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                _flashEvent.value = true
                delay(100)
                _flashEvent.value = false

                val currentFilter = _activeFilterSpec.value
                val draft = withContext(Dispatchers.IO) {
                    val croppedBitmap = StampCropper.cropToStampRect(
                        sourceBitmap = rawBitmap,
                        openingRect = openingRect,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight
                    )

                    val croppedFile = File(context.filesDir, "cropped_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(croppedFile).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }

                    val renderedFile = StampRenderer.renderStampToPng(
                        context = context,
                        croppedBitmap = croppedBitmap,
                        filterSpec = currentFilter
                    )

                    val rawFile = File(context.filesDir, "raw_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(rawFile).use { out ->
                        rawBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    if (croppedBitmap != rawBitmap && !croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }

                    var galleryLocation: String? = null
                    com.mipastudio.memostamp.core.location.LocationHelper.fetchCurrentLocation(context) { loc ->
                        galleryLocation = loc
                    }

                    StampDraft(
                        originalImagePath = rawFile.absolutePath,
                        croppedImagePath = croppedFile.absolutePath,
                        renderedImagePath = renderedFile.absolutePath,
                        memoryDate = System.currentTimeMillis(),
                        location = galleryLocation,
                        filterId = currentFilter.id,
                        filterIntensity = currentFilter.intensity,
                        filterSpecJson = currentFilter.toJson()
                    )
                }

                val draftId = com.mipastudio.memostamp.data.repository.StampRepository.getInstance(context).saveDraft(draft)
                _capturedDraft.value = draft
                _capturedDraftId.value = draftId
                _captureState.value = CaptureState.REVEALING
                delay(800)

                _frozenBitmap.value?.let {
                    if (!it.isRecycled) it.recycle()
                }
                _frozenBitmap.value = null

                _captureState.value = CaptureState.ADDING_NOTE
            } catch (e: Exception) {
                e.printStackTrace()
                _frozenBitmap.value?.let {
                    if (!it.isRecycled) it.recycle()
                }
                _frozenBitmap.value = null
                _captureState.value = CaptureState.READY
            }
        }
    }

    fun resetState() {
        _captureState.value = CaptureState.READY
        _capturedDraft.value = null
        _capturedDraftId.value = null
        _frozenBitmap.value?.let {
            if (!it.isRecycled) it.recycle()
        }
        _frozenBitmap.value = null
    }
}
