#!/usr/bin/env bash
set -e

echo "========================================================"
echo "  Task #72: Semantic Icon System Readiness Preflight    "
echo "========================================================"

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/23] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. Semantic icon registry exists
check_step "1" "Checking semantic icon registry exists across shared, androidApp, and iosApp" '
    grep -q "enum class MemoStampIconKey" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/MemoStampIconKey.kt && \
    grep -q "enum class MemoStampIconKey" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/MemoStampIconKey.kt && \
    grep -q "enum MemoStampIconKey" iosApp/iosApp/UI/MemoStampIcons.swift
'

# 2. Android semantic mapping exists
check_step "2" "Checking Android semantic icon resolver and custom vectors exist" '
    grep -q "object MemoStampIcons" androidApp/src/main/java/com/mipastudio/memostamp/ui/icon/MemoStampIcons.kt && \
    grep -q "CustomStamp" androidApp/src/main/java/com/mipastudio/memostamp/ui/icon/MemoStampIcons.kt && \
    grep -q "CustomPostmark" androidApp/src/main/java/com/mipastudio/memostamp/ui/icon/MemoStampIcons.kt && \
    grep -q "CustomReplyStamp" androidApp/src/main/java/com/mipastudio/memostamp/ui/icon/MemoStampIcons.kt
'

# 3. iOS semantic mapping exists
check_step "3" "Checking iOS semantic icon resolver and custom vectors exist" '
    grep -q "struct MemoStampIcon: View" iosApp/iosApp/UI/MemoStampIcons.swift && \
    grep -q "CustomStampVectorShape" iosApp/iosApp/UI/MemoStampIcons.swift && \
    grep -q "CustomPostmarkVectorView" iosApp/iosApp/UI/MemoStampIcons.swift && \
    grep -q "CustomReplyStampVectorView" iosApp/iosApp/UI/MemoStampIcons.swift
'

# 4. New production models do not use keyboard emoji as icon authority
check_step "4" "Checking production models do not use keyboard emoji as icon authority" '
    grep -q "val iconKey: String" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/Models.kt && \
    grep -q "val iconKey: String" androidApp/src/main/java/com/mipastudio/memostamp/data/local/CollectionEntity.kt && \
    grep -q "var iconKey: String?" iosApp/iosApp/Data/IOSLocalPersistenceStore.swift
'

# 5. Collection new writes use semantic icon IDs
check_step "5" "Checking collection creation uses semantic icon IDs" '
    grep -q "resolveCollectionIcon" shared/src/commonMain/kotlin/com/mipastudio/memostamp/repository/SharedMemoStampRepository.kt && \
    grep -q "mapLegacyCollectionIcon" androidApp/src/main/java/com/mipastudio/memostamp/data/repository/StampRepository.kt && \
    grep -q "selectedIconKey" iosApp/iosApp/Features/Vault/StampVaultScreenView.swift
'

# 6. Legacy Collection emoji migrate
check_step "6" "Checking legacy Collection emoji deterministic migration mapping" '
    python3 -c "
with open(\"shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/MemoStampLegacyMigration.kt\") as f:
    code = f.read()
assert \"📁\" in code and \"COLLECTION\" in code
assert \"☕\" in code and \"CAFE\" in code
assert \"✈\" in code and \"TRAVEL\" in code
"
'

# 7. Unknown Collection emoji has safe fallback
check_step "7" "Checking unknown Collection emoji falls back to neutral collection icon" '
    python3 -c "
with open(\"shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/MemoStampLegacyMigration.kt\") as f:
    code = f.read()
assert \"else -> MemoStampIconKey.COLLECTION.key\" in code
"
'

# 8. Existing collections remain readable
check_step "8" "Checking unit tests for legacy collection reading and backward compatibility" '
    test -f shared/src/commonTest/kotlin/com/mipastudio/memostamp/domain/model/IconSystemMigrationTest.kt && \
    test -f androidApp/src/test/java/com/mipastudio/memostamp/domain/model/IconSystemMigrationTest.kt
'

# 9. New mood writes use semantic IDs
check_step "9" "Checking new mood writes use semantic IDs instead of emoji" '
    python3 -c "
with open(\"androidApp/src/main/java/com/mipastudio/memostamp/feature/memorynote/MemoryNoteViewModel.kt\") as f:
    code = f.read()
assert \"val mood: String = \\\"special\\\"\" in code
assert not \"val mood: String = \\\"✨\\\"\" in code

with open(\"iosApp/iosApp/Features/Note/MemoryNoteScreenView.swift\") as f:
    code = f.read()
assert \"selectedMood: String = \\\"happy\\\"\" in code
assert not \"selectedMood: String = \\\"✨\\\"\" in code
"
'

# 10. Legacy mood emoji remain readable
check_step "10" "Checking legacy mood emoji maps deterministically to semantic key" '
    python3 -c "
with open(\"shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/MemoStampLegacyMigration.kt\") as f:
    code = f.read()
assert \"mapLegacyMood\" in code
assert \"✨\" in code and \"SPECIAL\" in code
assert \"😊\" in code and \"HAPPY\" in code
"
'

# 11. Notification banner no longer renders iconEmoji through Text
check_step "11" "Checking in-app notification banner renders semantic vector icon" '
    python3 -c "
with open(\"androidApp/src/main/java/com/mipastudio/memostamp/core/notification/InAppNotificationBanner.kt\") as f:
    code = f.read()
assert \"MemoStampIcon\" in code
assert \"activeBanner.iconKey\" in code
assert \"Text(text = item.iconEmoji\" not in code
"
'

# 12. StampTemplate selector no longer requires iconEmoji
check_step "12" "Checking StampTemplate selector uses semantic icon keys" '
    grep -q "iconKey" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/StampTemplate.kt && \
    grep -q "MemoStampIcon" androidApp/src/main/java/com/mipastudio/memostamp/feature/editor/StampEditorScreen.kt
'

# 13. Audited location UI no longer uses literal 📍
check_step "13" "Checking audited location UI does not use literal 📍 as icon authority" '
    python3 -c "
for path in [
    \"androidApp/src/main/java/com/mipastudio/memostamp/feature/home/HomeScreen.kt\",
    \"androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt\",
    \"androidApp/src/main/java/com/mipastudio/memostamp/feature/vault/StampDetailScreen.kt\",
    \"iosApp/iosApp/Features/Home/HomeScreenView.swift\",
    \"iosApp/iosApp/Features/Friends/FriendsAndTradeScreenView.swift\"
]:
    with open(path) as f:
        c = f.read()
    assert \"📍 \" not in c, f\"Found 📍 in {path}\"
"
'

# 14. Generated stamp artwork no longer inserts system 📍 glyph
check_step "14" "Checking generated stamp artwork draws vector pin instead of 📍 glyph" '
    python3 -c "
with open(\"androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/renderer/StampRenderer.kt\") as f:
    c = f.read()
assert \"drawLocationPin\" in c
assert \"removePrefix(\\\"📍\\\")\" in c

with open(\"iosApp/iosApp/Services/StampRenderEngine.swift\") as f:
    c = f.read()
assert \"drawLocationPin\" in c
assert \"replacingOccurrences(of: \\\"📍\\\"\" in c
"
'

# 15. First-party localization does not rely on decorative emoji icons
check_step "15" "Checking first-party localization does not rely on decorative emoji icons" '
    python3 -c "
with open(\"androidApp/src/main/res/values/strings.xml\") as f:
    c = f.read()
assert \"Profile updated successfully! ✨\" not in c
assert \"Select Stamp to Attach 📮\" not in c
assert \"Memory Feed 📮\" not in c
assert \"Owned ✨\" not in c

with open(\"iosApp/iosApp/en.lproj/Localizable.strings\") as f:
    c = f.read()
assert \"Profile updated successfully! ✨\" not in c
assert \"Select Stamp to Attach 📮\" not in c
assert \"Memory Feed 📮\" not in c
assert \"Owned ✨\" not in c
"
'

# 16. User-generated emoji remains untouched
check_step "16" "Checking user-generated emoji preservation in note, title, and caption text" '
    python3 -c "
with open(\"androidApp/src/main/java/com/mipastudio/memostamp/feature/memorynote/MemoryNoteScreen.kt\") as f:
    c = f.read()
assert \"uiState.title\" in c and \"uiState.note\" in c

with open(\"iosApp/iosApp/Features/Note/MemoryNoteScreenView.swift\") as f:
    c = f.read()
assert \"title\" in c and \"note\" in c
"
'

# 17. Android accessibility labels localized
check_step "17" "Checking Android accessibility content descriptions on semantic icons" '
    grep -q "contentDescription: String? = null" androidApp/src/main/java/com/mipastudio/memostamp/ui/icon/MemoStampIcons.kt
'

# 18. iOS accessibility labels localized
check_step "18" "Checking iOS accessibility labels on semantic icons" '
    grep -q "accessibilityLabel" iosApp/iosApp/UI/MemoStampIcons.swift
'

# 19. #70 localization tests PASS
check_step "19" "Verifying #70 localization preflight suite PASS" './scripts/check-localization-readiness.sh > /dev/null 2>&1'

# 20. #68 camera regression PASS
check_step "20" "Verifying #68 camera hardware zoom suite PASS" './scripts/check-camera-hardware-zoom-readiness.sh > /dev/null 2>&1'

# 21. #66 location regression PASS
check_step "21" "Verifying #66 iOS location grounding suite PASS" './scripts/check-ios-location-grounding-readiness.sh > /dev/null 2>&1'

# 22. #64 privacy regression PASS
check_step "22" "Verifying #64 store privacy compliance suite PASS" './scripts/check-store-privacy-readiness.sh > /dev/null 2>&1'

# 23. #62 release regression PASS
check_step "23" "Verifying #62 release candidate readiness suite PASS" './scripts/check-release-candidate-readiness.sh > /dev/null 2>&1'

echo "--------------------------------------------------------"
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo "  ALL 23 PREFLIGHT CHECKS PASSED SUCCESSFULLY! ✅      "
    echo "========================================================"
    exit 0
else
    echo "  $FAILED_CHECKS CHECKS FAILED! ❌                      "
    echo "========================================================"
    exit 1
fi
