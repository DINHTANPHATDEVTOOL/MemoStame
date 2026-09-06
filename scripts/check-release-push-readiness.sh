#!/usr/bin/env bash
# ==============================================================================
# MemoStamp Release Push Notification Capability & Readiness Preflight Checker
# Inspects code wiring, entitlements, plugin configuration, and credential hygiene.
# NEVER prints tokens, private keys, service account JSON, or sensitive values.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$WORKSPACE_ROOT"

echo "================================================================="
echo "   MEMOSTAMP RELEASE PUSH CAPABILITY PREFLIGHT INSPECTOR"
echo "================================================================="

EXIT_CODE=0
ANDROID_READY="EXTERNAL_SETUP_REQUIRED"
IOS_READY="EXTERNAL_SETUP_REQUIRED"

# ----------------------------------------------------------------------
# 1. Credential Hygiene: Verify no private credentials are tracked in Git
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 1] Scanning Git repository for accidentally tracked secrets..."
TRACKED_SECRETS=$(git ls-files | grep -E "(google-services\.json|\.p8$|\.mobileprovision$|service-account.*\.json$|\.keystore$|\.jks$)" || true)

if [ -n "$TRACKED_SECRETS" ]; then
    echo "  [FAIL] Prohibited credential files found tracked in Git:"
    echo "$TRACKED_SECRETS" | while read -r line; do echo "    - $line"; done
    echo "  Remediation: Untrack these files immediately and revoke exposed credentials."
    EXIT_CODE=1
else
    echo "  [PASS] Clean repository: No secret files or private keys tracked in Git."
fi

# ----------------------------------------------------------------------
# 2. Android: Google Services Plugin & FCM Capability Wiring
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 2] Verifying Android Google Services & FCM wiring..."

# Check version catalog
if grep -q 'com.google.gms.google-services' gradle/libs.versions.toml; then
    echo "  [PASS] Google Services plugin declared in gradle/libs.versions.toml"
else
    echo "  [FAIL] Google Services plugin missing from gradle/libs.versions.toml"
    EXIT_CODE=1
fi

# Check root build.gradle.kts
if grep -q 'alias(libs.plugins.google.services)' build.gradle.kts; then
    echo "  [PASS] Google Services plugin declared in root build.gradle.kts"
else
    echo "  [FAIL] Google Services plugin missing from root build.gradle.kts"
    EXIT_CODE=1
fi

# Check androidApp/build.gradle.kts wiring
if grep -q 'com.google.gms.google-services' androidApp/build.gradle.kts; then
    echo "  [PASS] Google Services plugin conditionally wired in androidApp/build.gradle.kts"
else
    echo "  [FAIL] Google Services plugin application missing from androidApp/build.gradle.kts"
    EXIT_CODE=1
fi

# Check AndroidManifest.xml package and FCM service
if grep -q 'com.mipastudio.memostamp' androidApp/build.gradle.kts && \
   grep -q 'MemoStampFirebaseMessagingService' androidApp/src/main/AndroidManifest.xml; then
    echo "  [PASS] Android package (com.mipastudio.memostamp) and FCM Service declared in manifest"
else
    echo "  [FAIL] Android package or FCM Service declaration missing from AndroidManifest.xml"
    EXIT_CODE=1
fi

# Check external google-services.json presence
if [ -f "androidApp/google-services.json" ]; then
    if grep -q '"package_name"[[:space:]]*:[[:space:]]*"com.mipastudio.memostamp"' androidApp/google-services.json; then
        echo "  [PASS] External androidApp/google-services.json present with matching package name"
        ANDROID_READY="READY"
    else
        echo "  [WARN] androidApp/google-services.json present but does not match package com.mipastudio.memostamp"
        ANDROID_READY="PACKAGE_MISMATCH"
    fi
else
    echo "  [EXTERNAL_SETUP_REQUIRED] androidApp/google-services.json not found."
    echo "    To wire live FCM: download google-services.json from Firebase Console and place at androidApp/google-services.json"
fi

# ----------------------------------------------------------------------
# 3. iOS: Push Notifications Entitlements & Target Capability Wiring
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 3] Verifying iOS APNs entitlements & target wiring..."

# Check iosApp.entitlements presence
if [ -f "iosApp/iosApp/iosApp.entitlements" ]; then
    if grep -q 'aps-environment' iosApp/iosApp/iosApp.entitlements; then
        echo "  [PASS] iosApp/iosApp/iosApp.entitlements exists and defines aps-environment"
    else
        echo "  [FAIL] iosApp/iosApp/iosApp.entitlements does not define aps-environment"
        EXIT_CODE=1
    fi
else
    echo "  [FAIL] iosApp/iosApp/iosApp.entitlements not found"
    EXIT_CODE=1
fi

# Check Xcode project target entitlements configuration
if grep -q 'CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;' iosApp/iosApp.xcodeproj/project.pbxproj; then
    echo "  [PASS] CODE_SIGN_ENTITLEMENTS configured in iosApp.xcodeproj/project.pbxproj"
else
    echo "  [FAIL] CODE_SIGN_ENTITLEMENTS not wired in iosApp.xcodeproj/project.pbxproj"
    EXIT_CODE=1
fi

# Check Bundle ID
if grep -q 'PRODUCT_BUNDLE_IDENTIFIER = com.mipastudio.memostamp;' iosApp/iosApp.xcodeproj/project.pbxproj; then
    echo "  [PASS] iOS bundle identifier matches com.mipastudio.memostamp"
else
    echo "  [FAIL] iOS bundle identifier mismatch in project.pbxproj"
    EXIT_CODE=1
fi

# Check APNs external server credential status
if [ -n "${APNS_KEY_ID:-}" ] && [ -n "${APNS_TEAM_ID:-}" ] && [ -n "${APNS_PRIVATE_KEY:-}" ]; then
    echo "  [PASS] APNs server credentials provided in environment"
    IOS_READY="READY"
else
    echo "  [EXTERNAL_SETUP_REQUIRED] APNs server secrets (APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY) not set in environment."
    echo "    To wire live APNs dispatch: configure hosted Supabase Edge Functions secrets via supabase secrets set."
fi

# ----------------------------------------------------------------------
# 4. Server Documentation & Secret Names Verification
# ----------------------------------------------------------------------
echo ""
echo "[CHECK 4] Verifying production release documentation..."
DOC_PATH="docs/release/push-notifications.md"
if [ -f "$DOC_PATH" ]; then
    MISSING_DOCS=0
    for SECRET_NAME in "FCM_SERVICE_ACCOUNT_JSON" "APNS_KEY_ID" "APNS_TEAM_ID" "APNS_BUNDLE_ID" "APNS_PRIVATE_KEY"; do
        if ! grep -q "$SECRET_NAME" "$DOC_PATH"; then
            echo "  [FAIL] Secret name $SECRET_NAME not documented in $DOC_PATH"
            MISSING_DOCS=1
        fi
    done
    if [ "$MISSING_DOCS" -eq 0 ]; then
        echo "  [PASS] All server secret names properly documented in $DOC_PATH"
    else
        EXIT_CODE=1
    fi
else
    echo "  [FAIL] $DOC_PATH not found"
    EXIT_CODE=1
fi

# ----------------------------------------------------------------------
# 5. Summary & Declaration
# ----------------------------------------------------------------------
echo ""
echo "================================================================="
echo "   PREFLIGHT READINESS SUMMARY"
echo "================================================================="
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "CODE_READY:                           PASS"
    echo "ANDROID_FCM_CAPABILITY_WIRING:        PASS"
    echo "IOS_APNS_ENTITLEMENT_WIRING:          PASS"
    echo "EXTERNAL_ANDROID_CREDENTIALS:         $ANDROID_READY"
    echo "EXTERNAL_IOS_CREDENTIALS:             $IOS_READY"
    echo "LIVE_DEVICE_VERIFIED:                 FALSE (Pending signed physical device testing)"
    echo "================================================================="
    echo "Status: ALL CODE CAPABILITIES READY FOR RELEASE."
    exit 0
else
    echo "Status: PREFLIGHT FAILED (See errors above)."
    exit 1
fi
