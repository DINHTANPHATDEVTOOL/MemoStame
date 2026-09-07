package com.mipastudio.memostamp.feature.collection

import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditMode
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditState
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditorAction
import org.junit.Assert.*
import org.junit.Test

class AlbumEditorLogicTest {

    @Test
    fun virtualAlbum_cannotEnterEditMode() {
        val virtualState = AlbumEditState(albumId = "loc_tokyo_district")
        assertTrue(virtualState.isVirtualAlbum)
        virtualState.enterEditMode()
        assertEquals(AlbumEditMode.VIEW, virtualState.mode)

        val virtualLegacyState = AlbumEditState(albumId = "virtual_favorites")
        assertTrue(virtualLegacyState.isVirtualAlbum)
        virtualLegacyState.enterEditMode()
        assertEquals(AlbumEditMode.VIEW, virtualLegacyState.mode)

        val regularState = AlbumEditState(albumId = "col_persisted_user_album_1")
        assertFalse(regularState.isVirtualAlbum)
        regularState.enterEditMode()
        assertEquals(AlbumEditMode.EDIT, regularState.mode)
    }

    @Test
    fun clampingRules_enforceCoordScaleRotationZIndexBounds() {
        // Coordinate bounds: [0.08, 0.92]
        assertEquals(0.08, AlbumEditState.clampX(-0.5), 0.0001)
        assertEquals(0.08, AlbumEditState.clampX(0.05), 0.0001)
        assertEquals(0.50, AlbumEditState.clampX(0.50), 0.0001)
        assertEquals(0.92, AlbumEditState.clampX(0.98), 0.0001)
        assertEquals(0.92, AlbumEditState.clampX(1.50), 0.0001)

        assertEquals(0.08, AlbumEditState.clampY(-0.1), 0.0001)
        assertEquals(0.92, AlbumEditState.clampY(1.2), 0.0001)

        // Scale bounds: [0.35, 3.0]
        assertEquals(0.35, AlbumEditState.clampScale(0.1), 0.0001)
        assertEquals(1.0, AlbumEditState.clampScale(1.0), 0.0001)
        assertEquals(3.0, AlbumEditState.clampScale(5.5), 0.0001)

        // Rotation bounds: [-180, 180]
        assertEquals(0.0, AlbumEditState.clampRotation(0.0), 0.0001)
        assertEquals(45.0, AlbumEditState.clampRotation(45.0), 0.0001)
        assertEquals(-170.0, AlbumEditState.clampRotation(190.0), 0.0001)
        assertEquals(170.0, AlbumEditState.clampRotation(-190.0), 0.0001)
        assertEquals(0.0, AlbumEditState.clampRotation(360.0), 0.0001)

        // Z-Index bounds: [1, 999]
        assertEquals(1, AlbumEditState.clampZIndex(0))
        assertEquals(1, AlbumEditState.clampZIndex(-10))
        assertEquals(42, AlbumEditState.clampZIndex(42))
        assertEquals(999, AlbumEditState.clampZIndex(1000))
    }

    @Test
    fun selection_onlyAllowedInEditMode() {
        val state = AlbumEditState(albumId = "col_test")
        assertEquals(AlbumEditMode.VIEW, state.mode)

        state.selectPlacement("place_1")
        assertNull(state.selectedPlacementId)

        state.enterEditMode()
        assertEquals(AlbumEditMode.EDIT, state.mode)

        state.selectPlacement("place_1")
        assertEquals("place_1", state.selectedPlacementId)

        state.selectPlacement(null)
        assertNull(state.selectedPlacementId)
    }

    @Test
    fun transientTransform_resolvesCorrectlyOverOriginalPlacement() {
        val state = AlbumEditState(albumId = "col_test")
        state.enterEditMode()

        val original = StampPlacement(
            id = "p1",
            albumId = "col_test",
            pageIndex = 0,
            stampId = "stamp_1",
            x = 0.3,
            y = 0.4,
            scale = 1.0,
            rotationDegrees = 0.0,
            zIndex = 1
        )

        // Before transient transform, resolved matches original
        val initialResolved = state.getResolvedTransform(original)
        assertEquals(0.3, initialResolved.x, 0.0001)
        assertEquals(0.4, initialResolved.y, 0.0001)
        assertEquals(1.0, initialResolved.scale, 0.0001)

        // Apply transient updates
        state.updateTransientTransform(
            placementId = "p1",
            x = 0.65,
            y = 0.75,
            scale = 1.8,
            rotationDegrees = 25.0,
            zIndex = 2
        )

        val updatedResolved = state.getResolvedTransform(original)
        assertEquals(0.65, updatedResolved.x, 0.0001)
        assertEquals(0.75, updatedResolved.y, 0.0001)
        assertEquals(1.8, updatedResolved.scale, 0.0001)
        assertEquals(25.0, updatedResolved.rotationDegrees, 0.0001)
        assertEquals(2, updatedResolved.zIndex)
    }

    @Test
    fun transientTransform_clampsValuesWithinSafeBounds() {
        val state = AlbumEditState(albumId = "col_test")
        state.enterEditMode()

        val original = StampPlacement(
            id = "p1",
            albumId = "col_test",
            pageIndex = 0,
            stampId = "stamp_1",
            x = 0.5,
            y = 0.5,
            scale = 1.0,
            rotationDegrees = 0.0,
            zIndex = 1
        )

        // Attempt out of bounds values
        state.updateTransientTransform(
            placementId = "p1",
            x = -0.5,
            y = 1.8,
            scale = 10.0,
            rotationDegrees = 270.0,
            zIndex = 10000
        )

        val clamped = state.getResolvedTransform(original)
        assertEquals(0.08, clamped.x, 0.0001)
        assertEquals(0.92, clamped.y, 0.0001)
        assertEquals(3.0, clamped.scale, 0.0001)
        assertEquals(-90.0, clamped.rotationDegrees, 0.0001)
        assertEquals(999, clamped.zIndex)
    }

    @Test
    fun undoStack_recordsActionsAndPops() {
        val state = AlbumEditState(albumId = "col_test")
        state.enterEditMode()

        val action1 = AlbumEditorAction.Transform(
            placementId = "p1",
            oldX = 0.2, oldY = 0.2, oldScale = 1.0, oldRotation = 0.0, oldZIndex = 1,
            newX = 0.5, newY = 0.5, newScale = 1.2, newRotation = 10.0, newZIndex = 1
        )
        val action2 = AlbumEditorAction.ZOrder(placementId = "p1", oldZIndex = 1, newZIndex = 2)

        state.undoStack.add(action1)
        state.undoStack.add(action2)
        assertEquals(2, state.undoStack.size)

        val popped = state.undoStack.removeAt(state.undoStack.lastIndex)
        assertEquals(action2, popped)
        assertEquals(1, state.undoStack.size)
        assertEquals(action1, state.undoStack[0])
    }

    @Test
    fun movePageAction_recordsCorrectIndices() {
        val action = AlbumEditorAction.MovePage(
            placementId = "p1",
            oldPageIndex = 0,
            newPageIndex = 2
        )
        assertEquals("p1", action.placementId)
        assertEquals(0, action.oldPageIndex)
        assertEquals(2, action.newPageIndex)
    }

    @Test
    fun sessionReset_clearsAllTransientState() {
        val state = AlbumEditState(albumId = "col_test")
        state.enterEditMode()
        state.selectPlacement("p1")
        state.updateTransientTransform("p1", 0.7, 0.7, 1.5, 30.0, 3)
        state.undoStack.add(AlbumEditorAction.ZOrder("p1", 1, 2))
        state.isVaultPickerOpen = true
        state.isMovePageMenuOpen = true

        state.resetSession()

        assertEquals(AlbumEditMode.VIEW, state.mode)
        assertNull(state.selectedPlacementId)
        assertTrue(state.transientTransforms.isEmpty())
        assertTrue(state.undoStack.isEmpty())
        assertFalse(state.isVaultPickerOpen)
        assertFalse(state.isMovePageMenuOpen)
        assertFalse(state.isSaving)
        assertNull(state.saveErrorMessage)
    }
}
