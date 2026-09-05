# Production Push Notifications Configuration Guide

This document specifies the architecture, external credential setup, and deployment requirements for **MemoStamp Production Push Notifications** (Android FCM & iOS APNs) as implemented in **Task #51**.

---

## 1. Overview & Architectural Principles

- **Cloud Data Authority**: Push notifications are lightweight wakeup signals containing event metadata (`event_id`, `event_type`, `route`, `target_user_id`). The Supabase Postgres database and REST endpoints remain the sole data authority. When opening a push notification, the application navigates to the target screen and authoritatively re-fetches/reconciles live data.
- **Server-Authoritative Events**: Clients cannot specify recipient user IDs, notification titles, or message bodies. The `dispatch-push` Edge Function requires caller JWT authentication, authoritatively verifies that the caller was the real creator/sender of the event entity (Direct Message or Friend Request), and derives recipient user IDs server-side.
- **Atomic Token Registry**: Device tokens are registered via `register_push_device_token` SQL RPC. When a new account authenticates on a previously used device, the device token is atomically reassigned to the active user, purging any stale associations.
- **Event Deduplication**: Both Android (`PushEventDeduper`) and iOS (`IOSPushEventDeduper`) maintain bounded caches (250 entries, 24h TTL) to prevent duplicate banners between Realtime and Push delivery.

---

## 2. Android: Firebase Cloud Messaging (FCM HTTP v1) Setup

### App Identifier
- **Package Name**: `com.mipastudio.memostamp`

### Firebase Console Setup
1. Log in to [Firebase Console](https://console.firebase.google.com/).
2. Create or select the project `MemoStamp`.
3. Add an Android app with Package Name: `com.mipastudio.memostamp`.
4. Download `google-services.json`.
   - Local placement: place into `androidApp/google-services.json`.
   - Note: If `google-services.json` is not present, the Android app cleanly disables push registration without crashing or failing builds.
5. In Firebase Project Settings -> **Cloud Messaging**, ensure **Firebase Cloud Messaging API (V1)** is enabled.

### Server Credentials for Supabase Edge Functions
1. Go to Firebase Console -> **Project Settings** -> **Service Accounts**.
2. Click **Generate New Private Key**.
3. Copy the entire content of the generated service-account JSON file.
4. Set the secret in your hosted Supabase project:
   ```bash
   supabase secrets set FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key_id":"...","private_key":"...","client_email":"...","client_id":"...","auth_uri":"...","token_uri":"...","auth_provider_x509_cert_url":"...","client_x509_cert_url":"..."}'
   ```
   *(Never commit this JSON file to source control or embed it into client APK/AAB builds).*

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
*(Never commit `.p8` files, certificates, or mobileprovision files to git).*

---

## 4. Test Seam & CI Provider Modes

In automated test runs and CI pipelines:
- `PUSH_PROVIDER_MODE=mock`: Dispatches pushes to local mock provider URL (`http://127.0.0.1:54325/mock-push`).
- No external Apple or Google accounts are required for automated CI tests to pass.
- In production, ensure `PUSH_PROVIDER_MODE=real` (or leave unset, which defaults to real provider dispatch).

---

## 5. Live Delivery Status Declaration

As specified in the production readiness contract:

- **LIVE FCM DELIVERY**: `EXTERNAL_SETUP_REQUIRED` (Pending deployment of production Firebase project service account JSON).
- **LIVE APNS DELIVERY**: `EXTERNAL_SETUP_REQUIRED` (Pending deployment of production Apple Developer `.p8` auth key).
- **CODE & CONTRACT COMPLETION**: `PASS` (All schema, RLS, RPCs, Edge Functions, native Android FCM service, native iOS APNs bridge, deduplication, and mock E2E tests fully implemented and verified).
