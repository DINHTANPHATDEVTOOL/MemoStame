#!/usr/bin/env bash
set -e

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

echo "=========================================================="
echo "  Task #80: 3D Stamp Book Page Lifecycle Readiness Suite"
echo "=========================================================="

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/28] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. SQL migration 013 exists and defines page lifecycle schema & RPCs
check_step "1" "Checking migration 013_album_page_lifecycle.sql" '
    test -f supabase/migrations/013_album_page_lifecycle.sql && \
    grep -E -q "ALTER TABLE (public\.)?album_stamp_placements ADD COLUMN IF NOT EXISTS page_id UUID" supabase/migrations/013_album_page_lifecycle.sql && \
    grep -E -q "CREATE OR REPLACE FUNCTION (public\.)?reorder_album_pages" supabase/migrations/013_album_page_lifecycle.sql && \
    grep -E -q "CREATE OR REPLACE FUNCTION (public\.)?remove_album_page" supabase/migrations/013_album_page_lifecycle.sql && \
    grep -E -q "CREATE OR REPLACE FUNCTION (public\.)?append_album_page" supabase/migrations/013_album_page_lifecycle.sql
'

# 2. Supabase security tests have assertions 116-121
check_step "2" "Checking Supabase RLS security negative tests" '
    grep -q "Assertion 116" supabase/tests/rls_negative_tests.sql && \
    grep -q "Assertion 117" supabase/tests/rls_negative_tests.sql && \
    grep -q "Assertion 118" supabase/tests/rls_negative_tests.sql && \
    grep -q "Assertion 119" supabase/tests/rls_negative_tests.sql && \
    grep -q "Assertion 120" supabase/tests/rls_negative_tests.sql && \
    grep -q "Assertion 121" supabase/tests/rls_negative_tests.sql
'

# 3. KMP shared domain model
check_step "3" "Checking KMP shared StampPlacement model" '
    grep -q "val pageId: String = \"\"" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/Models.kt
'

# 4. Android domain model
check_step "4" "Checking Android StampPlacement model" '
    grep -q "val pageId: String = \"\"" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/Models.kt
'

# 5. Android Room StampPlacementEntity
check_step "5" "Checking Android Room StampPlacementEntity pageId column & index" '
    grep -q "val pageId: String = \"\"" androidApp/src/main/java/com/mipastudio/memostamp/data/local/StampPlacementEntity.kt && \
    grep -q "Index(value = \[\"pageId\"\])" androidApp/src/main/java/com/mipastudio/memostamp/data/local/StampPlacementEntity.kt
'

# 6. Android Room MemoStampDatabase Migration 15_16
check_step "6" "Checking MemoStampDatabase version 16 & MIGRATION_15_16" '
    grep -q "version = 16" androidApp/src/main/java/com/mipastudio/memostamp/data/local/MemoStampDatabase.kt && \
    grep -q "MIGRATION_15_16" androidApp/src/main/java/com/mipastudio/memostamp/data/local/MemoStampDatabase.kt
'

# 7. Android Room AlbumLayoutDao deletePage
check_step "7" "Checking AlbumLayoutDao deletePage method" '
    grep -q "suspend fun deletePage" androidApp/src/main/java/com/mipastudio/memostamp/data/local/AlbumLayoutDao.kt
'

# 8. Android SupabaseClient RPC calls
check_step "8" "Checking Android SupabaseClient page lifecycle RPCs" '
    grep -q "suspend fun appendAlbumPage" androidApp/src/main/java/com/mipastudio/memostamp/data/remote/supabase/SupabaseClient.kt && \
    grep -q "suspend fun removeAlbumPage" androidApp/src/main/java/com/mipastudio/memostamp/data/remote/supabase/SupabaseClient.kt && \
    grep -q "suspend fun reorderAlbumPages" androidApp/src/main/java/com/mipastudio/memostamp/data/remote/supabase/SupabaseClient.kt
'

# 9. Android AlbumLayoutRepository lifecycle operations
check_step "9" "Checking Android AlbumLayoutRepository lifecycle operations" '
    grep -q "suspend fun ensurePageStructure" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun appendPage" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun removePage" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun reorderPages" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt
'

# 10. Android PageTurnState canonical page authority
check_step "10" "Checking Android PageTurnState canonical spreads authority" '
    grep -q "CANONICAL PAGE AUTHORITY" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "pages: List<com.mipastudio.memostamp.domain.model.AlbumPage>" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt
'

# 11. Android AlbumEditState page management support
check_step "11" "Checking Android AlbumEditState page management state & actions" '
    grep -q "var isPageManagementOpen" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "fun openPageManagement" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "fun appendPage" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "fun removePage" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt && \
    grep -q "fun reorderPage" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditState.kt
'

# 12. Android AlbumPageManagementSheet component exists
check_step "12" "Checking Android AlbumPageManagementSheet component" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumPageManagementSheet.kt && \
    grep -q "fun AlbumPageManagementSheet" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumPageManagementSheet.kt
'

# 13. Android AlbumEditorToolbar canonical pages
check_step "13" "Checking Android AlbumEditorToolbar canonical page destination" '
    grep -q "album_editor_manage_pages" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    grep -q "pages: List<AlbumPage>" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt
'

# 14. Android StampBook3DRenderer wiring
check_step "14" "Checking Android StampBook3DRenderer canonical pages & sheet wiring" '
    grep -q "pages: List<AlbumPage>" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "AlbumPageManagementSheet" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt
'

# 15. Android CollectionScreen ensurePageStructure
check_step "15" "Checking Android CollectionScreen ensurePageStructure wiring" '
    grep -q "ensurePageStructure" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt && \
    grep -q "pages = pages" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt
'

# 16. Android AlbumPageLifecycleTest
check_step "16" "Checking Android AlbumPageLifecycleTest exists" '
    test -f androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumPageLifecycleTest.kt && \
    grep -q "class AlbumPageLifecycleTest" androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumPageLifecycleTest.kt
'

# 17. iOS PersistedStampPlacementData pageId
check_step "17" "Checking iOS PersistedStampPlacementData pageId property" '
    grep -q "public let pageId: String?" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift
'

# 18. iOS IOSAlbumLayoutRepository lifecycle methods
check_step "18" "Checking iOS IOSAlbumLayoutRepository lifecycle operations" '
    grep -q "func ensurePageStructure" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func appendPage" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func removePage" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func reorderPages" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift
'

# 19. iOS PageTurnState canonical page authority
check_step "19" "Checking iOS PageTurnState canonical spreads authority" '
    grep -q "CANONICAL PAGE AUTHORITY" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift && \
    grep -q "pages: \[PersistedAlbumPageData\]" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 20. iOS AlbumEditState page management support
check_step "20" "Checking iOS AlbumEditState page management state & actions" '
    grep -q "var isPageManagementOpen" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "func openPageManagement" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "func appendPage" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "func removePage" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift && \
    grep -q "func reorderPage" iosApp/iosApp/Features/Collection/Editor/AlbumEditState.swift
'

# 21. iOS AlbumPageManagementSheetView exists
check_step "21" "Checking iOS AlbumPageManagementSheetView component" '
    test -f iosApp/iosApp/Features/Collection/Editor/AlbumPageManagementSheetView.swift && \
    grep -q "struct AlbumPageManagementSheetView" iosApp/iosApp/Features/Collection/Editor/AlbumPageManagementSheetView.swift
'

# 22. iOS AlbumEditorToolbarView canonical pages
check_step "22" "Checking iOS AlbumEditorToolbarView canonical page destination" '
    grep -q "album_editor_manage_pages" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift && \
    grep -q "pages: \[PersistedAlbumPageData\]" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift
'

# 23. iOS StampBook3DRenderer wiring
check_step "23" "Checking iOS StampBook3DRenderer canonical pages & sheet wiring" '
    grep -q "pages: \[PersistedAlbumPageData\]" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift && \
    grep -q "AlbumPageManagementSheetView" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 24. iOS CollectionScreenView ensurePageStructure
check_step "24" "Checking iOS CollectionScreenView ensurePageStructure wiring" '
    grep -q "ensurePageStructure" iosApp/iosApp/Features/Collection/CollectionScreenView.swift && \
    grep -q "pages: pages" iosApp/iosApp/Features/Collection/CollectionScreenView.swift
'

# 25. iOS AlbumPageLifecycleTests exists
check_step "25" "Checking iOS AlbumPageLifecycleTests exists" '
    test -f iosApp/iosAppTests/AlbumPageLifecycleTests.swift && \
    grep -q "class AlbumPageLifecycleTests" iosApp/iosAppTests/AlbumPageLifecycleTests.swift
'

# 26. Localization keys exist in all 5 files
check_step "26" "Checking localization keys across Android, iOS and xcstrings" '
    grep -q "album_editor_pages_title" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_pages_title" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "album_editor_pages_title" iosApp/iosApp/en.lproj/Localizable.strings && \
    grep -q "album_editor_pages_title" iosApp/iosApp/vi.lproj/Localizable.strings && \
    grep -q "album_editor_pages_title" iosApp/iosApp/Localizable.xcstrings && \
    grep -q "album_editor_page_has_stamps_error" androidApp/src/main/res/values/strings.xml && \
    grep -q "album_editor_page_has_stamps_error" iosApp/iosApp/Localizable.xcstrings
'

# 27. No hardcoded "(Current)"
check_step "27" "Verifying no hardcoded (Current) strings in toolbars" '
    ! grep -q " (Current)" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/editor/AlbumEditorToolbar.kt && \
    ! grep -q " (Current)" iosApp/iosApp/Features/Collection/Editor/AlbumEditorToolbarView.swift
'

# 28. Xcode project registers AlbumPageManagementSheetView.swift
check_step "28" "Checking Xcode project.pbxproj registers AlbumPageManagementSheetView.swift" '
    grep -q "AlbumPageManagementSheetView.swift in Sources" iosApp/iosApp.xcodeproj/project.pbxproj && \
    grep -q "AlbumPageManagementSheetView.swift" iosApp/iosApp.xcodeproj/project.pbxproj
'

echo "=========================================================="
if [ $FAILED_CHECKS -eq 0 ]; then
    echo "  ALL CHECKS PASSED (28/28)! Ready for testing & verification."
    exit 0
else
    echo "  $FAILED_CHECKS CHECKS FAILED! Please inspect errors above."
    exit 1
fi
