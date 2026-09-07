package com.mipastudio.memostamp.feature.collection.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.StampPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AlbumEditMode {
    VIEW,
    EDIT
}

data class TransientPlacementTransform(
    val x: Double,
    val y: Double,
    val scale: Double,
    val rotationDegrees: Double,
    val zIndex: Int
)

sealed interface AlbumEditorAction {
    data class Add(val placement: StampPlacement) : AlbumEditorAction
    data class Transform(
        val placementId: String,
        val oldX: Double,
        val oldY: Double,
        val oldScale: Double,
        val oldRotation: Double,
        val oldZIndex: Int,
        val newX: Double,
        val newY: Double,
        val newScale: Double,
        val newRotation: Double,
        val newZIndex: Int
    ) : AlbumEditorAction
    data class ZOrder(
        val placementId: String,
        val oldZIndex: Int,
        val newZIndex: Int
    ) : AlbumEditorAction
    data class MovePage(
        val placementId: String,
        val oldPageIndex: Int,
        val newPageIndex: Int
    ) : AlbumEditorAction
    data class Delete(val placement: StampPlacement) : AlbumEditorAction
}

/**
 * Transient in-memory state holder for Album interactive editing.
 * Does not write to DB/cloud per animation frame.
 */
class AlbumEditState(
    val albumId: String,
    val isVirtualAlbum: Boolean = AlbumLayoutRepository.isVirtualAlbum(albumId)
) {
    var mode by mutableStateOf(AlbumEditMode.VIEW)
        private set

    var selectedPlacementId by mutableStateOf<String?>(null)
        private set

    var activePageIndex by mutableIntStateOf(0)

    var isVaultPickerOpen by mutableStateOf(false)

    var isMovePageMenuOpen by mutableStateOf(false)

    var isSaving by mutableStateOf(false)
        private set

    var saveErrorMessage by mutableStateOf<String?>(null)
        private set

    // Transient transforms during ongoing gestures (not yet committed to DB/cloud)
    val transientTransforms = mutableStateMapOf<String, TransientPlacementTransform>()

    // Local in-memory Undo stack
    val undoStack = mutableStateListOf<AlbumEditorAction>()

    companion object {
        const val MIN_SCALE = 0.35
        const val MAX_SCALE = 3.0
        const val MIN_COORD = 0.08
        const val MAX_COORD = 0.92
        const val MIN_Z_INDEX = 1
        const val MAX_Z_INDEX = 999

        fun clampX(x: Double): Double = x.coerceIn(MIN_COORD, MAX_COORD)
        fun clampY(y: Double): Double = y.coerceIn(MIN_COORD, MAX_COORD)
        fun clampScale(scale: Double): Double = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        fun clampRotation(deg: Double): Double {
            var r = deg % 360.0
            if (r > 180.0) r -= 360.0
            if (r < -180.0) r += 360.0
            return r
        }
        fun clampZIndex(z: Int): Int = z.coerceIn(MIN_Z_INDEX, MAX_Z_INDEX)
    }

    fun enterEditMode() {
        if (isVirtualAlbum) return
        mode = AlbumEditMode.EDIT
    }

    fun exitEditMode(repository: AlbumLayoutRepository, coroutineScope: CoroutineScope) {
        // Flush any transient edits before switching to VIEW
        flushAllPendingTransforms(repository, coroutineScope)
        selectedPlacementId = null
        isVaultPickerOpen = false
        isMovePageMenuOpen = false
        mode = AlbumEditMode.VIEW
    }

    fun selectPlacement(id: String?) {
        if (mode != AlbumEditMode.EDIT) return
        selectedPlacementId = id
    }

    fun getResolvedTransform(placement: StampPlacement): TransientPlacementTransform {
        val transient = transientTransforms[placement.id]
        return transient ?: TransientPlacementTransform(
            x = placement.x,
            y = placement.y,
            scale = placement.scale,
            rotationDegrees = placement.rotationDegrees,
            zIndex = placement.zIndex
        )
    }

    /**
     * Called on each drag / pinch / rotate pointer frame.
     * Strictly memory-only; NEVER queries SQLite or Supabase here!
     */
    fun updateTransientTransform(
        placementId: String,
        x: Double,
        y: Double,
        scale: Double,
        rotationDegrees: Double,
        zIndex: Int
    ) {
        if (mode != AlbumEditMode.EDIT) return
        val clamped = TransientPlacementTransform(
            x = clampX(x),
            y = clampY(y),
            scale = clampScale(scale),
            rotationDegrees = clampRotation(rotationDegrees),
            zIndex = clampZIndex(zIndex)
        )
        transientTransforms[placementId] = clamped
    }

    /**
     * Called at the end of a user touch gesture.
     * Persists once to the repository and records an Undo action.
     */
    fun commitTransform(
        placementId: String,
        repository: AlbumLayoutRepository,
        originalPlacement: StampPlacement,
        coroutineScope: CoroutineScope
    ) {
        val finalTransform = transientTransforms.remove(placementId) ?: return
        if (isVirtualAlbum) return

        // Skip commit if practically unchanged
        if (kotlin.math.abs(finalTransform.x - originalPlacement.x) < 0.0001 &&
            kotlin.math.abs(finalTransform.y - originalPlacement.y) < 0.0001 &&
            kotlin.math.abs(finalTransform.scale - originalPlacement.scale) < 0.001 &&
            kotlin.math.abs(finalTransform.rotationDegrees - originalPlacement.rotationDegrees) < 0.1 &&
            finalTransform.zIndex == originalPlacement.zIndex
        ) {
            return
        }

        undoStack.add(
            AlbumEditorAction.Transform(
                placementId = placementId,
                oldX = originalPlacement.x,
                oldY = originalPlacement.y,
                oldScale = originalPlacement.scale,
                oldRotation = originalPlacement.rotationDegrees,
                oldZIndex = originalPlacement.zIndex,
                newX = finalTransform.x,
                newY = finalTransform.y,
                newScale = finalTransform.scale,
                newRotation = finalTransform.rotationDegrees,
                newZIndex = finalTransform.zIndex
            )
        )

        coroutineScope.launch(Dispatchers.IO) {
            isSaving = true
            val result = repository.updatePlacementTransform(
                placementId = placementId,
                albumId = albumId,
                x = finalTransform.x,
                y = finalTransform.y,
                scale = finalTransform.scale,
                rotationDegrees = finalTransform.rotationDegrees,
                zIndex = finalTransform.zIndex
            )
            isSaving = false
            if (result.isFailure) {
                saveErrorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun flushAllPendingTransforms(repository: AlbumLayoutRepository, coroutineScope: CoroutineScope) {
        if (transientTransforms.isEmpty() || isVirtualAlbum) return
        val pending = transientTransforms.toMap()
        transientTransforms.clear()

        coroutineScope.launch(Dispatchers.IO) {
            for ((id, t) in pending) {
                repository.updatePlacementTransform(
                    placementId = id,
                    albumId = albumId,
                    x = t.x,
                    y = t.y,
                    scale = t.scale,
                    rotationDegrees = t.rotationDegrees,
                    zIndex = t.zIndex
                )
            }
        }
    }

    /**
     * Adds a newly selected stamp from Vault near page center with deterministic top zIndex.
     */
    fun addPlacementFromVault(
        stampId: String,
        targetPageIndex: Int,
        existingPlacements: List<StampPlacement>,
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (isVirtualAlbum || mode != AlbumEditMode.EDIT) return
        val safePageIndex = targetPageIndex.coerceAtLeast(0)
        val maxZ = (existingPlacements.maxOfOrNull { it.zIndex } ?: 0).coerceAtLeast(0)
        val nextZ = clampZIndex(maxZ + 1)

        coroutineScope.launch(Dispatchers.IO) {
            isSaving = true
            val result = repository.upsertPlacement(
                albumId = albumId,
                pageIndex = safePageIndex,
                stampId = stampId,
                x = 0.5,
                y = 0.5,
                scale = 1.0,
                rotationDegrees = 0.0,
                zIndex = nextZ
            )
            isSaving = false
            if (result.isSuccess) {
                val created = result.getOrThrow()
                undoStack.add(AlbumEditorAction.Add(created))
                selectedPlacementId = created.id
                isVaultPickerOpen = false
            } else {
                saveErrorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    /**
     * Removes placement from current page.
     * STRICT SAFETY: Never deletes the underlying Vault stamp, media or feed items!
     */
    fun deletePlacement(
        placementId: String,
        currentPlacement: StampPlacement,
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (isVirtualAlbum || mode != AlbumEditMode.EDIT) return
        undoStack.add(AlbumEditorAction.Delete(currentPlacement))
        if (selectedPlacementId == placementId) {
            selectedPlacementId = null
        }

        coroutineScope.launch(Dispatchers.IO) {
            isSaving = true
            val result = repository.deletePlacement(placementId, albumId)
            isSaving = false
            if (result.isFailure) {
                saveErrorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    /**
     * Increments zIndex or rebalances sequence.
     */
    fun bringForward(
        placementId: String,
        currentPlacements: List<StampPlacement>,
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (isVirtualAlbum || mode != AlbumEditMode.EDIT) return
        val item = currentPlacements.find { it.id == placementId } ?: return
        val sorted = currentPlacements.sortedBy { it.zIndex }
        val itemIdx = sorted.indexOfFirst { it.id == placementId }
        if (itemIdx < 0 || itemIdx == sorted.size - 1) return // Already visually top

        val swapWith = sorted[itemIdx + 1]
        val oldZ = item.zIndex
        val newZ = clampZIndex(maxOf(oldZ + 1, swapWith.zIndex + 1))

        undoStack.add(AlbumEditorAction.ZOrder(placementId, oldZ, newZ))

        coroutineScope.launch(Dispatchers.IO) {
            repository.updatePlacementTransform(
                placementId = placementId,
                albumId = albumId,
                x = item.x,
                y = item.y,
                scale = item.scale,
                rotationDegrees = item.rotationDegrees,
                zIndex = newZ
            )
        }
    }

    /**
     * Decrements zIndex.
     */
    fun sendBackward(
        placementId: String,
        currentPlacements: List<StampPlacement>,
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (isVirtualAlbum || mode != AlbumEditMode.EDIT) return
        val item = currentPlacements.find { it.id == placementId } ?: return
        val sorted = currentPlacements.sortedBy { it.zIndex }
        val itemIdx = sorted.indexOfFirst { it.id == placementId }
        if (itemIdx <= 0) return // Already visually bottom

        val swapWith = sorted[itemIdx - 1]
        val oldZ = item.zIndex
        val newZ = clampZIndex(minOf(oldZ - 1, swapWith.zIndex - 1))

        undoStack.add(AlbumEditorAction.ZOrder(placementId, oldZ, newZ))

        coroutineScope.launch(Dispatchers.IO) {
            repository.updatePlacementTransform(
                placementId = placementId,
                albumId = albumId,
                x = item.x,
                y = item.y,
                scale = item.scale,
                rotationDegrees = item.rotationDegrees,
                zIndex = newZ
            )
        }
    }

    /**
     * Moves placement to a different editable page in the album.
     */
    fun movePlacementToPage(
        placementId: String,
        targetPageIndex: Int,
        currentPlacement: StampPlacement,
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (isVirtualAlbum || mode != AlbumEditMode.EDIT) return
        val safeTargetPage = targetPageIndex.coerceAtLeast(0)
        if (safeTargetPage == currentPlacement.pageIndex) return

        undoStack.add(
            AlbumEditorAction.MovePage(
                placementId = placementId,
                oldPageIndex = currentPlacement.pageIndex,
                newPageIndex = safeTargetPage
            )
        )

        coroutineScope.launch(Dispatchers.IO) {
            isSaving = true
            val result = repository.movePlacementToPage(placementId, albumId, safeTargetPage)
            isSaving = false
            if (result.isFailure) {
                saveErrorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    /**
     * Reverts the latest editor action.
     */
    fun undo(
        repository: AlbumLayoutRepository,
        coroutineScope: CoroutineScope
    ) {
        if (undoStack.isEmpty() || isVirtualAlbum) return
        val lastAction = undoStack.removeAt(undoStack.lastIndex)

        coroutineScope.launch(Dispatchers.IO) {
            when (lastAction) {
                is AlbumEditorAction.Add -> {
                    repository.deletePlacement(lastAction.placement.id, albumId)
                    if (selectedPlacementId == lastAction.placement.id) {
                        selectedPlacementId = null
                    }
                }
                is AlbumEditorAction.Transform -> {
                    repository.updatePlacementTransform(
                        placementId = lastAction.placementId,
                        albumId = albumId,
                        x = lastAction.oldX,
                        y = lastAction.oldY,
                        scale = lastAction.oldScale,
                        rotationDegrees = lastAction.oldRotation,
                        zIndex = lastAction.oldZIndex
                    )
                }
                is AlbumEditorAction.ZOrder -> {
                    val currentPlacements = repository.getLocalLayout(albumId).placements
                    val item = currentPlacements.find { it.id == lastAction.placementId }
                    if (item != null) {
                        repository.updatePlacementTransform(
                            placementId = lastAction.placementId,
                            albumId = albumId,
                            x = item.x,
                            y = item.y,
                            scale = item.scale,
                            rotationDegrees = item.rotationDegrees,
                            zIndex = lastAction.oldZIndex
                        )
                    }
                }
                is AlbumEditorAction.MovePage -> {
                    repository.movePlacementToPage(
                        placementId = lastAction.placementId,
                        albumId = albumId,
                        newPageIndex = lastAction.oldPageIndex
                    )
                }
                is AlbumEditorAction.Delete -> {
                    repository.upsertPlacement(
                        albumId = albumId,
                        pageIndex = lastAction.placement.pageIndex,
                        stampId = lastAction.placement.stampId,
                        x = lastAction.placement.x,
                        y = lastAction.placement.y,
                        scale = lastAction.placement.scale,
                        rotationDegrees = lastAction.placement.rotationDegrees,
                        zIndex = lastAction.placement.zIndex
                    )
                    selectedPlacementId = lastAction.placement.id
                }
            }
        }
    }

    /**
     * Account switch or album close cleanup.
     * Invalidates inflight transient state completely.
     */
    fun resetSession() {
        mode = AlbumEditMode.VIEW
        selectedPlacementId = null
        transientTransforms.clear()
        undoStack.clear()
        isVaultPickerOpen = false
        isMovePageMenuOpen = false
        isSaving = false
        saveErrorMessage = null
    }
}
