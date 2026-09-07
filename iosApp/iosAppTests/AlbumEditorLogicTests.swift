import XCTest
import SwiftUI
@testable import iosApp

final class AlbumEditorLogicTests: XCTestCase {

    func testVirtualAlbum_cannotEnterEditMode() {
        let virtualState = AlbumEditState(albumId: "loc_Tokyo_Chiyoda")
        XCTAssertTrue(virtualState.isVirtualAlbum)
        virtualState.enterEditMode()
        XCTAssertEqual(virtualState.mode, .view)

        let legacyVirtualState = AlbumEditState(albumId: "virtual_top_rated")
        XCTAssertTrue(legacyVirtualState.isVirtualAlbum)
        legacyVirtualState.enterEditMode()
        XCTAssertEqual(legacyVirtualState.mode, .view)

        let regularState = AlbumEditState(albumId: "col_vacation_2026")
        XCTAssertFalse(regularState.isVirtualAlbum)
        regularState.enterEditMode()
        XCTAssertEqual(regularState.mode, .edit)
    }

    func testClampingRules_enforceCoordScaleRotationZIndexBounds() {
        // Coordinate bounds: [0.08, 0.92]
        XCTAssertEqual(AlbumEditState.clampX(-0.5), 0.08, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampX(0.02), 0.08, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampX(0.50), 0.50, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampX(0.95), 0.92, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampX(1.50), 0.92, accuracy: 0.0001)

        XCTAssertEqual(AlbumEditState.clampY(-0.1), 0.08, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampY(1.2), 0.92, accuracy: 0.0001)

        // Scale bounds: [0.35, 3.0]
        XCTAssertEqual(AlbumEditState.clampScale(0.1), 0.35, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampScale(1.0), 1.0, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampScale(4.5), 3.0, accuracy: 0.0001)

        // Rotation bounds: [-180.0, 180.0]
        XCTAssertEqual(AlbumEditState.clampRotation(0.0), 0.0, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampRotation(45.0), 45.0, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampRotation(190.0), -170.0, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampRotation(-190.0), 170.0, accuracy: 0.0001)
        XCTAssertEqual(AlbumEditState.clampRotation(360.0), 0.0, accuracy: 0.0001)

        // Z-Index bounds: [1, 999]
        XCTAssertEqual(AlbumEditState.clampZIndex(0), 1)
        XCTAssertEqual(AlbumEditState.clampZIndex(-5), 1)
        XCTAssertEqual(AlbumEditState.clampZIndex(15), 15)
        XCTAssertEqual(AlbumEditState.clampZIndex(2000), 999)
    }

    func testSelection_onlyAllowedInEditMode() {
        let state = AlbumEditState(albumId: "col_test")
        XCTAssertEqual(state.mode, .view)

        state.selectPlacement("place_1")
        XCTAssertNil(state.selectedPlacementId)

        state.enterEditMode()
        XCTAssertEqual(state.mode, .edit)

        state.selectPlacement("place_1")
        XCTAssertEqual(state.selectedPlacementId, "place_1")

        state.selectPlacement(nil)
        XCTAssertNil(state.selectedPlacementId)
    }

    func testTransientTransform_resolvesCorrectlyOverOriginalPlacement() {
        let state = AlbumEditState(albumId: "col_test")
        state.enterEditMode()

        let original = PersistedStampPlacementData(
            id: "p1",
            albumId: "col_test",
            pageIndex: 0,
            stampId: "stamp_1",
            x: 0.25,
            y: 0.35,
            scale: 1.0,
            rotationDegrees: 0.0,
            zIndex: 1,
            updatedAt: 0
        )

        let initialResolved = state.getResolvedTransform(for: original)
        XCTAssertEqual(initialResolved.x, 0.25, accuracy: 0.0001)
        XCTAssertEqual(initialResolved.y, 0.35, accuracy: 0.0001)
        XCTAssertEqual(initialResolved.scale, 1.0, accuracy: 0.0001)

        state.updateTransientTransform(
            placementId: "p1",
            x: 0.60,
            y: 0.70,
            scale: 1.5,
            rotationDegrees: 20.0,
            zIndex: 3
        )

        let updatedResolved = state.getResolvedTransform(for: original)
        XCTAssertEqual(updatedResolved.x, 0.60, accuracy: 0.0001)
        XCTAssertEqual(updatedResolved.y, 0.70, accuracy: 0.0001)
        XCTAssertEqual(updatedResolved.scale, 1.5, accuracy: 0.0001)
        XCTAssertEqual(updatedResolved.rotationDegrees, 20.0, accuracy: 0.0001)
        XCTAssertEqual(updatedResolved.zIndex, 3)
    }

    func testUndoStack_recordsActionsAndPops() {
        let state = AlbumEditState(albumId: "col_test")
        state.enterEditMode()

        let action1 = AlbumEditorAction.transform(
            placementId: "p1",
            oldX: 0.2, oldY: 0.2, oldScale: 1.0, oldRotation: 0.0, oldZIndex: 1,
            newX: 0.5, newY: 0.5, newScale: 1.2, newRotation: 15.0, newZIndex: 1
        )
        let action2 = AlbumEditorAction.zOrder(placementId: "p1", oldZIndex: 1, newZIndex: 2)

        state.undoStack.append(action1)
        state.undoStack.append(action2)
        XCTAssertEqual(state.undoStack.count, 2)

        let popped = state.undoStack.popLast()
        XCTAssertNotNil(popped)
        XCTAssertEqual(state.undoStack.count, 1)
    }

    func testSessionReset_clearsAllTransientState() {
        let state = AlbumEditState(albumId: "col_test")
        state.enterEditMode()
        state.selectPlacement("p1")
        state.updateTransientTransform(placementId: "p1", x: 0.8, y: 0.8, scale: 2.0, rotationDegrees: 45.0, zIndex: 4)
        state.undoStack.append(.zOrder(placementId: "p1", oldZIndex: 1, newZIndex: 2))
        state.isVaultPickerOpen = true
        state.isMovePageMenuOpen = true

        state.resetSession()

        XCTAssertEqual(state.mode, .view)
        XCTAssertNil(state.selectedPlacementId)
        XCTAssertTrue(state.transientTransforms.isEmpty)
        XCTAssertTrue(state.undoStack.isEmpty)
        XCTAssertFalse(state.isVaultPickerOpen)
        XCTAssertFalse(state.isMovePageMenuOpen)
        XCTAssertFalse(state.isSaving)
        XCTAssertNil(state.saveErrorMessage)
    }
}
