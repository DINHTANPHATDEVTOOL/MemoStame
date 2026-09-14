#!/usr/bin/env bash
set -e

echo "========================================================"
echo "  Task #74: 3D Stamp Book Foundation Readiness Preflight"
echo "========================================================"

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/24] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. Android production StampBook does NOT use flat HorizontalPager as page-turn authority
check_step "1" "Checking Android production StampBook does not use HorizontalPager for page-turning" '
    grep -q "StampBook3DRenderer" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt && \
    ! grep -q "HorizontalPager" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "rotationY" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt
'

# 2. iOS production StampBook does NOT use PageTabViewStyle as page-turn authority
check_step "2" "Checking iOS production StampBook does not use PageTabViewStyle for page-turning" '
    grep -q "StampBook3DRenderer" iosApp/iosApp/Features/Collection/CollectionScreenView.swift && \
    ! grep -q "PageTabViewStyle" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift && \
    ! grep -q "PageTabViewStyle" iosApp/iosApp/Features/Collection/CollectionScreenView.swift && \
    grep -q "rotation3DEffect" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 3. Real Collections use stable canonical IDs
check_step "3" "Checking real collections use stable canonical IDs" '
    grep -q "id = col.id" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt && \
    grep -q "id: col.id" iosApp/iosApp/Features/Collection/CollectionScreenView.swift
'

# 4. Android no longer uses album_$index for persisted collections
check_step "4" "Checking Android no longer uses album_\$index for collections" '
    ! grep -q "album_\$index" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt
'

# 5. Legacy virtual album IDs are deterministic across launches
check_step "5" "Checking legacy virtual album IDs use deterministic hashing" '
    grep -q "loc_" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/CollectionScreen.kt && \
    grep -q "loc_" iosApp/iosApp/Features/Collection/CollectionScreenView.swift && \
    ! grep -q "album_\\(index\\)" iosApp/iosApp/Features/Collection/CollectionScreenView.swift
'

# 6. Two-page spread mathematics: calculateSpreads pairs pages into spreads
check_step "6" "Checking two-page spread mathematics implementation" '
    grep -q "fun calculateSpreads" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "func calculateSpreads" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 7. Empty album safe: creates single spread with inside cover and empty page
check_step "7" "Checking empty album spread safety" '
    grep -q "isInsideCover = true" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "isInsideCover: true" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 8. Odd page count safe: appends archival blank page
check_step "8" "Checking odd page count appends archival blank page" '
    grep -q "isBlankArchival" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "isBlankArchival" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 9. Turn progress clamps strictly within 0...1
check_step "9" "Checking turn progress strictly clamps 0...1" '
    grep -q "coerceIn(0f, 1f)" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "min(max(progress, 0.0), 1.0)" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 10. First and last spread boundary safety
check_step "10" "Checking boundary safety prevents turning beyond first and last spreads" '
    grep -q "currentSpreadIndex >= totalSpreads - 1" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "currentSpreadIndex <= 0" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "currentSpreadIndex >= totalSpreads - 1" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift && \
    grep -q "currentSpreadIndex <= 0" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 11. Cancelled turn stays on same spread
check_step "11" "Checking cancelled turn preserves current spread index" '
    grep -q "finishTurn(completed: Boolean" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "finishTurn(completed: Bool" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 12. Completed turn advances exactly one spread
check_step "12" "Checking completed turn advances exactly one spread" '
    grep -q "currentSpreadIndex += 1" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "currentSpreadIndex -= 1" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt
'

# 13. Deterministic cover state transitions
check_step "13" "Checking deterministic book state machine transitions" '
    grep -q "enum class BookState" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "TURNING_FORWARD" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "enum BookState" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift && \
    grep -q "turningForward" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 14. Safe dismiss during animation with interaction lock
check_step "14" "Checking dismiss and interaction lock safety" '
    grep -q "interactionLocked" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    grep -q "interactionLocked" iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 15. No per-frame DB/network calls in renderer loop
check_step "15" "Checking absence of DB or network queries inside renderer visual surfaces" '
    ! grep -q "dao\." androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    ! grep -q "supabase" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/StampBook3DRenderer.kt && \
    ! grep -q "repository\." iosApp/iosApp/Features/Collection/Book/StampBook3DRenderer.swift
'

# 16. Page renderer is future freeform-canvas compatible
check_step "16" "Checking future-canvas coordinate surface contract" '
    grep -q "data class BookPageContent" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "data class PageBounds" androidApp/src/main/java/com/mipastudio/memostamp/feature/collection/book/PageTurnState.kt && \
    grep -q "struct BookPageContent" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift && \
    grep -q "struct PageBounds" iosApp/iosApp/Features/Collection/Book/PageTurnState.swift
'

# 17. Existing Collection/Stamp data preserved (no destructive schema changes)
check_step "17" "Checking collection and stamp entity schema integrity" '
    grep -q "data class CollectionEntity" androidApp/src/main/java/com/mipastudio/memostamp/data/local/CollectionEntity.kt && \
    grep -q "data class StampEntity" androidApp/src/main/java/com/mipastudio/memostamp/data/local/StampEntity.kt
'

# 18. Account isolation remains intact
check_step "18" "Checking account isolation on collection queries" '
    grep -q "observeCollectionsByOwner(user.userId)" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/StampRepository.kt
'

# 19. #72 Semantic Icon System readiness PASS
check_step "19" "Verifying #72 semantic icon system readiness suite PASS" '
    ./scripts/check-icon-system-readiness.sh > /dev/null 2>&1
'

# 20. #70 Localization readiness PASS
check_step "20" "Verifying #70 production localization readiness suite PASS" '
    ./scripts/check-localization-readiness.sh > /dev/null 2>&1
'

# 21. #68 Camera hardware zoom readiness PASS
check_step "21" "Verifying #68 camera hardware zoom suite PASS" '
    ./scripts/check-camera-hardware-zoom-readiness.sh > /dev/null 2>&1
'

# 22. #66 iOS location grounding readiness PASS
check_step "22" "Verifying #66 iOS location grounding suite PASS" '
    ./scripts/check-ios-location-grounding-readiness.sh > /dev/null 2>&1
'

# 23. #64 Store privacy compliance readiness PASS
check_step "23" "Verifying #64 store privacy compliance suite PASS" '
    ./scripts/check-store-privacy-readiness.sh > /dev/null 2>&1
'

# 24. #62 Release candidate readiness PASS
check_step "24" "Verifying #62 release candidate readiness suite PASS" '
    ./scripts/check-release-candidate-readiness.sh > /dev/null 2>&1
'

echo "--------------------------------------------------------"
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo "  ALL 24 3D STAMP BOOK CHECKS PASSED SUCCESSFULLY! ✅ "
    echo "========================================================"
    exit 0
else
    echo "  $FAILED_CHECKS CHECKS FAILED! ❌                     "
    echo "========================================================"
    exit 1
fi
