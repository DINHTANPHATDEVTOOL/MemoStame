package com.mipastudio.memostamp.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.domain.model.StampElement
import com.mipastudio.memostamp.domain.model.StampTemplate
import com.mipastudio.memostamp.domain.model.StampTemplates
import com.mipastudio.memostamp.core.processor.CameraFilterSpec
import com.mipastudio.memostamp.core.processor.CameraPreset
import com.mipastudio.memostamp.core.processor.FilterPresets
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.feature.camera.SafeBitmapDecoder
import com.mipastudio.memostamp.feature.camera.renderer.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class StampDesignSpec(
    val filterSpec: CameraFilterSpec = FilterPresets.FILM_35MM,
    val templateId: String = "classic_post",
    val borderStyle: String = "perforated",
    val elements: List<StampElement> = emptyList()
)

data class StampEditorUiState(
    val stampId: String? = null,
    val sourceImagePath: String = "",
    val filterSpec: CameraFilterSpec = FilterPresets.FILM_35MM,
    val preset: CameraPreset = CameraPreset.NATURAL,
    val templateId: String = "classic_post",
    val borderStyle: String = "perforated",
    val elements: List<StampElement> = emptyList(),
    val selectedElementId: String? = null,
    val undoStack: List<StampDesignSpec> = emptyList(),
    val redoStack: List<StampDesignSpec> = emptyList(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false
)

class StampEditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StampEditorUiState())
    val uiState: StateFlow<StampEditorUiState> = _uiState.asStateFlow()

    private var initialDesignSpec: StampDesignSpec? = null

    fun loadStampData(context: Context, stampId: String?, initialPhotoUrl: String?) {
        viewModelScope.launch {
            if (!stampId.isNullOrBlank()) {
                val repo = StampRepository.getInstance(context)
                val result = repo.getStampById(stampId)
                val entity = result.getOrNull()
                if (entity != null) {
                    val loadedFilterSpec = CameraFilterSpec.fromJson(entity.filterSpecJson)
                        ?: FilterPresets.getById(entity.filterId ?: entity.preset)

                    val elements: List<StampElement> = if (!entity.designJson.isNullOrBlank()) {
                        try {
                            val type = object : com.google.gson.reflect.TypeToken<List<StampElement>>() {}.type
                            com.google.gson.Gson().fromJson(entity.designJson, type) ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    val croppedPath = entity.croppedImagePath
                    val croppedFile = croppedPath?.let { File(it) }
                    val sourcePath = if (croppedFile != null && croppedFile.exists() && croppedFile.length() > 0) {
                        entity.croppedImagePath!!
                    } else {
                        val origFile = File(entity.originalImagePath)
                        if (origFile.exists() && origFile.length() > 0) {
                            entity.originalImagePath
                        } else {
                            entity.stampImagePath
                        }
                    }

                    val spec = StampDesignSpec(
                        filterSpec = loadedFilterSpec,
                        templateId = entity.templateId ?: "classic_post",
                        borderStyle = entity.borderStyle ?: "perforated",
                        elements = elements
                    )
                    initialDesignSpec = spec
                    _uiState.update {
                        it.copy(
                            stampId = entity.id,
                            sourceImagePath = sourcePath,
                            filterSpec = loadedFilterSpec,
                            templateId = entity.templateId ?: "classic_post",
                            borderStyle = entity.borderStyle ?: "perforated",
                            elements = elements
                        )
                    }
                    return@launch
                }
            }

            // Fallback for new stamp or path
            val path = initialPhotoUrl ?: ""
            _uiState.update {
                it.copy(
                    sourceImagePath = path,
                    elements = listOf(
                        StampElement("el_1", "text", "MEMO • STAMP", 0.5f, 0.82f, rotation = -1f, colorHex = "#D94E41"),
                        StampElement("el_2", "badge", "MEMORY SERIES #001", 0.5f, 0.89f, colorHex = "#2B5B84")
                    )
                )
            }
            initialDesignSpec = currentDesignSpec()
        }
    }

    fun updateSourceImagePath(path: String) {
        pushUndoState()
        _uiState.update { it.copy(sourceImagePath = path, hasChanges = true) }
    }

    private fun currentDesignSpec(): StampDesignSpec {
        val s = _uiState.value
        return StampDesignSpec(s.filterSpec, s.templateId, s.borderStyle, s.elements)
    }

    private fun pushUndoState() {
        val currentSpec = currentDesignSpec()
        _uiState.update { state ->
            val newUndo = (state.undoStack + currentSpec).takeLast(25)
            state.copy(
                undoStack = newUndo,
                redoStack = emptyList(),
                hasChanges = true
            )
        }
    }

    fun selectFilter(filterSpec: CameraFilterSpec) {
        pushUndoState()
        _uiState.update { it.copy(filterSpec = filterSpec) }
    }

    fun selectPreset(preset: CameraPreset) {
        pushUndoState()
        val mappedSpec = FilterPresets.getById(preset.name.lowercase())
        _uiState.update { it.copy(filterSpec = mappedSpec, preset = preset) }
    }

    fun selectTemplate(templateId: String) {
        pushUndoState()
        val template = StampTemplates.getById(templateId)
        _uiState.update { state ->
            val mergedElements = if (template.defaultElements.isNotEmpty()) {
                (state.elements + template.defaultElements).distinctBy { it.id }
            } else {
                state.elements
            }
            state.copy(templateId = templateId, elements = mergedElements)
        }
    }

    fun selectBorder(borderStyle: String) {
        pushUndoState()
        _uiState.update { it.copy(borderStyle = borderStyle) }
    }

    fun selectElement(elementId: String?) {
        _uiState.update { it.copy(selectedElementId = elementId) }
    }

    fun addElement(type: String, value: String, colorHex: String = "#D94E41") {
        pushUndoState()
        val newId = "el_" + UUID.randomUUID().toString().take(6)
        val newElement = StampElement(
            id = newId,
            type = type,
            value = value,
            x = 0.5f,
            y = 0.5f,
            colorHex = colorHex,
            zIndex = (_uiState.value.elements.maxOfOrNull { it.zIndex } ?: 0) + 1
        )
        _uiState.update { state ->
            state.copy(
                elements = state.elements + newElement,
                selectedElementId = newId
            )
        }
    }

    fun updateElementPosition(elementId: String, newX: Float, newY: Float) {
        _uiState.update { state ->
            val updated = state.elements.map { el ->
                if (el.id == elementId) el.copy(x = newX.coerceIn(0.05f, 0.95f), y = newY.coerceIn(0.05f, 0.95f)) else el
            }
            state.copy(elements = updated)
        }
    }

    fun updateElementTransform(elementId: String, newScale: Float, newRotation: Float) {
        _uiState.update { state ->
            val updated = state.elements.map { el ->
                if (el.id == elementId) el.copy(
                    scale = newScale.coerceIn(0.4f, 3.0f),
                    rotation = (newRotation % 360f)
                ) else el
            }
            state.copy(elements = updated)
        }
    }

    fun updateElementColor(elementId: String, newColorHex: String) {
        pushUndoState()
        _uiState.update { state ->
            val updated = state.elements.map { el ->
                if (el.id == elementId) el.copy(colorHex = newColorHex) else el
            }
            state.copy(elements = updated)
        }
    }

    fun updateElementValue(elementId: String, newValue: String) {
        pushUndoState()
        _uiState.update { state ->
            val updated = state.elements.map { el ->
                if (el.id == elementId) el.copy(value = newValue) else el
            }
            state.copy(elements = updated)
        }
    }

    fun deleteElement(elementId: String) {
        pushUndoState()
        _uiState.update { state ->
            val updated = state.elements.filter { it.id != elementId }
            state.copy(
                elements = updated,
                selectedElementId = if (state.selectedElementId == elementId) null else state.selectedElementId
            )
        }
    }

    fun duplicateElement(elementId: String) {
        pushUndoState()
        val target = _uiState.value.elements.find { it.id == elementId } ?: return
        val newId = "el_" + UUID.randomUUID().toString().take(6)
        val copy = target.copy(
            id = newId,
            x = (target.x + 0.05f).coerceIn(0.1f, 0.9f),
            y = (target.y + 0.05f).coerceIn(0.1f, 0.9f),
            zIndex = (_uiState.value.elements.maxOfOrNull { it.zIndex } ?: 0) + 1
        )
        _uiState.update { state ->
            state.copy(
                elements = state.elements + copy,
                selectedElementId = newId
            )
        }
    }

    fun commitGestureFinished() {
        pushUndoState()
    }

    fun undo() {
        _uiState.update { state ->
            if (state.undoStack.isEmpty()) return@update state
            val previousSpec = state.undoStack.last()
            val remainingUndo = state.undoStack.dropLast(1)
            val currentSpec = currentDesignSpec()
            state.copy(
                filterSpec = previousSpec.filterSpec,
                templateId = previousSpec.templateId,
                borderStyle = previousSpec.borderStyle,
                elements = previousSpec.elements,
                undoStack = remainingUndo,
                redoStack = state.redoStack + currentSpec
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            if (state.redoStack.isEmpty()) return@update state
            val nextSpec = state.redoStack.last()
            val remainingRedo = state.redoStack.dropLast(1)
            val currentSpec = currentDesignSpec()
            state.copy(
                filterSpec = nextSpec.filterSpec,
                templateId = nextSpec.templateId,
                borderStyle = nextSpec.borderStyle,
                elements = nextSpec.elements,
                undoStack = state.undoStack + currentSpec,
                redoStack = remainingRedo
            )
        }
    }

    fun saveStamp(
        context: Context,
        title: String,
        location: String,
        date: String,
        caption: String,
        onSuccess: (StampEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value

            withContext(Dispatchers.IO) {
                try {
                    val repo = StampRepository.getInstance(context)

                    // 1. Decode original bitmap safely
                    val origPath = state.sourceImagePath
                    val rawBitmap = SafeBitmapDecoder.decodeSampledBitmap(
                        context = context,
                        uriOrPath = origPath,
                        reqWidth = 1500,
                        reqHeight = 2000
                    )

                    if (rawBitmap == null) {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isSaving = false) }
                            onError("Original photo file not found or invalid: $origPath")
                        }
                        return@withContext
                    }

                    // 2. Render new PNG to file directly
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val renderedFile = StampRenderer.renderStampToPng(
                        context = context,
                        croppedBitmap = rawBitmap,
                        elements = state.elements,
                        filterSpec = state.filterSpec,
                        outputFileName = "STAMP_$timeStamp.png"
                    )

                    if (!rawBitmap.isRecycled) rawBitmap.recycle()

                    val nowMillis = System.currentTimeMillis()
                    val designJsonStr = com.google.gson.Gson().toJson(state.elements)

                    // 3. Update database & safely replace old file only upon success
                    if (!state.stampId.isNullOrBlank()) {
                        val existing = repo.getStampById(state.stampId).getOrNull()
                        if (existing == null) {
                            renderedFile.delete()
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isSaving = false) }
                                onError("Stamp not found or unauthorized")
                            }
                            return@withContext
                        }

                        val oldProcPath = existing.stampImagePath
                        val updatedEntity = existing.copy(
                            stampImagePath = renderedFile.absolutePath,
                            title = title,
                            note = caption,
                            location = location,
                            preset = state.filterSpec.id,
                            filterId = state.filterSpec.id,
                            filterIntensity = state.filterSpec.intensity,
                            filterSpecJson = state.filterSpec.toJson(),
                            templateId = state.templateId,
                            borderStyle = state.borderStyle,
                            designJson = designJsonStr
                        )

                        val updateRes = repo.updateStamp(updatedEntity)
                        if (updateRes.isSuccess) {
                            if (!oldProcPath.isNullOrBlank() && oldProcPath != renderedFile.absolutePath) {
                                try {
                                    val oldFile = File(oldProcPath)
                                    if (oldFile.exists()) oldFile.delete()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                                onSuccess(updatedEntity)
                            }
                        } else {
                            renderedFile.delete()
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isSaving = false) }
                                onError("Failed to update stamp database")
                            }
                        }
                    } else {
                        val draft = com.mipastudio.memostamp.domain.model.StampDraft(
                            originalImagePath = origPath,
                            renderedImagePath = renderedFile.absolutePath,
                            title = title,
                            location = location,
                            memoryDate = nowMillis,
                            note = caption,
                            filterId = state.filterSpec.id,
                            filterIntensity = state.filterSpec.intensity,
                            filterSpecJson = state.filterSpec.toJson()
                        )
                        val saveRes = repo.saveStamp(draft)
                        if (saveRes.isSuccess) {
                            val newEntity = saveRes.getOrThrow()
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                                onSuccess(newEntity)
                            }
                        } else {
                            renderedFile.delete()
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(isSaving = false) }
                                onError("Failed to save stamp to database")
                            }
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isSaving = false) }
                        onError(e.localizedMessage ?: "Error saving stamp")
                    }
                }
            }
        }
    }

    fun renderAndSaveDraft(
        context: Context,
        draftId: String?,
        titleText: String,
        locationText: String,
        captionText: String,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            withContext(Dispatchers.IO) {
                try {
                    val origPath = state.sourceImagePath
                    val rawBitmap = SafeBitmapDecoder.decodeSampledBitmap(
                        context = context,
                        uriOrPath = origPath,
                        reqWidth = 1500,
                        reqHeight = 2000
                    ) ?: android.graphics.Bitmap.createBitmap(1200, 1500, android.graphics.Bitmap.Config.ARGB_8888)

                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val renderedFile = StampRenderer.renderStampToPng(
                        context = context,
                        croppedBitmap = rawBitmap,
                        elements = state.elements,
                        filterSpec = state.filterSpec,
                        outputFileName = "STAMP_DRAFT_$timeStamp.png"
                    )

                    if (!rawBitmap.isRecycled) rawBitmap.recycle()

                    val repo = StampRepository.getInstance(context)
                    val targetDraftId = draftId ?: "draft_${System.currentTimeMillis()}"
                    val newDraft = com.mipastudio.memostamp.domain.model.StampDraft(
                        id = targetDraftId,
                        originalImagePath = origPath,
                        renderedImagePath = renderedFile.absolutePath,
                        title = titleText,
                        location = locationText,
                        memoryDate = System.currentTimeMillis(),
                        note = captionText,
                        filterId = state.filterSpec.id,
                        filterIntensity = state.filterSpec.intensity,
                        filterSpecJson = state.filterSpec.toJson()
                    )
                    repo.saveDraft(newDraft)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isSaving = false) }
                        onComplete(targetDraftId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isSaving = false) }
                    }
                }
            }
        }
    }
}
