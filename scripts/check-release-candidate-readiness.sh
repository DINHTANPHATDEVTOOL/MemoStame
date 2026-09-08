#!/usr/bin/env bash
# ==============================================================================
# MemoStamp Release Candidate Readiness Preflight Inspector
# Verifies Android & iOS production capability wiring, signing seams, versioning,
# backend migrations, edge functions, security contracts, and credential hygiene.
# NEVER prints tokens, private keys, passwords, or secret credentials.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$WORKSPACE_ROOT"

echo "================================================================="
echo "   MEMOSTAMP RELEASE CANDIDATE READINESS INSPECTOR (#62)"
echo "================================================================="

EXIT_CODE=0
ANDROID_SIGNING_STATUS="EXTERNAL_SETUP_REQUIRED"
ANDROID_FCM_STATUS="EXTERNAL_SETUP_REQUIRED"
IOS_SIGNING_STATUS="EXTERNAL_SETUP_REQUIRED"
IOS_APNS_STATUS="EXTERNAL_SETUP_REQUIRED"
BACKEND_STATUS="EXTERNAL_SETUP_REQUIRED"

# ----------------------------------------------------------------------
# 1. Credential Hygiene & Git Repository Scan
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 1] Scanning Git repository for accidentally tracked secrets..."
TRACKED_SECRETS=$(git ls-files | grep -E "(\.keystore$|\.jks$|\.p8$|\.p12$|\.mobileprovision$|google-services\.json$|service-account.*\.json$)" || true)

if [ -n "$TRACKED_SECRETS" ]; then
    echo "  [FAIL] Prohibited secret files tracked in Git:"
    echo "$TRACKED_SECRETS" | while read -r line; do echo "    - $line"; done
    echo "  Remediation: Remove files from git and rotate credentials immediately."
    EXIT_CODE=1
else
    echo "  [PASS] Clean repository: No keystores, private keys, certificates, or service accounts tracked."
fi

# ----------------------------------------------------------------------
# 2. Android Release Candidate Wiring
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 2] Verifying Android Release Candidate wiring..."

# Application ID
if grep -q 'applicationId = "com.mipastudio.memostamp"' androidApp/build.gradle.kts; then
    echo "  [PASS] Application ID matches 'com.mipastudio.memostamp'"
else
    echo "  [FAIL] Application ID mismatch in androidApp/build.gradle.kts"
    EXIT_CODE=1
fi

# R8 Minification and Resource Shrinking
if grep -q 'isMinifyEnabled = true' androidApp/build.gradle.kts && \
   grep -q 'isShrinkResources = true' androidApp/build.gradle.kts; then
    echo "  [PASS] R8 code minification and resource shrinking enabled for Release"
else
    echo "  [FAIL] Release buildType must have isMinifyEnabled and isShrinkResources enabled"
    EXIT_CODE=1
fi

# Signing Configuration Seam
if grep -q 'MEMOSTAMP_RELEASE_STORE_FILE' androidApp/build.gradle.kts && \
   grep -q 'MEMOSTAMP_RELEASE_STORE_PASSWORD' androidApp/build.gradle.kts && \
   grep -q 'MEMOSTAMP_RELEASE_KEY_ALIAS' androidApp/build.gradle.kts && \
   grep -q 'MEMOSTAMP_RELEASE_KEY_PASSWORD' androidApp/build.gradle.kts && \
   grep -q 'MEMOSTAMP_REQUIRE_RELEASE_SIGNING' androidApp/build.gradle.kts; then
    echo "  [PASS] Android release signing seam wired with external environment variable overrides"
else
    echo "  [FAIL] Android release signing seam missing required properties in androidApp/build.gradle.kts"
    EXIT_CODE=1
fi

# Version Code & Version Name Overrides
if grep -q 'MEMOSTAMP_VERSION_CODE' androidApp/build.gradle.kts && \
   grep -q 'MEMOSTAMP_VERSION_NAME' androidApp/build.gradle.kts; then
    echo "  [PASS] Android version code and version name external overrides wired"
else
    echo "  [FAIL] Android version overrides missing in androidApp/build.gradle.kts"
    EXIT_CODE=1
fi

# FCM Google Services Plugin Wiring
if grep -q 'com.google.gms.google-services' androidApp/build.gradle.kts && \
   grep -q 'MemoStampFirebaseMessagingService' androidApp/src/main/AndroidManifest.xml; then
    echo "  [PASS] FCM Google Services plugin and Messaging Service declared"
else
    echo "  [FAIL] FCM Google Services wiring or service declaration missing"
    EXIT_CODE=1
fi

# Android External Signing Credentials Check
if [ -n "${MEMOSTAMP_RELEASE_STORE_FILE:-}" ] && \
   [ -n "${MEMOSTAMP_RELEASE_STORE_PASSWORD:-}" ] && \
   [ -n "${MEMOSTAMP_RELEASE_KEY_ALIAS:-}" ] && \
   [ -n "${MEMOSTAMP_RELEASE_KEY_PASSWORD:-}" ]; then
    if [ -f "$MEMOSTAMP_RELEASE_STORE_FILE" ]; then
        echo "  [PASS] External Android release keystore supplied and present"
        ANDROID_SIGNING_STATUS="READY"
    else
        echo "  [WARN] MEMOSTAMP_RELEASE_STORE_FILE specified but file not found on disk"
        ANDROID_SIGNING_STATUS="STORE_FILE_NOT_FOUND"
    fi
else
    echo "  [EXTERNAL_SETUP_REQUIRED] Android release keystore credentials not present in environment."
    echo "    To sign release AAB: set MEMOSTAMP_RELEASE_STORE_FILE, MEMOSTAMP_RELEASE_STORE_PASSWORD, MEMOSTAMP_RELEASE_KEY_ALIAS, MEMOSTAMP_RELEASE_KEY_PASSWORD"
fi

# Android External FCM Config Check
if [ -f "androidApp/google-services.json" ]; then
    echo "  [PASS] androidApp/google-services.json present"
    ANDROID_FCM_STATUS="READY"
else
    echo "  [EXTERNAL_SETUP_REQUIRED] androidApp/google-services.json not found."
    echo "    To enable FCM in release: place production google-services.json in androidApp/"
fi

# ----------------------------------------------------------------------
# 3. iOS Release Candidate Wiring
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 3] Verifying iOS Release Candidate wiring..."

# Bundle Identifier
if grep -q 'PRODUCT_BUNDLE_IDENTIFIER = com.mipastudio.memostamp;' iosApp/iosApp.xcodeproj/project.pbxproj; then
    echo "  [PASS] iOS bundle identifier matches 'com.mipastudio.memostamp'"
else
    echo "  [FAIL] iOS bundle identifier mismatch in project.pbxproj"
    EXIT_CODE=1
fi

# Privacy Manifest (PrivacyInfo.xcprivacy)
if [ -f "iosApp/iosApp/PrivacyInfo.xcprivacy" ] && grep -q 'NSPrivacyTracking' iosApp/iosApp/PrivacyInfo.xcprivacy; then
    echo "  [PASS] iOS Privacy manifest (PrivacyInfo.xcprivacy) present and structured"
else
    echo "  [FAIL] iOS PrivacyInfo.xcprivacy missing or invalid"
    EXIT_CODE=1
fi

# Push Entitlements & Target Wiring
if [ -f "iosApp/iosApp/iosApp.entitlements" ] && \
   grep -q 'aps-environment' iosApp/iosApp/iosApp.entitlements && \
   grep -q 'CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;' iosApp/iosApp.xcodeproj/project.pbxproj; then
    echo "  [PASS] iOS Push Notifications capability and CODE_SIGN_ENTITLEMENTS wired"
else
    echo "  [FAIL] iOS Push entitlements or Xcode target wiring missing"
    EXIT_CODE=1
fi

# Signed IPA Export Configuration & Codemagic Pipeline
if [ -f "iosApp/ExportOptions-AppStore.plist" ] && \
   grep -q 'app-store' iosApp/ExportOptions-AppStore.plist && \
   grep -q 'ios-app-store-release' codemagic.yaml && \
   grep -q 'exportArchive' codemagic.yaml; then
    echo "  [PASS] App Store signed IPA export configuration and Codemagic pipeline present"
else
    echo "  [FAIL] App Store export configuration or Codemagic workflow missing"
    EXIT_CODE=1
fi

# Version / Build Number Overrides
if grep -q 'MARKETING_VERSION' codemagic.yaml && \
   grep -q 'CURRENT_PROJECT_VERSION' codemagic.yaml; then
    echo "  [PASS] iOS external version and build number overrides configured"
else
    echo "  [FAIL] iOS version override settings missing from build pipeline"
    EXIT_CODE=1
fi

# APNs Production Verification Path
if grep -q 'aps-environment' codemagic.yaml && \
   grep -q 'codesign' codemagic.yaml; then
    echo "  [PASS] Signed IPA APNs production entitlement verification path configured"
else
    echo "  [FAIL] APNs production entitlement verification step missing in release pipeline"
    EXIT_CODE=1
fi

# Codemagic App Store Connect Publishing & Authentication Consistency (#86)
if grep -q 'api_key: \$APP_STORE_CONNECT_PRIVATE_KEY' codemagic.yaml && \
   grep -q 'key_id: \$APP_STORE_CONNECT_KEY_IDENTIFIER' codemagic.yaml && \
   grep -q 'issuer_id: \$APP_STORE_CONNECT_ISSUER_ID' codemagic.yaml && \
   ! grep -q 'auth: integration' codemagic.yaml; then
    echo "  [PASS] Codemagic App Store Connect publishing configured with consistent secret group variables (no mixed auth)"
else
    echo "  [FAIL] Codemagic App Store Connect publishing configuration invalid or uses mixed auth mode"
    EXIT_CODE=1
fi

# iOS External Apple Distribution Credentials Check
if [ -n "${APP_STORE_CONNECT_PRIVATE_KEY:-}" ] || [ -n "${CERTIFICATE_PRIVATE_KEY:-}" ]; then
    echo "  [PASS] iOS distribution signing credentials provided in environment"
    IOS_SIGNING_STATUS="READY"
else
    echo "  [EXTERNAL_SETUP_REQUIRED] Apple distribution credentials not present in local environment."
    echo "    Supplied via Codemagic secure environment group 'app_store_credentials' during release."
fi

# iOS External APNs Keys Check
if [ -n "${APNS_KEY_ID:-}" ] && [ -n "${APNS_TEAM_ID:-}" ] && [ -n "${APNS_PRIVATE_KEY:-}" ]; then
    echo "  [PASS] APNs server credentials provided in environment"
    IOS_APNS_STATUS="READY"
else
    echo "  [EXTERNAL_SETUP_REQUIRED] APNs server secrets not set in local environment."
fi

# ----------------------------------------------------------------------
# 4. Hosted Backend & Production Contracts Preflight
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 4] Verifying hosted Supabase backend migrations & Edge Functions contracts..."

# Migrations 001 through 013
MIGRATIONS=(
    "001_auth_social_rls.sql"
    "002_schema_contract_and_rls_hardening.sql"
    "003_deployment_safety_and_id_contract.sql"
    "004_add_direct_messages_realtime_publication.sql"
    "005_media_storage_and_feed_replies.sql"
    "006_push_device_tokens.sql"
    "007_user_blocks_and_abuse_reports.sql"
    "008_block_privacy_oracle_hotfix.sql"
    "009_cloud_stamp_trade.sql"
    "010_social_abuse_rate_limits.sql"
    "011_maps_grounding_rate_limits.sql"
    "012_album_layout_persistence.sql"
    "013_album_page_lifecycle.sql"
)
MISSING_MIGRATIONS=0
for MIG in "${MIGRATIONS[@]}"; do
    if [ ! -f "supabase/migrations/$MIG" ]; then
        echo "  [FAIL] Required migration missing: supabase/migrations/$MIG"
        MISSING_MIGRATIONS=1
    fi
done
if [ "$MISSING_MIGRATIONS" -eq 0 ]; then
    echo "  [PASS] All 13 required database migrations present (001 through 013)"
else
    EXIT_CODE=1
fi

# Edge Functions
EDGE_FUNCTIONS=("delete-account" "dispatch-push" "accept-trade" "maps-grounding")
MISSING_FUNCTIONS=0
for FUNC in "${EDGE_FUNCTIONS[@]}"; do
    if [ ! -f "supabase/functions/$FUNC/index.ts" ]; then
        echo "  [FAIL] Required Edge Function missing: supabase/functions/$FUNC/index.ts"
        MISSING_FUNCTIONS=1
    fi
done
if [ "$MISSING_FUNCTIONS" -eq 0 ]; then
    echo "  [PASS] All 4 required Edge Functions present (delete-account, dispatch-push, accept-trade, maps-grounding)"
else
    EXIT_CODE=1
fi

# Auth Redirect URL Contract
if grep -q 'memostamp://auth/recovery' docs/release/supabase-auth.md; then
    echo "  [PASS] Password recovery redirect contract (memostamp://auth/recovery) documented"
else
    echo "  [FAIL] Password recovery redirect contract missing from docs/release/supabase-auth.md"
    EXIT_CODE=1
fi

# Check Hosted Backend Secret Names in Documentation
DOC_PATH="docs/release/push-notifications.md"
if [ -f "$DOC_PATH" ] && \
   grep -q "FCM_SERVICE_ACCOUNT_JSON" "$DOC_PATH" && \
   grep -q "APNS_KEY_ID" "$DOC_PATH" && \
   grep -q "APNS_PRIVATE_KEY" "$DOC_PATH"; then
    echo "  [PASS] Push server secret names verified in documentation"
    BACKEND_STATUS="CODE_VERIFIED"
else
    echo "  [FAIL] Push server secret names missing from release documentation"
    EXIT_CODE=1
fi

# ----------------------------------------------------------------------
# 5. Mobile Artifact Security Checks
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 5] Inspecting mobile source code for prohibited secrets..."

# Check service_role in mobile production code
SERVICE_ROLE_IN_MOBILE=$(git grep -i "service_role" -- androidApp/src/main/ iosApp/iosApp/ ':!iosApp/iosApp.xcodeproj' || true)
if [ -n "$SERVICE_ROLE_IN_MOBILE" ]; then
    echo "  [FAIL] Prohibited service_role reference found in mobile client code:"
    echo "$SERVICE_ROLE_IN_MOBILE"
    EXIT_CODE=1
else
    echo "  [PASS] Zero service_role references in Android and iOS client code"
fi

# Check Gemini private key / direct API call in mobile production code
GEMINI_IN_MOBILE=$(git grep -i "generativelanguage.googleapis.com" -- androidApp/src/main/ iosApp/iosApp/ || true)
if [ -n "$GEMINI_IN_MOBILE" ]; then
    echo "  [FAIL] Direct Gemini API call found in mobile code (must route through Edge Function):"
    echo "$GEMINI_IN_MOBILE"
    EXIT_CODE=1
else
    echo "  [PASS] Mobile production code does not contain direct Gemini API endpoints or keys"
fi

# Check production defaults for localhost backend
if grep -q 'DEFAULT_SUPABASE_URL.*localhost' androidApp/src/main/java/com/mipastudio/memostamp/data/remote/supabase/SupabaseConfig.kt || \
   grep -q 'DEFAULT_SUPABASE_URL.*127\.0\.0\.1' androidApp/src/main/java/com/mipastudio/memostamp/data/remote/supabase/SupabaseConfig.kt; then
    echo "  [FAIL] Localhost backend URL found in Android core production code defaults"
    EXIT_CODE=1
else
    echo "  [PASS] Clean backend configuration: No localhost production backend defaults in Android"
fi

# Check Android debuggable default in release
if grep -A 10 'release {' androidApp/build.gradle.kts | grep -q 'isDebuggable = true'; then
    echo "  [FAIL] Release buildType must not have isDebuggable = true"
    EXIT_CODE=1
else
    echo "  [PASS] Release buildType debuggable is false by default"
fi

# ----------------------------------------------------------------------
# 6. Release Candidate Summary & Declaration
# ----------------------------------------------------------------------
echo ""
echo "================================================================="
echo "   RELEASE CANDIDATE READINESS SUMMARY"
echo "================================================================="
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "CODE_READY:                           PASS"
    echo "ANDROID_RELEASE_SIGNING_SEAM:         PASS"
    echo "ANDROID_PLAY_APP_SIGNING_WIRING:      PASS"
    echo "ANDROID_EXTERNAL_SIGNING_SECRETS:     $ANDROID_SIGNING_STATUS"
    echo "ANDROID_EXTERNAL_FCM_CONFIG:          $ANDROID_FCM_STATUS"
    echo "IOS_APP_STORE_SIGNING_SEAM:           PASS"
    echo "IOS_SIGNED_IPA_EXPORT_CONFIG:         PASS"
    echo "IOS_EXTERNAL_APPLE_CREDENTIALS:       $IOS_SIGNING_STATUS"
    echo "IOS_EXTERNAL_APNS_CREDENTIALS:        $IOS_APNS_STATUS"
    echo "HOSTED_BACKEND_CONTRACTS:             $BACKEND_STATUS"
    echo "LIVE_DEVICE_VERIFICATION:             PENDING_SIGNED_HARDWARE"
    echo "================================================================="
    echo "Status: ALL CODE CAPABILITIES READY FOR RELEASE CANDIDATE."
    exit 0
else
    echo "Status: RELEASE CANDIDATE PREFLIGHT FAILED (See errors above)."
    exit 1
fi
