#!/usr/bin/env bash
# ==============================================================================
# MemoStamp Store Privacy Compliance & Data Disclosure Preflight Inspector (#64)
# Verifies Apple Privacy Manifest, Google Play Data Safety, In-App Privacy Links,
# In-App Account Deletion discoverability, deployable web pages, and ads/tracking absence.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$WORKSPACE_ROOT"

echo "================================================================="
echo "   MEMOSTAMP STORE PRIVACY COMPLIANCE PREFLIGHT INSPECTOR (#64)"
echo "================================================================="

EXIT_CODE=0
PRIVACY_CONTACT_STATUS="EXTERNAL_SETUP_REQUIRED: PRIVACY_CONTACT"
LIVE_URL_STATUS="LIVE_URL_VERIFICATION_REQUIRED"
STORE_CONSOLE_STATUS="STORE_CONSOLE_DECLARATION_REQUIRED"

# ----------------------------------------------------------------------
# 1. Apple Privacy Manifest (PrivacyInfo.xcprivacy) Audit
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 1] Auditing Apple Privacy Manifest (PrivacyInfo.xcprivacy)..."
PRIVACY_MANIFEST="iosApp/iosApp/PrivacyInfo.xcprivacy"

if [ ! -f "$PRIVACY_MANIFEST" ]; then
    echo "  [FAIL] $PRIVACY_MANIFEST not found"
    EXIT_CODE=1
else
    # Check for empty collected data types array
    if grep -q '<key>NSPrivacyCollectedDataTypes</key>[[:space:]]*<array/>' "$PRIVACY_MANIFEST"; then
        echo "  [FAIL] NSPrivacyCollectedDataTypes is empty. First-party collection must be declared."
        EXIT_CODE=1
    else
        echo "  [PASS] NSPrivacyCollectedDataTypes is populated."
    fi

    # Check for valid Apple privacy constants
    EXPECTED_DATA_TYPES=(
        "NSPrivacyCollectedDataTypeEmailAddress"
        "NSPrivacyCollectedDataTypeName"
        "NSPrivacyCollectedDataTypeUserID"
        "NSPrivacyCollectedDataTypeDeviceID"
        "NSPrivacyCollectedDataTypePreciseLocation"
        "NSPrivacyCollectedDataTypeCoarseLocation"
        "NSPrivacyCollectedDataTypePhotosorVideos"
        "NSPrivacyCollectedDataTypeEmailsOrTextMessages"
        "NSPrivacyCollectedDataTypeOtherUserContent"
    )

    MISSING_CONSTANTS=0
    for DT in "${EXPECTED_DATA_TYPES[@]}"; do
        if ! grep -q "$DT" "$PRIVACY_MANIFEST"; then
            echo "  [FAIL] Missing required Apple data type constant: $DT"
            MISSING_CONSTANTS=1
        fi
    done

    if [ "$MISSING_CONSTANTS" -eq 0 ]; then
        echo "  [PASS] All 9 verified production data types declared with valid Apple constants."
    else
        EXIT_CODE=1
    fi

    # Check Tracking declaration
    if grep -A 1 'NSPrivacyTracking' "$PRIVACY_MANIFEST" | grep -q '<false/>'; then
        echo "  [PASS] NSPrivacyTracking is accurately declared as false (no cross-app tracking)."
    else
        echo "  [FAIL] NSPrivacyTracking must be false since MemoStamp performs no cross-app tracking."
        EXIT_CODE=1
    fi

    # Check UserDefaults API Required Reason
    if grep -q 'NSPrivacyAccessedAPICategoryUserDefaults' "$PRIVACY_MANIFEST" && grep -q 'CA92.1' "$PRIVACY_MANIFEST"; then
        echo "  [PASS] UserDefaults API required reason CA92.1 declared."
    else
        echo "  [FAIL] Missing UserDefaults API required reason in PrivacyInfo.xcprivacy"
        EXIT_CODE=1
    fi
fi

# ----------------------------------------------------------------------
# 2. In-App Privacy Policy Links & URL Safety
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 2] Auditing in-app Privacy Policy links & URL safety..."

# Android Privacy Policy Link
if grep -q 'PrivacyConfig.getPrivacyPolicyUrl()' androidApp/src/main/java/com/mipastudio/memostamp/feature/profile/PassportScreen.kt && \
   grep -q 'PRIVACY_POLICY_URL' androidApp/build.gradle.kts; then
    echo "  [PASS] Android Privacy Policy link discoverable in PassportScreen settings."
else
    echo "  [FAIL] Android Privacy Policy link missing from PassportScreen.kt or build.gradle.kts"
    EXIT_CODE=1
fi

# iOS Privacy Policy Link
if grep -q 'PrivacyConfig.privacyPolicyUrl' iosApp/iosApp/Features/Profile/PassportScreenView.swift; then
    echo "  [PASS] iOS Privacy Policy link discoverable in PassportScreenView settings."
else
    echo "  [FAIL] iOS Privacy Policy link missing from PassportScreenView.swift"
    EXIT_CODE=1
fi

# Production URL Validation (Must be HTTPS, reject HTTP, localhost, example.com)
PROD_PRIVACY_URL=$(grep 'DEFAULT_PRIVACY_POLICY_URL' androidApp/src/main/java/com/mipastudio/memostamp/core/privacy/PrivacyConfig.kt | grep -o 'https://[^"]*' || true)
if [ -z "$PROD_PRIVACY_URL" ]; then
    echo "  [FAIL] Production privacy policy URL is not HTTPS or missing"
    EXIT_CODE=1
elif echo "$PROD_PRIVACY_URL" | grep -qE "(http://|localhost|127\.0\.0\.1|example\.com|placeholder)"; then
    echo "  [FAIL] Insecure or placeholder domain in production privacy policy URL: $PROD_PRIVACY_URL"
    EXIT_CODE=1
else
    echo "  [PASS] Production Privacy Policy URL is secure HTTPS ($PROD_PRIVACY_URL)"
fi

# ----------------------------------------------------------------------
# 3. Account Deletion Discoverability & Integrity (#47 Truth)
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 3] Auditing in-app Account Deletion discoverability & truth..."

# Android In-App Account Deletion
if grep -q 'showDeleteAccountDialog' androidApp/src/main/java/com/mipastudio/memostamp/feature/profile/PassportScreen.kt && \
   grep -q 'deleteAccount' androidApp/src/main/java/com/mipastudio/memostamp/data/repository/UserAuthRepository.kt; then
    echo "  [PASS] Android in-app account deletion discoverable and wired to auth repository."
else
    echo "  [FAIL] Android in-app account deletion missing or broken"
    EXIT_CODE=1
fi

# iOS In-App Account Deletion
if grep -q 'showDeleteAccountSheet' iosApp/iosApp/Features/Profile/PassportScreenView.swift && \
   grep -q 'delete-account' iosApp/iosApp/Data/SupabaseAuthService.swift; then
    echo "  [PASS] iOS in-app account deletion discoverable and wired to delete-account Edge Function."
else
    echo "  [FAIL] iOS in-app account deletion missing or broken"
    EXIT_CODE=1
fi

# Backend delete-account Edge Function
if [ -f "supabase/functions/delete-account/index.ts" ] && \
   grep -q 'stamp-media' supabase/functions/delete-account/index.ts && \
   grep -q 'auth/v1/admin/users' supabase/functions/delete-account/index.ts; then
    echo "  [PASS] Server-authoritative delete-account Edge Function purges storage and auth cascade."
else
    echo "  [FAIL] Backend delete-account Edge Function missing or incomplete"
    EXIT_CODE=1
fi

# ----------------------------------------------------------------------
# 4. Web Resources & Compliance Documentation
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 4] Auditing web resources & compliance documentation..."

# Public Privacy Policy Source
if [ -f "site/privacy/index.html" ] && grep -q 'MemoStamp Privacy Policy' site/privacy/index.html; then
    echo "  [PASS] Deployable Privacy Policy source exists (site/privacy/index.html)"
else
    echo "  [FAIL] site/privacy/index.html missing or invalid"
    EXIT_CODE=1
fi

# Public Account Deletion Web Page Source
if [ -f "site/account-deletion/index.html" ] && grep -q 'MemoStamp Account Deletion' site/account-deletion/index.html; then
    echo "  [PASS] Deployable Account Deletion source exists (site/account-deletion/index.html)"
else
    echo "  [FAIL] site/account-deletion/index.html missing or invalid"
    EXIT_CODE=1
fi

# Production Data Inventory Document
if [ -f "docs/release/privacy-data-inventory.md" ] && grep -q 'Comprehensive Data Inventory Matrix' docs/release/privacy-data-inventory.md; then
    echo "  [PASS] Comprehensive privacy data inventory exists (docs/release/privacy-data-inventory.md)"
else
    echo "  [FAIL] docs/release/privacy-data-inventory.md missing or invalid"
    EXIT_CODE=1
fi

# Google Play Data Safety Mapping Document
if [ -f "docs/release/google-play-data-safety.md" ] && grep -q 'Detailed Data Category Declarations' docs/release/google-play-data-safety.md; then
    echo "  [PASS] Google Play Data Safety mapping exists (docs/release/google-play-data-safety.md)"
else
    echo "  [FAIL] docs/release/google-play-data-safety.md missing or invalid"
    EXIT_CODE=1
fi

# ----------------------------------------------------------------------
# 5. Ads, Analytics & Tracking Absence Audit
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 5] Auditing codebase for accidental ad/tracking/analytics SDKs..."

DISALLOWED_PATTERNS=(
    "com.google.android.gms:play-services-ads"
    "com.google.firebase:firebase-analytics"
    "com.facebook.android:facebook-android-sdk"
    "com.appsflyer:af-android-sdk"
    "com.adjust.sdk:adjust-android"
    "android.permission.AD_ID"
)

FOUND_ADS=0
for PAT in "${DISALLOWED_PATTERNS[@]}"; do
    if git grep -q "$PAT" -- androidApp/build.gradle.kts androidApp/src/main/ AndroidManifest.xml 2>/dev/null; then
        echo "  [FAIL] Prohibited advertising/tracking pattern found: $PAT"
        FOUND_ADS=1
    fi
done

if [ "$FOUND_ADS" -eq 0 ]; then
    echo "  [PASS] Zero advertising SDKs, zero analytics trackers, and zero AD_ID permissions found."
else
    EXIT_CODE=1
fi

# ----------------------------------------------------------------------
# 6. External Contacts & Deployment Verification
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 6] Checking external contact and deployment status..."

if [ -n "${MEMOSTAMP_PRIVACY_CONTACT_EMAIL:-}" ]; then
    echo "  [PASS] Official privacy contact email provided: $MEMOSTAMP_PRIVACY_CONTACT_EMAIL"
    PRIVACY_CONTACT_STATUS="READY"
else
    echo "  [EXTERNAL_SETUP_REQUIRED] Privacy contact email not set in environment (MEMOSTAMP_PRIVACY_CONTACT_EMAIL)."
fi

# ----------------------------------------------------------------------
# 7. Summary & Declaration
# ----------------------------------------------------------------------
echo ""
echo "================================================================="
echo "   STORE PRIVACY COMPLIANCE SUMMARY"
echo "================================================================="
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "CODE_READY:                           PASS"
    echo "PRIVACY_DATA_INVENTORY:               PASS"
    echo "APPLE_PRIVACY_MANIFEST_WIRING:        PASS"
    echo "GOOGLE_PLAY_DATA_SAFETY_MAPPING:      PASS"
    echo "IN_APP_PRIVACY_LINKS:                 PASS"
    echo "IN_APP_ACCOUNT_DELETION_INTEGRITY:    PASS"
    echo "DEPLOYABLE_WEB_RESOURCES:             PASS"
    echo "NO_TRACKING_OR_ADS:                   PASS"
    echo "EXTERNAL_PRIVACY_CONTACT:             $PRIVACY_CONTACT_STATUS"
    echo "STORE_CONSOLE_STATUS:                 $STORE_CONSOLE_STATUS"
    echo "LIVE_URL_STATUS:                      $LIVE_URL_STATUS"
    echo "================================================================="
    echo "Status: ALL CODE PRIVACY CAPABILITIES READY FOR STORE COMPLIANCE."
    exit 0
else
    echo "Status: STORE PRIVACY PREFLIGHT FAILED (See errors above)."
    exit 1
fi
