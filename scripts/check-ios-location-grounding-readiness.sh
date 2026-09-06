#!/usr/bin/env bash
# ==============================================================================
# check-ios-location-grounding-readiness.sh
#
# Preflight audit for Task #66: iOS Location Grounding Parity
# Verifies that iOS location and maps grounding implementation adheres strictly to:
# - Authenticated Supabase Edge Function gateway (/functions/v1/maps-grounding)
# - Zero mobile Gemini API keys, provider URLs, or service_role credentials
# - Bounded search payloads and omission of client-controllable model/prompts
# - Account-switch isolation and search generation race condition guards
# - Defensive decoding of places and story responses
# - Non-destructive title and caption autofill in MemoryNoteScreenView
# - Location picker integration in StampEditorScreenView
# - Complete Xcode project wiring in project.pbxproj
# ==============================================================================

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$WORKSPACE_ROOT"

echo "======================================================================"
echo "  AUDIT: iOS Location Grounding Parity Readiness"
echo "======================================================================"

ERRORS=0

function fail() {
    echo "  [FAIL] $1"
    ERRORS=$((ERRORS + 1))
}

function pass() {
    echo "  [PASS] $1"
}

# ------------------------------------------------------------------------------
# 1. Verify Core Swift Files Exist
# ------------------------------------------------------------------------------
echo "Checking core Swift source files..."

CLIENT_FILE="iosApp/iosApp/Data/IOSMapsGroundingClient.swift"
PICKER_FILE="iosApp/iosApp/Features/Location/LocationPickerSheetView.swift"
NOTE_FILE="iosApp/iosApp/Features/Note/MemoryNoteScreenView.swift"
EDITOR_FILE="iosApp/iosApp/Features/Editor/StampEditorScreenView.swift"
PBX_FILE="iosApp/iosApp.xcodeproj/project.pbxproj"

if [ -f "$CLIENT_FILE" ]; then
    pass "IOSMapsGroundingClient.swift exists"
else
    fail "IOSMapsGroundingClient.swift missing at $CLIENT_FILE"
fi

if [ -f "$PICKER_FILE" ]; then
    pass "LocationPickerSheetView.swift exists"
else
    fail "LocationPickerSheetView.swift missing at $PICKER_FILE"
fi

# ------------------------------------------------------------------------------
# 2. Verify Zero Forbidden Gemini / Provider Credentials in iOS
# ------------------------------------------------------------------------------
echo "Checking for forbidden Gemini provider credentials in iOS source..."

FORBIDDEN_TOKENS=(
    "GEMINI_API_KEY"
    "generativelanguage.googleapis.com"
    "GEMINI_PROVIDER_MODE"
    "MOCK_GEMINI_URL"
    "service_role"
)

for token in "${FORBIDDEN_TOKENS[@]}"; do
    MATCHES=$(grep -rn "$token" iosApp/iosApp/ 2>/dev/null || true)
    if [ -n "$MATCHES" ]; then
        fail "Found forbidden credential/host '$token' in iosApp/:\n$MATCHES"
    else
        pass "No '$token' found in iOS source"
    fi
done

# ------------------------------------------------------------------------------
# 3. Verify Supabase maps-grounding Endpoint & Auth Wiring
# ------------------------------------------------------------------------------
echo "Checking maps-grounding endpoint and auth wiring..."

if grep -q "/functions/v1/maps-grounding" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient references authoritative '/functions/v1/maps-grounding' endpoint"
else
    fail "IOSMapsGroundingClient must reference '/functions/v1/maps-grounding'"
fi

if grep -q "SupabaseAuthService.shared.loadOrRefreshSession" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient uses SupabaseAuthService.shared.loadOrRefreshSession for authoritative auth"
else
    fail "IOSMapsGroundingClient must load or refresh session authoritative token"
fi

if grep -Fq 'Bearer \(session.accessToken)' "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient attaches current Bearer access token"
else
    fail "IOSMapsGroundingClient must pass Authorization: Bearer <token>"
fi

# ------------------------------------------------------------------------------
# 4. Verify Forbidden Payload Fields Are Absent From Client Requests
# ------------------------------------------------------------------------------
echo "Checking forbidden client payload controls in client..."

FORBIDDEN_BODY_KEYS=(
    "\"acting_uid\""
    "\"actingUid\""
    "\"userId\""
    "\"user_id\""
    "\"prompt\""
    "\"system_prompt\""
    "\"systemPrompt\""
    "\"model\""
    "\"provider_url\""
    "\"providerUrl\""
    "\"tools\""
)

for key in "${FORBIDDEN_BODY_KEYS[@]}"; do
    if grep -q "$key" "$CLIENT_FILE"; then
        fail "IOSMapsGroundingClient must NOT include client payload key $key"
    else
        pass "Client request body does not include $key"
    fi
done

# ------------------------------------------------------------------------------
# 5. Verify Account-Switch & Generation Guards
# ------------------------------------------------------------------------------
echo "Checking account-switch and search generation race guards..."

if grep -q "capturedAuthUid" "$CLIENT_FILE" && grep -q "currentUid == capturedAuthUid" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient implements account-switch isolation guard"
else
    fail "IOSMapsGroundingClient missing account-switch guard (capturedAuthUid check)"
fi

if grep -q "activeSearchGeneration" "$CLIENT_FILE" && grep -q "isGenerationValid" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient implements search generation monotonic counter race guard"
else
    fail "IOSMapsGroundingClient missing activeSearchGeneration race guard"
fi

# ------------------------------------------------------------------------------
# 6. Verify Rate Limit (HTTP 429) Handling
# ------------------------------------------------------------------------------
echo "Checking HTTP 429 rate limit handling..."

if grep -q "429" "$CLIENT_FILE" && grep -q "retry_after_seconds" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient parses HTTP 429 retry_after_seconds"
else
    fail "IOSMapsGroundingClient must handle HTTP 429 and retry_after_seconds"
fi

# ------------------------------------------------------------------------------
# 7. Verify Defensive Parsing & Bounded Input
# ------------------------------------------------------------------------------
echo "Checking defensive decoding and input bounds..."

if grep -q "prefix(100)" "$CLIENT_FILE" && grep -q "prefix(150)" "$CLIENT_FILE" && grep -q "prefix(250)" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient enforces input length bounds (prefix 100/150/250)"
else
    fail "IOSMapsGroundingClient must enforce client-side string bounds"
fi

if grep -q "isNaN" "$CLIENT_FILE" && grep -q "isInfinite" "$CLIENT_FILE"; then
    pass "IOSMapsGroundingClient guards against NaN and infinite distance values"
else
    fail "IOSMapsGroundingClient must validate distance values against NaN and infinite"
fi

# ------------------------------------------------------------------------------
# 8. Verify Non-Destructive Autofill in MemoryNoteScreenView
# ------------------------------------------------------------------------------
echo "Checking non-destructive autofill in MemoryNoteScreenView..."

if grep -q "title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty" "$NOTE_FILE"; then
    pass "MemoryNoteScreenView preserves user-written title (does not overwrite existing text)"
else
    fail "MemoryNoteScreenView must check title is empty before autofilling suggested title"
fi

if grep -q "caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty" "$NOTE_FILE"; then
    pass "MemoryNoteScreenView preserves user-written caption (does not overwrite existing text)"
else
    fail "MemoryNoteScreenView must check caption is empty before autofilling poetic note"
fi

if grep -q "LocationPickerSheetView" "$NOTE_FILE"; then
    pass "MemoryNoteScreenView binds LocationPickerSheetView"
else
    fail "MemoryNoteScreenView missing LocationPickerSheetView binding"
fi

# ------------------------------------------------------------------------------
# 9. Verify StampEditorScreenView Location Integration
# ------------------------------------------------------------------------------
echo "Checking StampEditorScreenView location integration..."

if grep -q "LocationPickerSheetView" "$EDITOR_FILE"; then
    pass "StampEditorScreenView binds LocationPickerSheetView"
else
    fail "StampEditorScreenView missing LocationPickerSheetView binding"
fi

if grep -q "showLocationPickerSheet" "$EDITOR_FILE"; then
    pass "StampEditorScreenView defines showLocationPickerSheet state"
else
    fail "StampEditorScreenView missing showLocationPickerSheet state"
fi

# ------------------------------------------------------------------------------
# 10. Verify Xcode Project (project.pbxproj) Wiring
# ------------------------------------------------------------------------------
echo "Checking Xcode project.pbxproj wiring..."

PBX_ENTRIES=(
    "IOSMapsGroundingClient.swift in Sources"
    "IOSMapsGroundingClient.swift"
    "LocationPickerSheetView.swift in Sources"
    "LocationPickerSheetView.swift"
)

for entry in "${PBX_ENTRIES[@]}"; do
    if grep -q "$entry" "$PBX_FILE"; then
        pass "project.pbxproj contains '$entry'"
    else
        fail "project.pbxproj missing '$entry'"
    fi
done

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo "======================================================================"
if [ $ERRORS -eq 0 ]; then
    echo "  [SUCCESS] All iOS Location Grounding Parity readiness checks passed!"
    echo "======================================================================"
    exit 0
else
    echo "  [FAILURE] Found $ERRORS readiness violations."
    echo "======================================================================"
    exit 1
fi
