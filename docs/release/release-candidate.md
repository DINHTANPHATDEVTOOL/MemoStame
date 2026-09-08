# MemoStamp Release Candidate Pipeline & Production Distribution (#62)

This guide documents the Release Candidate pipeline for producing store-uploadable Android (Google Play) and iOS (App Store / TestFlight) artifacts for MemoStamp (`com.mipastudio.memostamp`), while strictly keeping all credentials external to git.

---

## 1. Android Release Candidate & Play App Signing

### 1.1 Google Play App Signing Model
MemoStamp uses the standard **Google Play App Signing** architecture:
- **Upload Key**: Generated locally by the release engineer and used exclusively to sign the Android App Bundle (`.aab`) before uploading to Google Play Console.
- **App Signing Key**: Held securely in Google Cloud infrastructure. Google verifies the upload signature, removes the upload signature, re-signs the distribution APKs with the production app signing key, and distributes them to end users.
- **Security Rule**: The upload keystore (`.jks` / `.keystore`) and its passwords must **never** be committed to git or printed in logs.

### 1.2 Android Signing Configuration Seam
The Android application build configuration in `androidApp/build.gradle.kts` connects to an external keystore via environment variables or Gradle project properties:

| Environment Variable | Gradle Property | Description |
|---|---|---|
| `MEMOSTAMP_RELEASE_STORE_FILE` | `MEMOSTAMP_RELEASE_STORE_FILE` | Absolute path to the release upload keystore file (`.jks` / `.keystore`) |
| `MEMOSTAMP_RELEASE_STORE_PASSWORD` | `MEMOSTAMP_RELEASE_STORE_PASSWORD` | Keystore password |
| `MEMOSTAMP_RELEASE_KEY_ALIAS` | `MEMOSTAMP_RELEASE_KEY_ALIAS` | Key alias in keystore |
| `MEMOSTAMP_RELEASE_KEY_PASSWORD` | `MEMOSTAMP_RELEASE_KEY_PASSWORD` | Private key password |
| `MEMOSTAMP_REQUIRE_RELEASE_SIGNING` | `MEMOSTAMP_REQUIRE_RELEASE_SIGNING` | When set to `"true"`, Gradle strictly fails if any signing property is missing |

#### Behavior:
- When signing credentials are not supplied and `MEMOSTAMP_REQUIRE_RELEASE_SIGNING` is not `"true"`, the build succeeds unsigned (used for PR compile CI).
- When `MEMOSTAMP_REQUIRE_RELEASE_SIGNING="true"`, the build **fails immediately** with clear error messages if any credential is missing or the keystore file does not exist.

### 1.3 Android Version Overrides
Version numbers can be overridden dynamically at build time without editing source files:
- `MEMOSTAMP_VERSION_CODE`: Positive integer (e.g. `42`). Malformed strings or non-positive integers are rejected by Gradle. Defaults to `1`.
- `MEMOSTAMP_VERSION_NAME`: Semantic version string matching `^[0-9]+(\.[0-9]+)+(-[a-zA-Z0-9.]+)?$` (e.g. `1.0.0`, `1.2.0-rc1`). Defaults to `"1.0"`.

### 1.4 Generating an Android Release Bundle Locally
```bash
export MEMOSTAMP_RELEASE_STORE_FILE="/path/to/upload-keystore.jks"
export MEMOSTAMP_RELEASE_STORE_PASSWORD="your-store-password"
export MEMOSTAMP_RELEASE_KEY_ALIAS="your-key-alias"
export MEMOSTAMP_RELEASE_KEY_PASSWORD="your-key-password"
export MEMOSTAMP_REQUIRE_RELEASE_SIGNING="true"
export MEMOSTAMP_VERSION_CODE=100
export MEMOSTAMP_VERSION_NAME="1.0.0"

./gradlew :androidApp:bundleRelease
```
The signed AAB will be output to:
`androidApp/build/outputs/bundle/release/androidApp-release.aab`

Verify the signature:
```bash
jarsigner -verify -verbose androidApp/build/outputs/bundle/release/androidApp-release.aab | grep "jar verified"
```

---

## 2. iOS Release Candidate & App Store / TestFlight Pipeline

### 2.1 Distribution Model
MemoStamp uses **Codemagic CI/CD** (or direct Xcode execution) for production iOS release candidate builds:
- **Build Target**: Archives for `generic/platform=iOS` targeting `iphoneos`.
- **Signing**: Uses external Apple Distribution Certificate (`.p12`) and App Store Provisioning Profile securely supplied via Codemagic environment variables or secret groups.
- **Export**: Exports a valid signed `.ipa` using `iosApp/ExportOptions-AppStore.plist`.

### 2.2 Codemagic Workflows
`codemagic.yaml` defines two independent workflows:
1. `ios-kmp-workflow`: Unsigned simulator build for GitHub Actions and PR compilation checks (requires zero Apple credentials).
2. `ios-app-store-release`: Production App Store & TestFlight release candidate pipeline:
   - Applies external version and build number overrides (`MEMOSTAMP_VERSION_NAME` / `MEMOSTAMP_VERSION_CODE`).
   - Configures code signing from the Codemagic `app_store_credentials` secret group.
   - Archives release bundle:
     ```bash
     xcodebuild archive \
       -project iosApp/iosApp.xcodeproj \
       -scheme iosApp \
       -destination "generic/platform=iOS" \
       -archivePath build/ios/xcarchive/iosApp.xcarchive \
       MARKETING_VERSION="$TARGET_VERSION" \
       CURRENT_PROJECT_VERSION="$TARGET_BUILD"
     ```
   - Exports signed IPA:
     ```bash
     xcodebuild -exportArchive \
       -archivePath build/ios/xcarchive/iosApp.xcarchive \
       -exportOptionsPlist iosApp/ExportOptions-AppStore.plist \
       -exportPath build/ios/ipa
     ```
   - Verifies the exported IPA has a valid signature and production APNs entitlement:
     ```bash
     codesign -d --entitlements :- /path/to/extracted/iosApp.app | grep -A 1 'aps-environment' | grep 'production'
     ```
   - Publishes to TestFlight via App Store Connect API keys configured in `app_store_credentials` secret group:
     ```yaml
     publishing:
       app_store_connect:
         api_key: $APP_STORE_CONNECT_PRIVATE_KEY
         key_id: $APP_STORE_CONNECT_KEY_IDENTIFIER
         issuer_id: $APP_STORE_CONNECT_ISSUER_ID
         submit_to_testflight: true
     ```

---

## 3. Separation of Configuration vs External Secrets

| Item | Exposure Tier | Storage Location |
|---|---|---|
| Application / Bundle ID (`com.mipastudio.memostamp`) | **Build-Safe** | `androidApp/build.gradle.kts`, `project.pbxproj` |
| Version code / name | **Build-Safe** | Environment / build parameters |
| Supabase URL & Anon Key | **Client-Safe** | Hardcoded or injected via `.env` / BuildConfig |
| Google Services config (`google-services.json`) | **External Deployment** | Placed in `androidApp/` on release machine (git-ignored) |
| Android Upload Keystore (`.jks`) & Passwords | **STRICT SECRET** | Secret manager / CI environment variables |
| Apple Distribution Certificate (`.p12`) & Provisioning Profile | **STRICT SECRET** | Codemagic secret group `app_store_credentials` |
| App Store Connect API Key (`APP_STORE_CONNECT_PRIVATE_KEY`, `APP_STORE_CONNECT_KEY_IDENTIFIER`, `APP_STORE_CONNECT_ISSUER_ID`) | **STRICT SECRET** | Codemagic secret group `app_store_credentials` (Protected) |
| FCM Service Account JSON | **STRICT SECRET** | Supabase Edge Functions secret (`FCM_SERVICE_ACCOUNT_JSON`) |
| APNs Auth Key (`.p8`) | **STRICT SECRET** | Supabase Edge Functions secrets (`APNS_PRIVATE_KEY`, `APNS_KEY_ID`, `APNS_TEAM_ID`) |
| Supabase `service_role` key | **STRICT SECRET** | Never in mobile code; hosted Supabase internal only |

---

## 4. Hosted Backend Production Contracts

Before deploying a Release Candidate to live users, verify that the hosted Supabase environment matches all production contracts:

### Database Migrations
Migrations `001` through `011` must be applied in order:
1. `001_auth_social_rls.sql`
2. `002_schema_contract_and_rls_hardening.sql`
3. `003_deployment_safety_and_id_contract.sql`
4. `004_add_direct_messages_realtime_publication.sql`
5. `005_media_storage_and_feed_replies.sql`
6. `006_push_device_tokens.sql`
7. `007_user_blocks_and_abuse_reports.sql`
8. `008_block_privacy_oracle_hotfix.sql`
9. `009_cloud_stamp_trade.sql`
10. `010_social_abuse_rate_limits.sql`
11. `011_maps_grounding_rate_limits.sql`

### Edge Functions
The following 4 Edge Functions must be deployed:
- `delete-account` (GDPR account deletion)
- `dispatch-push` (FCM v1 and APNs push notification delivery)
- `accept-trade` (Cloud-authoritative stamp trading)
- `maps-grounding` (Server-side Gemini location grounding)

### Deep Link Allow-List
The Supabase Dashboard must have the recovery deep link allow-listed:
- Redirect URL: `memostamp://auth/recovery`

---

## 5. Release Candidate Preflight Inspector

To verify readiness without exposing secrets, execute the release candidate inspector:
```bash
bash scripts/check-release-candidate-readiness.sh
```

### Output States
- `CODE_READY: PASS`: All source code, Gradle configurations, Xcode settings, entitlements, and migration files are verified and correctly wired.
- `EXTERNAL_SETUP_REQUIRED: PENDING_DEPLOYMENT`: Indicates external secrets (keystore, certificates, hosted provider keys) must be supplied in the deployment environment.
- `LIVE_DEVICE_VERIFICATION: PENDING_SIGNED_HARDWARE`: Physical testing on signed hardware is required before general store release.
