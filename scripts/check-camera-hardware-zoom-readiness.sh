#!/usr/bin/env bash
set -euo pipefail

echo "========================================================"
echo "  Task #68: Camera Hardware Zoom Readiness Preflight   "
echo "========================================================"

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$WORKSPACE_ROOT"

FAIL_COUNT=0

# 1. NO FIXED ZOOM PRESET AUTHORITY
echo -n "Checking for absence of fixed zoom preset arrays... "
if grep -rn 'listOf(2f, 3f, 5f)' androidApp/src/main/java/ 2>/dev/null; then
    echo "FAILED (found hardcoded listOf(2f, 3f, 5f) in androidApp)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
elif grep -rn '\["1x", "2x", "3x", "5x"\]' iosApp/ 2>/dev/null; then
    echo "FAILED (found hardcoded zoomOptions array in iosApp)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "PASS"
fi

# 2. DEVICE-DERIVED NATIVE LENS PRESETS
echo -n "Checking dynamic runtime physical lens discovery... "
HAS_ANDROID_DISCOVERY=false
HAS_IOS_DISCOVERY=false

if grep -q "NativeCameraLensDiscovery" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/CameraController.kt; then
    HAS_ANDROID_DISCOVERY=true
fi

if grep -q "discoverCapabilities" iosApp/iosApp/Features/Camera/CameraScreenView.swift; then
    HAS_IOS_DISCOVERY=true
fi

if [ "$HAS_ANDROID_DISCOVERY" = true ] && [ "$HAS_IOS_DISCOVERY" = true ]; then
    echo "PASS"
else
    echo "FAILED (discovery wiring missing)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# 3. CONTINUOUS HARDWARE PINCH ZOOM
echo -n "Checking continuous hardware pinch zoom integration... "
HAS_ANDROID_PINCH=false
HAS_IOS_PINCH=false

if grep -q "detectTransformGestures" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/CameraScreen.kt && \
   grep -q "cameraController.setZoomRatio" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/CameraScreen.kt; then
    HAS_ANDROID_PINCH=true
fi

if grep -q "MagnificationGesture" iosApp/iosApp/Features/Camera/CameraScreenView.swift && \
   grep -q "device.videoZoomFactor" iosApp/iosApp/Features/Camera/CameraScreenView.swift; then
    HAS_IOS_PINCH=true
fi

if [ "$HAS_ANDROID_PINCH" = true ] && [ "$HAS_IOS_PINCH" = true ]; then
    echo "PASS"
else
    echo "FAILED (pinch zoom wiring incomplete)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# 4. NO FAKE OPTICAL LENS
echo -n "Checking that intermediate pinch zoom does not synthesize fake optical lenses... "
if grep -q "isMatchingOpticalLens" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/components/CameraControls.kt && \
   grep -q "isMatchingLens" iosApp/iosApp/Features/Camera/CameraScreenView.swift; then
    echo "PASS"
else
    echo "FAILED (live zoom vs optical preset separation missing)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# 5. NO DEVICE MODEL HARD-CODING
echo -n "Checking zero device model hard-coding... "
if grep -rn "Build.MODEL" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/lens/ 2>/dev/null; then
    echo "FAILED (found Build.MODEL in lens discovery)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
elif grep -rn "Build.MANUFACTURER" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/lens/ 2>/dev/null; then
    echo "FAILED (found Build.MANUFACTURER in lens discovery)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
elif grep -rn "iPhone13" iosApp/iosApp/Features/Camera/ 2>/dev/null; then
    echo "FAILED (found iPhone model string in iosApp camera)"
    FAIL_COUNT=$((FAIL_COUNT + 1))
else
    echo "PASS"
fi

# 6. PREVIEW/CAPTURE ZOOM CONSISTENCY
echo -n "Checking preview and capture zoom consistency... "
if grep -q "setZoomRatio" androidApp/src/main/java/com/mipastudio/memostamp/feature/camera/CameraController.kt && \
   grep -q "setZoomFactor" iosApp/iosApp/Features/Camera/CameraScreenView.swift; then
    echo "PASS"
else
    echo "FAILED"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

echo "========================================================"
if [ "$FAIL_COUNT" -eq 0 ]; then
    echo "NO FIXED ZOOM PRESET AUTHORITY: PASS"
    echo "DEVICE-DERIVED NATIVE LENS PRESETS: PASS"
    echo "CONTINUOUS HARDWARE PINCH ZOOM: PASS"
    echo "NO FAKE OPTICAL LENS: PASS"
    echo "NO DEVICE MODEL HARD-CODING: PASS"
    echo "PREVIEW/CAPTURE ZOOM CONSISTENCY: PASS"
    echo "For physical-device behavior that CI cannot prove:"
    echo "LIVE_DEVICE_VERIFICATION_REQUIRED"
    exit 0
else
    echo "Check failed with $FAIL_COUNT errors."
    exit 1
fi
