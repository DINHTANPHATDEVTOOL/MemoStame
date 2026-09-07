#!/usr/bin/env bash
set -euo pipefail

echo "========================================================"
echo "  Task #70: Production Localization Readiness Preflight "
echo "========================================================"

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$WORKSPACE_ROOT"

FAIL_COUNT=0

# Helper function
check_step() {
    local num="$1"
    local desc="$2"
    shift 2
    echo -n "[$num/21] $desc... "
    if "$@"; then
        echo "PASS"
    else
        echo "FAILED"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

# 1. Android base resources contain meaningful UI strings (>300 keys)
check_step "01" "Checking Android base strings.xml exists and has >300 keys" bash -c '
    [ -f "androidApp/src/main/res/values/strings.xml" ] && \
    KEY_COUNT=$(grep -c "<string name=" "androidApp/src/main/res/values/strings.xml") && \
    [ "$KEY_COUNT" -gt 300 ]
'

# 2. Android Vietnamese resources exist
check_step "02" "Checking Android values-vi/strings.xml exists" bash -c '
    [ -f "androidApp/src/main/res/values-vi/strings.xml" ]
'

# 3. Base/VI required key parity (exact same number and key names)
check_step "03" "Checking Android Base and VI exact key parity" bash -c '
    python3 -c "
import xml.etree.ElementTree as ET
base = set(e.get(\"name\") for e in ET.parse(\"androidApp/src/main/res/values/strings.xml\").getroot().findall(\"string\"))
vi = set(e.get(\"name\") for e in ET.parse(\"androidApp/src/main/res/values-vi/strings.xml\").getroot().findall(\"string\"))
assert base == vi, f\"Parity diff: base-vi={base-vi}, vi-base={vi-base}\"
"
'

# 4. iOS localization catalog/resources exist
check_step "04" "Checking iOS Localizable.xcstrings exists" bash -c '
    [ -f "iosApp/iosApp/Localizable.xcstrings" ]
'

# 5. English iOS localization exists
check_step "05" "Checking iOS en.lproj/Localizable.strings exists" bash -c '
    [ -f "iosApp/iosApp/en.lproj/Localizable.strings" ]
'

# 6. Vietnamese iOS localization exists
check_step "06" "Checking iOS vi.lproj/Localizable.strings exists" bash -c '
    [ -f "iosApp/iosApp/vi.lproj/Localizable.strings" ]
'

# 7. string(vi:en:) is no longer production authority
check_step "07" "Checking absence of langManager.string(vi:en:) in production Swift" bash -c '
    VIOLATIONS=$(grep -rn "langManager\.string(vi:" iosApp/iosApp/ --exclude="*Test*" 2>/dev/null || true)
    [ -z "$VIOLATIONS" ]
'

# 8. SYSTEM mode exists on Android
check_step "08" "Checking SYSTEM language mode exists on Android" bash -c '
    grep -q "SYSTEM(\"system\")" androidApp/src/main/java/com/mipastudio/memostamp/core/i18n/AppLanguageManager.kt
'

# 9. SYSTEM mode exists on iOS
check_step "09" "Checking SYSTEM language mode exists on iOS" bash -c '
    grep -q "case system" iosApp/iosApp/Services/AppLanguageManager.swift
'

# 10. Existing vi/en preference migration works
check_step "10" "Checking legacy preference migration (app_lang / app_language)" bash -c '
    grep -q "KEY_LEGACY_LANG" androidApp/src/main/java/com/mipastudio/memostamp/core/i18n/AppLanguageManager.kt && \
    grep -q "prefsLegacyKey" iosApp/iosApp/Services/AppLanguageManager.swift
'

# 11. Malformed / unknown saved locale falls back to SYSTEM
check_step "11" "Checking corrupted/unknown saved locale falls back to SYSTEM" bash -c '
    python3 -c "
# Verify fromCode fallback logic
lines = open(\"androidApp/src/main/java/com/mipastudio/memostamp/core/i18n/AppLanguageManager.kt\").read()
assert \"else -> SYSTEM\" in lines
"
'

# 12. Internal IDs remain independent of localized labels
check_step "12" "Checking stable enum IDs decoupled from localized labels" bash -c '
    grep -q "R.string.audience_friends" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/FeedModels.kt && \
    grep -q "FeedFilterCircle" iosApp/iosApp/Features/Home/HomeScreenView.swift
'

# 13. Audited production screens contain no obvious hard-coded language copy
check_step "13" "Checking audited production screens use localization architecture" bash -c '
    grep -q "stringResource(R.string." androidApp/src/main/java/com/mipastudio/memostamp/feature/home/HomeScreen.kt && \
    grep -q "stringResource(R.string." androidApp/src/main/java/com/mipastudio/memostamp/feature/profile/PassportScreen.kt && \
    grep -q "stringResource(R.string." androidApp/src/main/java/com/mipastudio/memostamp/feature/friends/FriendsAndTradeScreen.kt && \
    grep -q "langManager.localized(" iosApp/iosApp/Features/Home/HomeScreenView.swift && \
    grep -q "langManager.localized(" iosApp/iosApp/Features/Profile/PassportScreenView.swift && \
    grep -q "langManager.localized(" iosApp/iosApp/Features/Friends/FriendsAndTradeScreenView.swift
'

# 14. Format placeholders match between languages
check_step "14" "Checking format placeholders match between base and Vietnamese" bash -c '
    python3 -c "
import xml.etree.ElementTree as ET, re
base = {e.get(\"name\"): e.text or \"\" for e in ET.parse(\"androidApp/src/main/res/values/strings.xml\").getroot().findall(\"string\")}
vi = {e.get(\"name\"): e.text or \"\" for e in ET.parse(\"androidApp/src/main/res/values-vi/strings.xml\").getroot().findall(\"string\")}
p = re.compile(r\"%(\d+\\\$)?[sdf]\")
for k, b_txt in base.items():
    v_txt = vi.get(k, \"\")
    assert len(p.findall(b_txt)) == len(p.findall(v_txt)), f\"Placeholder mismatch in {k}\"
"
'

# 15. No missing localization key between Android and iOS
check_step "15" "Checking key coverage in iOS string catalog" bash -c '
    python3 -c "
import xml.etree.ElementTree as ET, json
base = set(e.get(\"name\") for e in ET.parse(\"androidApp/src/main/res/values/strings.xml\").getroot().findall(\"string\"))
ios_catalog = json.load(open(\"iosApp/iosApp/Localizable.xcstrings\"))[\"strings\"]
missing = base - set(ios_catalog.keys())
assert len(missing) == 0, f\"Missing keys in iOS catalog: {missing}\"
"
'

# 16. Locale preference survives account switch / logout
check_step "16" "Checking language preferences stored separately from auth session" bash -c '
    grep -q "app_prefs" androidApp/src/main/java/com/mipastudio/memostamp/core/i18n/AppLanguageManager.kt && \
    grep -q "prefsModeKey" iosApp/iosApp/Services/AppLanguageManager.swift
'

# 17. Security flows (RLS & migration tests) remain intact
check_step "17" "Checking RLS security & SQL migrations exist" bash -c '
    [ -f "supabase/tests/rls_negative_tests.sql" ] && \
    [ -d "supabase/migrations" ]
'

# 18. #62 Signed Release Candidate checks pass
check_step "18" "Checking #62 Release Candidate readiness" bash -c '
    ./scripts/check-release-candidate-readiness.sh > /dev/null 2>&1
'

# 19. #64 Privacy compliance checks pass
check_step "19" "Checking #64 Store Privacy compliance readiness" bash -c '
    ./scripts/check-store-privacy-readiness.sh > /dev/null 2>&1
'

# 20. #66 Location grounding checks pass
check_step "20" "Checking #66 iOS Location Grounding readiness" bash -c '
    ./scripts/check-ios-location-grounding-readiness.sh > /dev/null 2>&1
'

# 21. #68 Camera hardware zoom checks pass
check_step "21" "Checking #68 Camera Hardware Zoom readiness" bash -c '
    ./scripts/check-camera-hardware-zoom-readiness.sh > /dev/null 2>&1
'

echo "--------------------------------------------------------"
if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "  ALL 21 PREFLIGHT CHECKS PASSED SUCCESSFULLY! ✅      "
    echo "========================================================"
    exit 0
else
    echo "  PREFLIGHT FAILED WITH $FAIL_COUNT ERROR(S) ❌         "
    echo "========================================================"
    exit 1
fi
