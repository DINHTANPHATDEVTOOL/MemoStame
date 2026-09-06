# Production Push Notifications Configuration & Readiness Guide

This document specifies the architecture, external credential setup, capability wiring, and real-device deployment requirements for **MemoStamp Production Push Notifications** (Android FCM & iOS APNs).

---

## 1. Overview & Architectural Principles

- **Cloud Data Authority**: Push notifications are lightweight wakeup signals containing event metadata (`event_id`, `event_type`, `route`, `target_user_id`). The Supabase Postgres database and REST endpoints remain the sole data authority. When opening a push notification, the application navigates to the target screen and authoritatively re-fetches/reconciles live data.
- **Server-Authoritative Events**: Clients cannot specify recipient user IDs, notification titles, or message bodies. The `dispatch-push` Edge Function requires caller JWT authentication, authoritatively verifies that the caller was the real creator/sender of the event entity (Direct Message or Friend Request), and derives recipient user IDs server-side.
- **Atomic Token Registry**: Device tokens are registered via `register_push_device_token` SQL RPC. When a new account authenticates on a previously used device, the device token is atomically reassigned to the active user, purging any stale associations.
- **Event Deduplication**: Both Android (`PushEventDeduper`) and iOS (`IOSPushEventDeduper`) maintain bounded caches (250 entries, 24h TTL) to prevent duplicate banners between Realtime and Push delivery.
- **Zero Token Logging**: Push device tokens are treated as confidential credentials and are never printed to logs or transmitted outside the secure token registry RPC.
- **Fail-Closed Lifecycle**: In the absence of external provider configuration (e.g. PR CI or development environments without Firebase/Apple certificates), push initialization cleanly fails closed without crashing, without inventing fake tokens, and without claiming active delivery.

---

## 2. Android: Firebase Cloud Messaging (FCM HTTP v1) Setup

### App Identifier
- **Package Name**: `com.mipastudio.memostamp`

### Firebase Console Setup
1. Log in to [Firebase Console](https://console.firebase.google.com/).
2. Create or select the project `MemoStamp`.
3. Add an Android app with Package Name: `com.mipastudio.memostamp`.
4. Download `google-services.json`.
   - **Placement**: Place into `androidApp/google-services.json` (or supply path via `-PgoogleServicesJsonPath=...` in CI).
   - **Gradle Plugin Processing**: `androidApp/build.gradle.kts` dynamically applies the official `com.google.gms.google-services` plugin only when `google-services.json` is present.
   - **Default FirebaseApp Initialization**: When `google-services.json` is processed by Gradle, the `process<Variant>GoogleServices` task generates Android resources that allow `FirebaseInitProvider` to initialize the default `FirebaseApp` on application startup.
   - **Absence Handling**: If `google-services.json` is absent (such as in standard GitHub Actions pull request CI), the plugin is skipped, `FirebaseApp.getApps(context)` remains empty, and `PushTokenManager.getFcmTokenSafe` returns `null` cleanly without crashing.
5. In Firebase Project Settings -> **Cloud Messaging**, ensure **Firebase Cloud Messaging API (V1)** is enabled.

### Server Credentials for Supabase Edge Functions
1. Go to Firebase Console -> **Project Settings** -> **Service Accounts**.
2. Click **Generate New Private Key**.
3. Copy the entire content of the generated service-account JSON file.
4. Set the secret in your hosted Supabase project:
   ```bash
   supabase secrets set FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key_id":"...","private_key":"...","client_email":"...","client_id":"...","auth_uri":"...","token_uri":"...","auth_provider_x509_cert_url":"...","client_x509_cert_url":"..."}'
   ```
   *(Never commit this JSON file to source control or embed it into client APK/AAB builds. It is git-ignored by `*service-account*.json` and `**/google-services.json`).*

---

## 3. iOS: Apple Push Notification service (APNs) Setup

### App Identifier
- **Bundle ID**: `com.mipastudio.memostamp`

### Apple Developer Portal Setup
1. Log in to [Apple Developer Portal](https://developer.apple.com/account/).
2. Navigate to **Certificates, Identifiers & Profiles** -> **Identifiers**.
3. Select App ID `com.mipastudio.memostamp`.
4. In **Capabilities**, check **Push Notifications** and save.
5. Navigate to **Keys** and click **Create a key** (+).
   - Key Name: `MemoStamp APNs Key`
   - Check **Apple Push Notifications service (APNs)**.
   - Download the `.p8` key file. Note your **Key ID** (10 characters, e.g. `ABC123DEFG`).
   - Note your **Team ID** (e.g. `XYZ987ABCD`).

### Xcode Entitlements & Target Capability Wiring
- **Entitlements File**: `iosApp/iosApp/iosApp.entitlements` defines the Apple Push Notifications capability:
  ```xml
  <key>aps-environment</key>
  <string>development</string>
  ```
- **Project Target Configuration**: `iosApp/iosApp.xcodeproj/project.pbxproj` specifies:
  ```text
  CODE_SIGN_ENTITLEMENTS = iosApp/iosApp.entitlements;
  ```
  in both `Debug` and `Release` build configurations for the `iosApp` target.
- **Background Modes Assessment**: The production APNs payload sent by `dispatch-push` contains standard user-visible alerts (`alert`, `sound`, `badge`) and does not use `"content-available": 1`. Therefore, silent background processing modes (`remote-notification`) are intentionally not enabled, keeping the capability footprint minimal and secure.
- **Error Handling**: When running on unsigned hardware or in simulator environments, `didFailToRegisterForRemoteNotificationsWithError` fails safely without logging tokens or inventing fake registrations.

### Server Credentials for Supabase Edge Functions
Set the following secrets in your hosted Supabase project:
```bash
supabase secrets set APNS_KEY_ID="ABC123DEFG"
supabase secrets set APNS_TEAM_ID="XYZ987ABCD"
supabase secrets set APNS_BUNDLE_ID="com.mipastudio.memostamp"
supabase secrets set APNS_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg...
-----END PRIVATE KEY-----"
```
*(Never commit `.p8` files, certificates, or mobileprovision files to git. They are git-ignored by `*.p8`, `*.mobileprovision`, `*.p12`).*

---

## 4. Test Seam & CI Provider Modes

In automated test runs and CI pipelines:
- `PUSH_PROVIDER_MODE=mock`: Dispatches pushes to local mock provider URL (`http://127.0.0.1:54325/mock-push`).
- No external Apple or Google accounts are required for automated CI tests to pass.
- In production, ensure `PUSH_PROVIDER_MODE=real` (or leave unset, which defaults to real provider dispatch).

---

## 5. Release Preflight Inspector

To verify push readiness without exposing secrets, run:
```bash
bash scripts/check-release-push-readiness.sh
```
The script inspects:
- Git hygiene: confirms no private keys (`.p8`), service accounts, or `google-services.json` are tracked.
- Android Gradle plugin & manifest wiring.
- iOS entitlement source and target build settings (`CODE_SIGN_ENTITLEMENTS`).
- Server secret documentation.

---

## 6. Live Delivery Status Declaration

| Component | Status | Description |
|---|---|---|
| **CODE_READY** | **PASS** | Android FCM service, Gradle Google Services plugin, iOS entitlements file, and Xcode target configuration are fully implemented and verified. |
| **EXTERNAL_CREDENTIAL_SETUP_REQUIRED** | **PENDING_DEPLOYMENT** | Requires developer/release team to supply production `androidApp/google-services.json` and deploy server secrets (`FCM_SERVICE_ACCOUNT_JSON`, `APNS_PRIVATE_KEY`, `APNS_KEY_ID`, `APNS_TEAM_ID`). |
| **LIVE_DEVICE_VERIFIED** | **NOT_CLAIMED_IN_CI** | Requires manual verification on physical signed hardware (see checklist below). |

---

## 7. Real Device Verification Checklist

Perform these tests on physical devices after code signing and credential deployment:

### Android Physical Device Checklist
- [ ] Install signed APK/AAB built with `google-services.json`.
- [ ] Launch application -> verify default `FirebaseApp` initializes without error.
- [ ] Log in with Account A -> verify FCM token is fetched and registered to `push_device_tokens` with `p_platform = android`, `p_provider = fcm`, `p_environment = production`.
- [ ] Send direct message to Account A while app is in **background** -> verify heads-up notification arrives.
- [ ] Send direct message to Account A while app is **terminated** -> verify system notification arrives.
- [ ] Tap notification -> verify application launches and routes directly to the chat conversation.
- [ ] Send friend request to Account A -> verify friend request notification arrives and routes to Friends screen.
- [ ] Block Account B -> send message from B to A -> verify push is suppressed.
- [ ] Log out Account A -> verify token is marked inactive on server.
- [ ] Log in Account B on same device -> verify token is atomically reassigned to Account B.

### iPhone Physical Device Checklist
- [ ] Install signed IPA provisioned with Apple Developer Team certificate and App ID `com.mipastudio.memostamp`.
- [ ] Launch application -> accept Push Notifications system permission prompt.
- [ ] Log in with Account A -> verify APNs token (hex) is registered to `push_device_tokens` with `p_platform = ios`, `p_provider = apns`, `p_environment = production`.
- [ ] Send direct message to Account A while app is in **background** -> verify notification banner and sound.
- [ ] Send direct message to Account A while app is in **foreground** -> verify banner displays (or dedupes if Realtime already received).
- [ ] Tap notification -> verify deep link routes to chat conversation.
- [ ] Block Account B -> send message from B to A -> verify push is suppressed.
- [ ] Log out Account A -> verify token is unregistered.
