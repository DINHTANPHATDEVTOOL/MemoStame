#!/usr/bin/env bash
set -e

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

echo "=========================================================="
echo "  Task #76: 3D Album Placement Persistence Readiness Suite"
echo "=========================================================="

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/25] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. Supabase migration 012 exists and creates album_pages and album_stamp_placements
check_step "1" "Checking migration 012 schema definitions" '
    test -f supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "CREATE TABLE IF NOT EXISTS public.album_pages" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "CREATE TABLE IF NOT EXISTS public.album_stamp_placements" supabase/migrations/012_album_layout_persistence.sql
'

# 2. Server-side normalized geometry and bounding constraints
check_step "2" "Checking server-side geometry constraints and limits" '
    grep -q "x >= 0.0 AND x <= 1.0" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "y >= 0.0 AND y <= 1.0" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "scale > 0.05 AND scale <= 5.0" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "rotation_degrees >= -360.0 AND rotation_degrees <= 360.0" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "z_index >= -1000 AND z_index <= 1000" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "page_index >= 0" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "NOT isnan(x)" supabase/migrations/012_album_layout_persistence.sql
'

# 3. Composite FK and account deletion cascade
check_step "3" "Checking composite foreign key and account cascade constraints" '
    grep -q "REFERENCES public.profiles(id) ON DELETE CASCADE" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "album_stamp_placements_page_fk" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "REFERENCES public.album_pages" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "ON DELETE CASCADE ON UPDATE CASCADE" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "UNIQUE (owner_id, album_id, stamp_id)" supabase/migrations/012_album_layout_persistence.sql
'

# 4. Strict Row-Level Security on layout tables
check_step "4" "Checking RLS policies and zero anon access" '
    grep -q "ENABLE ROW LEVEL SECURITY" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "auth.uid() = owner_id" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "REVOKE ALL ON TABLE public.album_pages FROM anon" supabase/migrations/012_album_layout_persistence.sql && \
    grep -q "REVOKE ALL ON TABLE public.album_stamp_placements FROM anon" supabase/migrations/012_album_layout_persistence.sql && \
    ! grep -q "acting_uid" supabase/migrations/012_album_layout_persistence.sql
'

# 5. RLS negative test suite updated with all #76 assertions
check_step "5" "Checking RLS negative tests coverage for album layout" '
    grep -q "ANON CANNOT SELECT ALBUM PAGES" supabase/tests/rls_negative_tests.sql && \
    grep -q "USER A CAN CREATE OWN PLACEMENT" supabase/tests/rls_negative_tests.sql && \
    grep -q "USER B CANNOT SELECT USER A PLACEMENT" supabase/tests/rls_negative_tests.sql && \
    grep -q "NEGATIVE PAGE_INDEX REJECTED" supabase/tests/rls_negative_tests.sql && \
    grep -q "DUPLICATE STAMP IN ALBUM REJECTED" supabase/tests/rls_negative_tests.sql
'

# 6. Multi-user contract E2E test harness updated
check_step "6" "Checking black-box multi-user E2E contract tests" '
    grep -q "phase15_album_layout_persistence" supabase/tests/e2e_contract_tests.py && \
    grep -q "self.phase15_album_layout_persistence()" supabase/tests/e2e_contract_tests.py
'

# 7. Domain models in Shared KMP and Android
check_step "7" "Checking AlbumPage and StampPlacement domain models" '
    grep -q "data class AlbumPage" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/Models.kt && \
    grep -q "data class StampPlacement" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/Models.kt && \
    grep -q "data class AlbumPage" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/Models.kt && \
    grep -q "data class StampPlacement" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/Models.kt
'

# 8. Android Room Entities & DAO
check_step "8" "Checking Android Room entity schemas and Dao" '
    grep -q "data class AlbumPageEntity" androidApp/src/main/java/com/mipastudio/memostamp/data/local/AlbumPageEntity.kt && \
    grep -q "data class StampPlacementEntity" androidApp/src/main/java/com/mipastudio/memostamp/data/local/StampPlacementEntity.kt && \
    grep -q "interface AlbumLayoutDao" androidApp/src/main/java/com/mipastudio/memostamp/data/local/AlbumLayoutDao.kt
'

# 9. Android Safe Room Migration 14->15
check_step "9" "Checking safe non-destructive Room database migration 14->15" '
    grep -q "val MIGRATION_14_15" androidApp/src/main/java/com/mipastudio/memostamp/data/local/MemoStampDatabase.kt && \
    grep -q "version = 15" androidApp/src/main/java/com/mipastudio/memostamp/data/local/MemoStampDatabase.kt && \
    ! grep -q "fallbackToDestructiveMigration" androidApp/src/main/java/com/mipastudio/memostamp/data/local/MemoStampDatabase.kt
'

# 10. Android AlbumLayoutRepository implementation
check_step "10" "Checking Android AlbumLayoutRepository operations" '
    grep -q "class AlbumLayoutRepository" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "fun observeLayout" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun upsertPage" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun upsertPlacement" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun updatePlacementTransform" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun movePlacementToPage" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "suspend fun deletePlacement" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt
'

# 11. Android Account-switch race guard
check_step "11" "Checking Android account-switch race guard implementation" '
    grep -q "sessionGeneration" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "Account switch race guard" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt
'

# 12. iOS Local Persistence & Backward Compatibility
check_step "12" "Checking iOS local persistence backward compatibility" '
    grep -q "struct PersistedAlbumPageData" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift && \
    grep -q "struct PersistedStampPlacementData" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift && \
    grep -q "let albumPages: \[PersistedAlbumPageData\]\?" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift && \
    grep -q "let albumPlacements: \[PersistedStampPlacementData\]\?" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift
'

# 13. iOS AlbumLayoutRepository implementation
check_step "13" "Checking iOS IOSAlbumLayoutRepository operations" '
    grep -q "class IOSAlbumLayoutRepository" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func loadLocalLayout" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func syncLayout" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func upsertPage" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func upsertPlacement" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func updatePlacementTransform" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func movePlacementToPage" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift && \
    grep -q "func deletePlacement" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift
'

# 14. iOS Session Lifecycle Hook
check_step "14" "Checking iOS session change hooks for layout repository" '
    grep -q "IOSAlbumLayoutRepository.shared.onSessionChanged" iosApp/iosApp/Data/SupabaseAuthService.swift
'

# 15. Xcode project registration
check_step "15" "Checking Xcode project registers IOSAlbumLayoutRepository.swift" '
    grep -q "IOSAlbumLayoutRepository.swift" iosApp/iosApp.xcodeproj/project.pbxproj
'

# 16. Logical page-index authority (pageIndex = 0 is first editable inner page)
check_step "16" "Checking page index authority across Android and iOS" '
    grep -q "pageIndex = 0 means first editable inner page" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "pageIndex = 0 means first editable inner page" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 17. Virtual album isolation: synthetic albums never write cloud layouts
check_step "17" "Checking virtual album isolation from cloud persistence" '
    grep -q "isVirtualAlbum(albumId)" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/AlbumLayoutRepository.kt && \
    grep -q "isVirtualAlbum(albumId)" iosApp/iosApp/Data/IOSAlbumLayoutRepository.swift
'

# 18. Normalized coordinates mapping to editable page bounds
check_step "18" "Checking normalized coordinate mapping in renderers" '
    grep -q "pageWidth \* placement.x" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "pageHeight \* placement.y" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "pageWidth \* CGFloat(placement.x)" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift && \
    grep -q "pageHeight \* CGFloat(placement.y)" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 19. Deterministic z-order rendering with stable ID tie-break
check_step "19" "Checking deterministic z-order sorting in renderers" '
    grep -q "it.zIndex" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "thenBy { it.id }" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "\$0.zIndex < \$1.zIndex" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 20. Unit tests: Android and iOS persistence test suites exist
check_step "20" "Checking automated persistence test suites" '
    test -f androidApp/src/test/java/com/mipastudio/memostamp/feature/collection/AlbumLayoutPersistenceTest.kt && \
    test -f iosApp/iosAppTests/AlbumLayoutPersistenceTests.swift
'

# 21. #74 3D Stamp Book foundation readiness PASS
check_step "21" "Verifying #74 3D stamp book readiness suite PASS" '
    bash ./scripts/check-3d-stamp-book-readiness.sh > /dev/null 2>&1
'

# 22. #72 Semantic Icon System readiness PASS
check_step "22" "Verifying #72 semantic icon system readiness suite PASS" '
    bash ./scripts/check-icon-system-readiness.sh > /dev/null 2>&1
'

# 23. #70 Localization readiness PASS
check_step "23" "Verifying #70 production localization readiness suite PASS" '
    bash ./scripts/check-localization-readiness.sh > /dev/null 2>&1
'

# 24. #68 Camera hardware zoom readiness PASS
check_step "24" "Verifying #68 camera hardware zoom suite PASS" '
    bash ./scripts/check-camera-hardware-zoom-readiness.sh > /dev/null 2>&1
'

# 25. #66 & #64 & #62 store privacy and release readiness PASS
check_step "25" "Verifying #66, #64, and #62 regression suites PASS" '
    bash ./scripts/check-ios-location-grounding-readiness.sh > /dev/null 2>&1 && \
    bash ./scripts/check-store-privacy-readiness.sh > /dev/null 2>&1 && \
    bash ./scripts/check-release-candidate-readiness.sh > /dev/null 2>&1
'

echo "----------------------------------------------------------"
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo "  ALL 25 ALBUM PLACEMENT PERSISTENCE CHECKS PASSED! ✅ "
    echo "=========================================================="
    exit 0
else
    echo "  $FAILED_CHECKS CHECKS FAILED! ❌                     "
    echo "=========================================================="
    exit 1
fi
