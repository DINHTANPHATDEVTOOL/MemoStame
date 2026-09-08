# MemoStamp Release Candidate 1 (RC1) — Deployment & Store Handoff Guide (#84)

This guide documents the end-to-end operational procedures for deploying MemoStamp (`com.mipastudio.memostamp`) RC1 to hosted production infrastructure and publishing release artifacts to Google Play Store and Apple App Store / TestFlight.

---

## 1. Production Supabase Backend Preflight (Section 3)

### 1.1 Complete Migration Chain Audit (001 through 013)
The authoritative production migration chain comprises 13 sequential SQL migrations located in `supabase/migrations/`:

| Migration File | Primary Responsibility |
|---|---|
| `001_auth_social_rls.sql` | Users, posts, comments, likes, friendship relationships, and baseline RLS policies. |
| `002_schema_contract_and_rls_hardening.sql` | Hardened constraints, index optimization, and strict foreign keys. |
| `003_deployment_safety_and_id_contract.sql` | Consistent UUIDv4 primary key generation and deployment safety triggers. |
| `004_add_direct_messages_realtime_publication.sql` | Realtime publication for direct message conversations. |
| `005_media_storage_and_feed_replies.sql` | Public and private media storage buckets, and threaded comments. |
| `006_push_device_tokens.sql` | Push notification device token registration table and device deduplication. |
| `007_user_blocks_and_abuse_reports.sql` | User blocking, content abuse reports, and mutual isolation RLS. |
| `008_block_privacy_oracle_hotfix.sql` | Eliminated metadata leaks in blocked user search queries. |
| `009_cloud_stamp_trade.sql` | Cloud stamp trade proposals, atomic acceptance triggers, and trade audit trail. |
| `010_social_abuse_rate_limits.sql` | Server-authoritative rate limits for comments, posts, and friend requests. |
| `011_maps_grounding_rate_limits.sql` | Server-side rate limits and quota enforcement for location grounding. |
| `012_album_layout_persistence.sql` | 3D Stamp Book layout models and persisted placement coordinates. |
| `013_album_page_lifecycle.sql` | Atomic page lifecycle stored procedures (`append_album_page`, `remove_album_page`, `reorder_album_pages`) with ON DELETE CASCADE. |

### 1.2 Edge Functions Inventory
Four production Edge Functions in `supabase/functions/` must be deployed to the hosted Supabase project:
1. `delete-account`: Purges storage assets, cancels subscriptions, and deletes auth user record.
2. `dispatch-push`: Server-side push notification dispatch to FCM (Android) and APNs (iOS).
3. `accept-trade`: Atomic execution of cloud stamp ownership transfers between users.
4. `maps-grounding`: Server-authoritative reverse geocoding and Gemini postmark narrative generation.

### 1.3 Production Deployment Steps
Execute deployment using the official Supabase CLI against your linked production project:
```bash
# 1. Link CLI to production project
supabase link --project-ref "<your-production-project-ref>"

# 2. Deploy database migrations
supabase db push

# 3. Deploy all Edge Functions with secure JWT verification
supabase functions deploy delete-account --no-verify-jwt=false
supabase functions deploy dispatch-push --no-verify-jwt=false
supabase functions deploy accept-trade --no-verify-jwt=false
supabase functions deploy maps-grounding --no-verify-jwt=false
```

### 1.4 Required Secret Configuration (Names Only)
Configure Edge Function secrets via `supabase secrets set`. **Never print or commit secret values.**

| Secret Name | Intended Usage |
|---|---|
| `SUPABASE_SERVICE_ROLE_KEY` | High-privilege key for user deletion and push dispatch (server-only). |
| `GEMINI_API_KEY` | Google Gemini API key for server-side Maps grounding. |
| `FCM_PROJECT_ID` | Firebase project ID for Android push notifications. |
| `FCM_CLIENT_EMAIL` | Firebase service account email for HTTP v1 push dispatch. |
| `FCM_PRIVATE_KEY` | Firebase service account private key. |
| `APNS_KEY_ID` | Apple Push Notification service 10-character key ID. |
| `APNS_TEAM_ID` | Apple Developer Team ID (10 characters). |
| `APNS_TOPIC` | Bundle ID: `com.mipastudio.memostamp`. |
| `APNS_PRIVATE_KEY` | Apple Push Notification service Auth Key (`.p8` content). |

### 1.5 Supabase Auth Configuration
In the Supabase Dashboard (**Authentication** > **URL Configuration**):
- Set **Site URL**: `https://memostamp.mipastudio.com`
- Add to **Redirect URLs**:
  - `memostamp://auth/recovery` (Canonical mobile deep link for self-service password reset)

---

## 2. Android RC1 Release Artifact & Play Store Handoff (Sections 4 & 19)

### 2.1 Configuration Contract
- **Application ID**: `com.mipastudio.memostamp`
- **Build Type**: `release`
- **Minification (R8)**: `isMinifyEnabled = true`
- **Resource Shrinking**: `isShrinkResources = true`
- **Debuggable**: `false`
- **ProGuard Configuration**: `proguard-android-optimize.txt` and `proguard-rules.pro`

### 2.2 Generating the Signed Upload Android App Bundle (AAB)
Prepare production upload keystore credentials in your secure local environment or CI runner:
```bash
export MEMOSTAMP_RELEASE_STORE_FILE="/secure/path/to/upload-keystore.jks"
export MEMOSTAMP_RELEASE_STORE_PASSWORD="<upload-store-password>"
export MEMOSTAMP_RELEASE_KEY_ALIAS="<upload-key-alias>"
export MEMOSTAMP_RELEASE_KEY_PASSWORD="<upload-key-password>"
export MEMOSTAMP_REQUIRE_RELEASE_SIGNING="true"
export MEMOSTAMP_VERSION_CODE=100
export MEMOSTAMP_VERSION_NAME="1.0.0-rc1"

./gradlew :androidApp:bundleRelease --no-daemon
```
The output artifact is generated at:
`androidApp/build/outputs/bundle/release/androidApp-release.aab`

### 2.3 Signature Verification Protocol
Verify that the generated bundle is signed properly:
```bash
jarsigner -verify -verbose androidApp/build/outputs/bundle/release/androidApp-release.aab | grep "jar verified"
```

### 2.4 Google Play Console Handoff Checklist
- [ ] **Internal Testing Track**: Create a new release in Google Play Console and upload `androidApp-release.aab`.
- [ ] **Google Play App Signing**: Ensure Play App Signing is enrolled so Google derives device-specific split APKs.
- [ ] **Version Code**: Ensure `versionCode` increases monotonically (e.g., `100` -> `101`).
- [ ] **Data Safety Section**: Complete Data Safety declarations in accordance with `docs/release/google-play-data-safety.md`.
- [ ] **Privacy Policy**: Set store listing Privacy Policy URL to `https://memostamp.mipastudio.com/privacy`.
- [ ] **Account Deletion Link**: Set Account Deletion declaration URL to `https://memostamp.mipastudio.com/account-deletion`.

---

## 3. iOS RC1 Release Artifact & App Store / TestFlight Handoff (Sections 5 & 20)

### 3.1 Configuration Contract
- **Bundle Identifier**: `com.mipastudio.memostamp`
- **Archive Target**: `generic/platform=iOS` (targeting physical `iphoneos`)
- **Privacy Manifest**: `iosApp/iosApp/PrivacyInfo.xcprivacy` bundled in root target
- **Entitlements**: `aps-environment = production`

### 3.2 Building Archive & Exporting Signed IPA via Codemagic
MemoStamp uses Codemagic CI/CD (configured in `codemagic.yaml`) for automated production builds:
1. Configure Codemagic secret group `app_store_credentials`:
   - `APP_STORE_CONNECT_PRIVATE_KEY`
   - `APP_STORE_CONNECT_KEY_IDENTIFIER`
   - `APP_STORE_CONNECT_ISSUER_ID`
   - `CERTIFICATE_PRIVATE_KEY`
2. Run workflow `ios-app-store-release` with version overrides:
   - `MEMOSTAMP_VERSION_NAME="1.0.0"` (maps to `MARKETING_VERSION`)
   - `MEMOSTAMP_VERSION_CODE="100"` (maps to `CURRENT_PROJECT_VERSION`)
3. Codemagic exports the signed IPA using `iosApp/ExportOptions-AppStore.plist`.
4. Codemagic publishes directly to TestFlight using the App Store Connect API keys without requiring mixed integration authentication:
   ```yaml
   publishing:
     app_store_connect:
       api_key: $APP_STORE_CONNECT_PRIVATE_KEY
       key_id: $APP_STORE_CONNECT_KEY_IDENTIFIER
       issuer_id: $APP_STORE_CONNECT_ISSUER_ID
       submit_to_testflight: true
   ```

### 3.3 Verifying Exported IPA & APNs Production Entitlement
```bash
# Extract application from IPA
unzip -q build/ios/ipa/iosApp.ipa -d /tmp/ipa_inspect
APP_PATH=$(find /tmp/ipa_inspect/Payload -name "*.app" | head -n 1)

# Verify codesign
codesign -v --verbose=2 "$APP_PATH"

# Verify production APNs entitlement
ENTITLEMENTS=$(codesign -d --entitlements :- "$APP_PATH" 2>&1)
echo "$ENTITLEMENTS" | grep -A 1 'aps-environment' | grep 'production'
```

### 3.4 App Store Connect / TestFlight Handoff Checklist
- [ ] **TestFlight Internal Testing**: Upload IPA to App Store Connect and distribute build to Internal Testing group.
- [ ] **App Privacy Details (Nutrition Labels)**: Complete declarations according to `docs/release/privacy-data-inventory.md`.
- [ ] **Privacy Policy URL**: Point listing to `https://memostamp.mipastudio.com/privacy`.
- [ ] **Account Deletion**: Confirm review team can test in-app account deletion via Passport screen.
- [ ] **Purpose Strings**: Verify `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, and `NSLocationWhenInUseUsageDescription` in `Info.plist`.

---

## 4. Privacy & Store Compliance Summary (Section 6 & #64)

- **Tracking**: `NSPrivacyTracking = false`. Zero advertising identifiers (`idfa` / `AD_ID`). Zero 3rd-party tracking SDKs.
- **Data Safety Mapping**: Data collection strictly limited to user content (photos, stamps, comments), account identity (email), diagnostics (error logs), and location (postmark tagging).
- **Public Compliance Web Pages**:
  - Privacy Policy: `site/privacy/index.html` -> Deploy to `https://memostamp.mipastudio.com/privacy`
  - Account Deletion: `site/account-deletion/index.html` -> Deploy to `https://memostamp.mipastudio.com/account-deletion`
