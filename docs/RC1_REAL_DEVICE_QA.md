# MemoStamp Release Candidate 1 (RC1) — Real-Device QA Matrix (#84)

This document establishes the comprehensive Real-Device Quality Assurance (QA) protocol for MemoStamp (`com.mipastudio.memostamp`) RC1.

> **Honesty & Integrity Rule**: In accordance with the RC1 Definition of Done, no physical-device test case may be marked `PASS` based solely on code inspection, unit tests, or unsigned simulator builds. Tests requiring physical hardware, retail cellular networks, signed distribution profiles, or app store consoles are strictly classified as `LIVE_DEVICE_VERIFICATION_REQUIRED`, `EXTERNAL_SETUP_REQUIRED`, or `STORE_CONSOLE_VERIFICATION_REQUIRED`.

---

## 1. Physical Device Test Matrix Specification (Section 17)

All physical device evaluations must be executed against production-configured, signed Release Candidate artifacts (Android signed AAB/release APK and iOS TestFlight/Ad-Hoc signed IPA).

### 1.1 Android Hardware Matrix
| Role | Target Class | Specific Test Model | OS / API Level | Target Architecture |
|---|---|---|---|---|
| **Tier 1 (Flagship Modern)** | Modern Pixel / Samsung | Google Pixel 8 Pro / Samsung Galaxy S24 | Android 14+ (API 34/35) | arm64-v8a |
| **Tier 2 (Mid-Tier / Standard)** | Mainstream Volume Device | Samsung Galaxy A54 5G / Xiaomi Redmi Note 13 | Android 13/14 (API 33/34) | arm64-v8a |
| **Tier 3 (Baseline / Min API)** | Low-End / Minimum Target | Motorola Moto G Power / Pixel 4a | Android 10/11 (API 29/30) | arm64-v8a / armeabi-v7a |

### 1.2 iOS Hardware Matrix
| Role | Target Class | Specific Test Model | iOS Version | Target Architecture |
|---|---|---|---|---|
| **Tier 1 (Pro Multi-Lens)** | Modern Multi-Lens Hardware | iPhone 15 Pro / iPhone 13 Pro Max | iOS 17.x / 18.x | arm64 (Triple Lens) |
| **Tier 2 (Standard Dual-Lens)** | Mainstream iPhone | iPhone 13 / iPhone 14 | iOS 16.x / 17.x | arm64 (Dual Lens) |
| **Tier 3 (Compact / Single Lens)** | Baseline Supported iPhone | iPhone SE (3rd Gen) / iPhone 11 | iOS 16.x | arm64 (Single Lens) |

---

## 2. Real-Device Execution Protocol & Test Cases (Sections 8 – 16, 18)

### Record Format
Each execution record is audited with:
- **Case ID**: Unique test identifier
- **Platform**: Android / iOS
- **Device Model**: Specific physical hardware
- **OS Version**: Physical device firmware version
- **App Version / Build**: e.g., `1.0.0 (100)`
- **Status**: `CODE_VERIFIED` | `LIVE_DEVICE_VERIFICATION_REQUIRED` | `EXTERNAL_SETUP_REQUIRED` | `PASS` | `FAIL` | `BLOCKED`
- **Notes**: Observable behavior, timing, ergonomics
- **Evidence / Log**: Console log snippet, video, or screenshot link

---

### 2.1 Auth Test Matrix (Section 8)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `AUTH-01` | Android + iOS | Multi-matrix | Any | RC1 | `CODE_VERIFIED` | **Clean Sign-Up**: Register new email/password account. Verify verification email dispatched and session created. | Unit/RLS: `001_auth_social_rls.sql` |
| `AUTH-02` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Sign-In & Session Persistence**: Sign in, force quit app, cold launch, verify instant session restore without credential prompt. | Cold launch telemetry |
| `AUTH-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Logout / Login Lifecycle**: Log out cleanly; verify Keychain/EncryptedSharedPreferences cleared; log in with second account. | Secure storage inspection |
| `AUTH-04` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Account Switch Isolation**: Switch User A -> User B; verify no cached stamps, feed drafts, chats, or album placements bleed across accounts. | Cache clearance audit |
| `AUTH-05` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Forgot Password & Recovery Link**: Trigger password reset; receive email; tap `memostamp://auth/recovery`; verify deep link directly opens Password Update sheet. | Deep link handler log |
| `AUTH-06` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Self-Service Account Deletion**: Trigger account deletion from Passport screen; verify confirmation modal, immediate logout, and purge of user records & storage in Supabase. | Edge Function: `delete-account` |

---

### 2.2 Feed & Social Reliability Matrix (Section 9 & #82)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `FEED-01` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Create & View Post**: Publish post with stamp and caption; verify real-time appearance in global feed. | Feed feed query response |
| `FEED-02` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Like / Unlike Toggle**: Rapid toggle like on post; verify optimistic UI update and deterministic database reconciliation. | RLS post likes assertion |
| `FEED-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `CODE_VERIFIED` | **#82 Comment Success**: Type comment -> Send -> Submitting lock -> Server confirms -> Clear draft & render comment. | Automated: `check-postdetail-comment-readiness.sh` |
| `FEED-04` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **#82 Offline / Network Failure**: Put device in Airplane Mode -> Send comment -> Verify draft preserved in input field, banner displays localized error, Retry button appears. | Network disconnect capture |
| `FEED-05` | Android + iOS | Physical hardware | Target matrix | RC1 | `CODE_VERIFIED` | **#82 Rapid Tap Deduplication**: Spam Send button 5x rapidly; verify submit lock prevents duplicate network requests or duplicate rows. | `PostDetailCommentReliabilityTest` |
| `FEED-06` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Block & Abuse Report**: Report post/user; block user; verify blocked user's posts, comments, and profile disappear from feed immediately. | RLS: `007_user_blocks_and_abuse_reports.sql` |
| `FEED-07` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Friend Request & Acceptance**: Send friend request User A -> User B; accept request; verify mutual friend status and mutual stamp visibility. | RLS: `001_auth_social_rls.sql` |

---

### 2.3 Chat & Realtime Reliability Matrix (Section 10)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `CHAT-01` | Android + iOS | Dual physical devices | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Direct Message Exchange**: User A sends message to User B; verify message received via Supabase Realtime WebSocket < 500ms. | WebSocket telemetry log |
| `CHAT-02` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **History Load & Pagination**: Open chat conversation; verify historical messages load in correct chronological order with unread badge update. | Local Room/SQLite verification |
| `CHAT-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Network Interruption Recovery**: Sever Wi-Fi mid-conversation, restore connection; verify WebSocket reconnects and catches up missing messages without duplicate keys. | Reconnect handler trace |
| `CHAT-04` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Account Switch in Chat**: Switch accounts; verify conversation list immediately re-hydrates for current authenticated user only. | Account-switch guard trace |

---

### 2.4 Camera Hardware Zoom & Multi-Lens Discovery (Section 11 & #68)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `CAM-01` | Android | Modern Pixel / Samsung | Android 14+ | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **CameraX Physical Zoom**: Query `CameraInfo.zoomState`; verify physical multi-lens discovery; execute continuous hardware pinch zoom without preview cropping. | CameraX `setZoomRatio` logs |
| `CAM-02` | iOS | iPhone 13 Pro Max / 15 Pro | iOS 17/18 | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **AVFoundation Hardware Lenses**: Query `AVCaptureDevice.DiscoverySession`; verify exact physical presets discovered: `0.5x \| 1x \| 3x` (or `5x`). No fake `2x` unless physical telephoto. | AVFoundation device array log |
| `CAM-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Continuous Pinch Zoom & Capture Consistency**: Pinch from 1.0x to 3.2x; verify live indicator displays `3.2x`; capture photo; verify captured image matches preview framing exactly. | Photo metadata & bounds audit |
| `CAM-04` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Front / Back Camera Switching**: Switch to front-facing camera; verify zoom resets to 1.0x; switch back to rear; verify physical presets re-populate correctly. | Lens switch transition video |

---

### 2.5 Location & Maps Grounding Matrix (Section 12 & #66)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `LOC-01` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **GPS Acquisition & Permission Flow**: Request location permission; verify graceful fallback on deny; acquire high-accuracy GPS coordinates on allow. | CoreLocation / FusedLocation log |
| `LOC-02` | Android + iOS | Physical hardware | Target matrix | RC1 | `CODE_VERIFIED` | **Server-Authoritative Maps Grounding**: Request postmark story; verify request routed strictly through hosted Edge Function `/maps-grounding`. Zero mobile Gemini keys. | `scripts/check-ios-location-grounding-readiness.sh` |
| `LOC-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **429 Rate Limiting & Fallback**: Exceed 10 requests/min; verify client displays localized rate limit message and preserves user-entered location text. | HTTP 429 response handling log |

---

### 2.6 Push Notification Delivery (Section 13 & #61)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `PUSH-01` | Android | Physical hardware | Target matrix | RC1 | `EXTERNAL_SETUP_REQUIRED` | **FCM Token Registration**: Launch signed release build; verify FCM device token registered in `push_device_tokens` table. Requires `google-services.json`. | DB token record audit |
| `PUSH-02` | Android | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **FCM Notification Reception**: Trigger chat message or trade request from User B; verify notification received in both foreground (banner) and background (system tray). | Notification tray screenshot |
| `PUSH-03` | iOS | Physical iPhone | Target matrix | RC1 | `EXTERNAL_SETUP_REQUIRED` | **APNs Production Registration**: Launch TestFlight/App Store signed build; verify APNs token sent to server. Requires production APNs provisioning profile. | Device registration payload |
| `PUSH-04` | iOS | Physical iPhone | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **APNs Production Reception**: Trigger push; verify notification delivered with sound and badge. Note: Cannot verify on unsigned simulator. | APNs delivery receipt |

---

### 2.7 Cloud Stamp Trade Isolation (Section 14 & #57)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `TRADE-01`| Android + iOS | Dual physical devices | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Trade Lifecycle**: User A proposes trade with stamp; User B accepts via Edge Function `/accept-trade`; verify atomic exchange and stamp ownership transfer. | E2E Phase 11 contract audit |
| `TRADE-02`| Android + iOS | Dual physical devices | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Zero Foreign Media Exposure**: Verify User A cannot access original private media URLs of User B before trade acceptance; verify foreign albums remain locked. | RLS: `009_cloud_stamp_trade.sql` |

---

### 2.8 3D Stamp Book Full Real-Device Pass (Section 15, #74, #76, #78, #80)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `ALBUM-01`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Shelf & Book Open**: Open 3D album from shelf; verify smooth 3D cover open transition, 2.5D page shadow rendering, and stable 60fps frame rate. | GPU profiler / Systrace |
| `ALBUM-02`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Interactive Page Curl**: Drag page corner; verify realistic page curl deformation, proper z-index layering, and natural snap-to-turn when release past threshold. | Screen recording / gesture trace |
| `ALBUM-03`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Stamp Manipulation Gestures**: Enter Edit mode; add stamp from Vault; drag, pinch-to-resize, and two-finger rotate; verify bounds clamping and zero gesture fighting. | Gesture arbitration log |
| `ALBUM-04`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Placement & Lifecycle Persistence**: Place stamp on Page 2, add Page 3, reorder pages; close app, reopen offline; verify exact stamp coordinates, rotation, and page order restored. | Local Room & SQLite layout rows |
| `ALBUM-05`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Empty Page & Deletion Safeguards**: Attempt to delete page containing stamps (rejected with warning); delete empty page (succeeds atomically and reindexes spreads). | RPC: `remove_album_page` log |
| `ALBUM-06`| Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **Stress & Performance (Many Stamps)**: Populate spread with 20+ stamps; flip pages rapidly; verify zero memory leaks, no low-memory terminations (OOM), and smooth rendering. | Xcode Instruments / Android Studio Profiler |

---

### 2.9 Localization & Language Switching (Section 16 & #70)

| Case ID | Platform | Device Model | OS Version | App Build | Status | Notes & Verification Scope | Evidence Reference |
|---|---|---|---|---|---|---|---|
| `LOC-01` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **System Vietnamese OS**: Set device language to Tiếng Việt; launch app; verify 100% of UI (Auth, Feed, PostDetail, Album, Camera, Settings, Deletion) renders in Vietnamese. | Full UI walkthrough video |
| `LOC-02` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **System English OS**: Set device language to English; verify 100% of UI renders in English with zero missing key placeholders. | UI audit screenshots |
| `LOC-03` | Android + iOS | Physical hardware | Target matrix | RC1 | `LIVE_DEVICE_VERIFICATION_REQUIRED` | **In-App Explicit Switch**: Override system language via in-app Settings; verify immediate live update across all active screens without app restart. | LanguageManager state audit |

---

### 2.10 Physical Performance & Cold Launch (Section 18)

| Metric | Target Standard | Measurement Protocol | Status |
|---|---|---|---|
| **Cold Launch Time** | < 2.0s to interactive feed | Measured from app process start to first frame rendered on physical device | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
| **Feed Scrolling Frame Rate** | 58–60 FPS (zero stutter) | Monitored via Android Systrace / iOS Core Animation Instruments during rapid scroll | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
| **Camera Cold Start** | < 1.0s to active preview | Measured from tap on Camera icon to first preview buffer rendered | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
| **Album 3D Page Turn** | Sustained 60 FPS | Monitored during continuous page curl gestures across 10-page book | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
| **Peak Memory Footprint** | < 250 MB under heavy edit | Measured with 15 camera captures + 20 album stamp placements active | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
| **Background / Foreground** | Instant resume (< 200ms) | Background app mid-edit for 5 minutes; return to foreground; verify zero lost state | `LIVE_DEVICE_VERIFICATION_REQUIRED` |
