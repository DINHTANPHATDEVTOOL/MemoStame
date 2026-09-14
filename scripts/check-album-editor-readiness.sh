#!/usr/bin/env bash
set -e

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

echo "=========================================================="
echo "  Task #78: Interactive Album Stamp Editor Readiness Suite"
echo "=========================================================="

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/27] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. Android AlbumEditState exists and defines core state & constraints
check_step "1" "Checking Android AlbumEditState implementation" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "enum class AlbumEditMode" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "val transientTransforms" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "val undoStack" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "fun enterEditMode" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt
'

# 2. Android EditableStampPlacement combined gestures
check_step "2" "Checking Android EditableStampPlacement gestures" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt && \
    grep -q "detectTransformGestures" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt && \
    grep -q "updateTransientTransform" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt && \
    grep -q "commitTransform" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt
'

# 3. Android EditableBookPage interactive container
check_step "3" "Checking Android EditableBookPage container" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableBookPage.kt && \
    grep -q "BoxWithConstraints" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableBookPage.kt && \
    grep -q "EditableStampPlacement" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableBookPage.kt && \
    grep -q "selectPlacement(null)" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableBookPage.kt
'

# 4. Android AlbumVaultPicker sheet
check_step "4" "Checking Android AlbumVaultPicker sheet" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumVaultPicker.kt && \
    grep -q "ModalBottomSheet" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumVaultPicker.kt && \
    grep -q "LazyVerticalGrid" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumVaultPicker.kt && \
    grep -q "placedStampIds" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumVaultPicker.kt
'

# 5. Android AlbumEditorToolbar actions
check_step "5" "Checking Android AlbumEditorToolbar actions" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    grep -q "AlbumEditorTopControls" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    grep -q "AlbumEditorBottomBar" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    grep -q "bringForward" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    grep -q "deletePlacement" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt
'

# 6. Android StampBook3DRenderer integration
check_step "6" "Checking Android StampBook3DRenderer editor integration" '
    grep -q "AlbumEditState" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "editState.mode == AlbumEditMode.EDIT" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "AlbumEditorTopControls" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "AlbumVaultPicker" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt
'

# 7. Android CollectionScreen passing available vault stamps
check_step "7" "Checking Android CollectionScreen wiring" '
    grep -q "availableVaultStamps" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt && \
    grep -q "availableVaultStamps = cloudStamps" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt
'

# 8. Android Unit Tests
check_step "8" "Checking Android AlbumEditorLogicTest exists" '
    test -f androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumEditorLogicTest.kt && \
    grep -q "class AlbumEditorLogicTest" androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumEditorLogicTest.kt && \
    grep -q "virtualAlbum_cannotEnterEditMode" androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumEditorLogicTest.kt
'

# 9. iOS AlbumEditState implementation
check_step "9" "Checking iOS AlbumEditState implementation" '
    test -f iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "enum AlbumEditMode" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "transientTransforms: \[String: TransientPlacementTransformData\]" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "undoStack: \[AlbumEditorAction\]" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "func enterEditMode" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift
'

# 10. iOS EditableStampPlacementView gestures
check_step "10" "Checking iOS EditableStampPlacementView gestures" '
    test -f iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift && \
    grep -q "DragGesture" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift && \
    grep -q "MagnificationGesture" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift && \
    grep -q "RotationGesture" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift && \
    grep -q "commitTransform" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift
'

# 11. iOS EditableBookPageView interactive container
check_step "11" "Checking iOS EditableBookPageView container" '
    test -f iosApp/iosApp/Features/Collection/Editor/EditableBookPageView.swift && \
    grep -q "GeometryReader" iosApp/iosApp/Features/Collection/Editor/EditableBookPageView.swift && \
    grep -q "EditableStampPlacementView" iosApp/iosApp/Features/Collection/Editor/EditableBookPageView.swift && \
    grep -q "selectPlacement(nil)" iosApp/iosApp/Features/Collection/Editor/EditableBookPageView.swift
'

# 12. iOS AlbumVaultPickerView sheet
check_step "12" "Checking iOS AlbumVaultPickerView sheet" '
    test -f iosApp/iosApp/Features/Collection/Editor/AlbumVaultPickerView.swift && \
    grep -q "LazyVGrid" iosApp/iosApp/Features/Collection/Editor/AlbumVaultPickerView.swift && \
    grep -q "addPlacementFromVault" iosApp/iosApp/Features/Collection/Editor/AlbumVaultPickerView.swift
'

# 13. iOS AlbumEditorToolbarView
check_step "13" "Checking iOS AlbumEditorToolbarView actions" '
    test -f iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift && \
    grep -q "AlbumEditorTopControlsView" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift && \
    grep -q "AlbumEditorBottomBarView" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift && \
    grep -q "bringForward" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift && \
    grep -q "deletePlacement" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift
'

# 14. iOS StampBook3DRenderer integration
check_step "14" "Checking iOS StampBook3DRenderer integration" '
    grep -q "editState.mode != .edit" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift && \
    grep -q "AlbumEditorTopControlsView" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift && \
    grep -q "AlbumVaultPickerView" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 15. iOS CollectionScreenView passing available vault stamps
check_step "15" "Checking iOS CollectionScreenView wiring" '
    grep -q "availableVaultStamps" iosApp/iosApp/Features/Collection/CollectionScreenView.swift && \
    grep -q "cloudStamps.map" iosApp/iosApp/Features/Collection/CollectionScreenView.swift
'

# 16. iOS project.pbxproj registers new editor files
check_step "16" "Checking iOS project.pbxproj registrations" '
    grep -q "AlbumEditState.swift" iosApp/iosApp.xcodeproj/project.pbxproj && \
    grep -q "EditableStampPlacementView.swift" iosApp/iosApp.xcodeproj/project.pbxproj && \
    grep -q "EditableBookPageView.swift" iosApp/iosApp.xcodeproj/project.pbxproj && \
    grep -q "AlbumVaultPickerView.swift" iosApp/iosApp.xcodeproj/project.pbxproj && \
    grep -q "AlbumEditorToolbarView.swift" iosApp/iosApp.xcodeproj/project.pbxproj
'

# 17. iOS Unit Tests
check_step "17" "Checking iOS AlbumEditorLogicTests exists" '
    test -f iosApp/iosAppTests/AlbumEditorLogicTests.swift && \
    grep -q "class AlbumEditorLogicTests" iosApp/iosAppTests/AlbumEditorLogicTests.swift && \
    grep -q "testVirtualAlbum_cannotEnterEditMode" iosApp/iosAppTests/AlbumEditorLogicTests.swift
'

# 18-21. Localization strings in 4 files
check_step "18" "Checking Android English localization keys" '
    grep -q "album_editor_edit" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_done" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_add_stamp" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_vault_title" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_undo" androidApp/src/main/res/values/strings.xml
'

check_step "19" "Checking Android Vietnamese localization keys" '
    grep -q "album_editor_edit" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "album_editor_done" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "album_editor_add_stamp" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "album_editor_vault_title" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "album_editor_undo" androidApp/src/main/res/values-vi/strings.xml
'

check_step "20" "Checking iOS English localization keys" '
    grep -q "album_editor_edit" iosApp/iosApp/en.lproj/Localizable.strings && \
    grep -q "album_editor_done" iosApp/iosApp/en.lproj/Localizable.strings && \
    grep -q "album_editor_add_stamp" iosApp/iosApp/en.lproj/Localizable.strings && \
    grep -q "album_editor_vault_title" iosApp/iosApp/en.lproj/Localizable.strings && \
    grep -q "album_editor_undo" iosApp/iosApp/en.lproj/Localizable.strings
'

check_step "21" "Checking iOS Vietnamese localization keys" '
    grep -q "album_editor_edit" iosApp/iosApp/vi.lproj/Localizable.strings && \
    grep -q "album_editor_done" iosApp/iosApp/vi.lproj/Localizable.strings && \
    grep -q "album_editor_add_stamp" iosApp/iosApp/vi.lproj/Localizable.strings && \
    grep -q "album_editor_vault_title" iosApp/iosApp/vi.lproj/Localizable.strings && \
    grep -q "album_editor_undo" iosApp/iosApp/vi.lproj/Localizable.strings
'

# 22. Virtual album protection
check_step "22" "Checking virtual album read-only enforcement" '
    grep -q "if (isVirtualAlbum) return" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "guard \!isVirtualAlbum else { return }" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift
'

# 23. Safe deletion contract: no Vault deletion
check_step "23" "Checking safe deletion: placement-only deletion" '
    grep -q "deletePlacement(placementId, albumId)" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "deletePlacement(placementId: placementId, albumId: albumId)" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    ! grep -q "deleteStamp" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    ! grep -q "deleteStamp" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift
'

# 24. Gesture arbitration: page turns suppressed in edit mode
check_step "24" "Checking gesture arbitration suppresses page turn in edit mode" '
    grep -q "editState.mode == AlbumEditMode.EDIT" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "editState.mode != .edit" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 25. Coordinate & scale bounds enforcement
check_step "25" "Checking coordinate and scale clamping bounds" '
    grep -q "MIN_SCALE = 0.35" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "MAX_SCALE = 3.0" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "minScale: Double = 0.35" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "maxScale: Double = 3.0" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift
'

# 26. Transient memory-only during gesture execution
check_step "26" "Checking transient memory-only during gesture execution" '
    grep -q "updateTransientTransform" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt && \
    grep -q "updateTransientTransform" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift && \
    grep -q "commitTransform" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/EditableStampPlacement.kt && \
    grep -q "commitTransform" iosApp/iosApp/Features/Collection/Editor/EditableStampPlacementView.swift
'

# 27. E2E contract test rate-limit window boundary guard
check_step "27" "Checking E2E contract test rate-limit boundary guard in Phase 13" '
    grep -q "fresh minute window so all 30 DMs land in the same rate-limit window" supabase/tests/e2e_contract_tests.py
'

echo "=========================================================="
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo "  ALL 27 READINESS CHECKS PASSED!"
    echo "=========================================================="
    exit 0
else
    echo "  $FAILED_CHECKS CHECKS FAILED!"
    echo "=========================================================="
    exit 1
fi
