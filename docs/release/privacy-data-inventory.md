# MemoStamp Production Privacy & Data Inventory (#64)

This document provides a complete and authoritative inventory of all personal data, identifiers, content, and device information processed by MemoStamp (`com.mipastudio.memostamp`) across Android, iOS, Supabase backend, Edge Functions, and external cloud infrastructure.

Every entry in this inventory is verified against actual production code, database schemas, and service integrations.

---

## 1. Summary of Data Practices

- **Zero Advertising SDKs**: MemoStamp contains no advertising networks, no AdMob, no Meta Audience Network, and requests no advertising identifiers (no `AD_ID` / IDFA).
- **Zero Third-Party Tracking**: MemoStamp does not track users across apps or websites owned by other companies (`NSPrivacyTracking: false`).
- **No Sale of Personal Data**: Personal data is never sold, rented, or leased to third-party data brokers.
- **Server Authority**: Authentication and cloud operations are governed by Supabase Auth and Row Level Security (RLS).
- **In-App Account Deletion**: Users can permanently delete their accounts and personal data at any time from within the mobile app (`delete-account` Edge Function).

---

## 2. Comprehensive Data Inventory Matrix

| Data Category | Specific Data Element | Source / Feature | User / Device Supplied | Stored Server-Side | Linked to User Identity | Optional? | Permission Gated? | Business & App Purpose | Cloud Processors | Retention & Deletion Behavior | Apple Privacy Manifest Constant | Google Play Data Safety Field |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **Identity** | Email address | Authentication / Sign-up / Login | User supplied | Yes (`auth.users`) | Yes (`auth.uid`) | Required for account | No | Authentication, account security, password recovery | Supabase Auth | Retained while account active. Permanently purged on account deletion via `delete-account`. | `NSPrivacyCollectedDataTypeEmailAddress` | Personal info > Email address |
| **Identity** | User ID (`auth.uid`) | Supabase Auth UUID | System generated | Yes (`auth.users`, `public.profiles`) | Yes | Required for account | No | Account identity, database relational integrity, RLS policy enforcement | Supabase Postgres | Retained while account active. Permanently purged on account deletion via `delete-account`. | `NSPrivacyCollectedDataTypeUserID` | Personal info > User IDs |
| **Identity** | Display Name | User Profile (`public.profiles.display_name`) | User supplied | Yes (`public.profiles`) | Yes | Yes (defaults to email prefix) | No | Personalization, in-app social recognition by friends | Supabase Postgres | Updated by user anytime. Permanently deleted on account deletion. | `NSPrivacyCollectedDataTypeName` | Personal info > Name |
| **Identity** | Avatar Image URL | User Profile (`public.profiles.avatar_url`) | User supplied | Yes (`stamp-media` bucket, `public.profiles`) | Yes | Yes | Optional Camera / Photo Library | User profile customization | Supabase Storage & Postgres | Overwritten on update. Purged from storage on account deletion. | `NSPrivacyCollectedDataTypePhotosorVideos` | Personal info > Name / Photos |
| **User Content** | Stamp Photos / Images | Stamp Creation / Camera capture | User / Camera capture | Yes (`stamp-media` bucket) | Yes | Required for stamp creation | Yes (`CAMERA` / Camera Usage Description) | Core app feature: creating photographic commemorative stamps | Supabase Storage & Postgres | Stored until stamp deleted by user or account permanently deleted. | `NSPrivacyCollectedDataTypePhotosorVideos` | Photos and videos > Photos |
| **User Content** | Stamp Title & Story / Note | Stamp Creation / Postmark details | User supplied | Yes (`public.stamps`, `stamp_trade_requests`) | Yes | Yes | No | Contextual postmark storytelling and stamp details | Supabase Postgres | Deleted when stamp or account is deleted. | `NSPrivacyCollectedDataTypeOtherUserContent` | Other in-app content |
| **User Content** | Feed Posts & Captions | Community Feed (`public.posts`) | User supplied | Yes (`public.posts`) | Yes | Optional | No | Sharing stamps with friends and community | Supabase Postgres | Deleted when post is deleted or account is deleted. | `NSPrivacyCollectedDataTypeOtherUserContent` | Social / User generated content |
| **User Content** | Comments & Replies | Feed Social Interactions (`public.post_comments`, `post_replies`) | User supplied | Yes (`public.post_comments`, `public.post_replies`) | Yes | Optional | No | Social discussion around shared stamps | Supabase Postgres | Cascaded deletion on post or account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Social / User generated content |
| **User Content** | Post Reactions | Feed Reactions (`public.post_reactions`) | User supplied | Yes (`public.post_reactions`) | Yes | Optional | No | Expressing appreciation (likes, stamps) | Supabase Postgres | Cascaded deletion on post or account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Social / User generated content |
| **User Content** | Direct Messages | Chat Feature (`public.direct_messages`) | User supplied | Yes (`public.direct_messages`) | Yes (sender & recipient IDs) | Optional | No | 1-on-1 private messaging between mutual friends | Supabase Postgres, Realtime | Deleted when sender or recipient account is deleted. | `NSPrivacyCollectedDataTypeEmailsOrTextMessages` | Messages > Emails or text messages |
| **User Content** | Abuse Reports & Reasons | Moderation & Reporting (`public.abuse_reports`) | User supplied | Yes (`public.abuse_reports`) | Yes | Optional | No | Community safety, policy enforcement, anti-abuse | Supabase Postgres | Maintained for moderation; reporter linkage deleted on account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Other in-app content |
| **User Content** | Stamp Trades | Cloud Stamp Trade (`public.stamp_trade_requests`, `public.received_trade_stamps`) | User supplied | Yes (`public.stamp_trade_requests`, `public.received_trade_stamps`) | Yes | Optional | No | Trading stamp copies between friends | Supabase Postgres, Storage | When sender account is deleted, recipient-owned received copies are preserved with `original_sender_id` set to `NULL` (anonymized). | `NSPrivacyCollectedDataTypeOtherUserContent` | Other in-app content |
| **Location** | Precise Latitude & Longitude | Stamping Geolocation / GPS | Device sensor (GPS) | Yes (`public.stamps.latitude`, `public.stamps.longitude`) | Yes | Yes (user may skip or use coarse place) | Yes (`ACCESS_FINE_LOCATION` / Location When In Use) | Attaching geographical provenance to commemorative stamp | Supabase Postgres | Stored as part of stamp record. Purged on stamp or account deletion. | `NSPrivacyCollectedDataTypePreciseLocation` | Location > Precise location |
| **Location** | Coarse / Approximate Location | Stamping Geolocation fallback | Network / Cell tower / Wi-Fi | Yes (`public.stamps.latitude`, `public.stamps.longitude`) | Yes | Yes | Yes (`ACCESS_COARSE_LOCATION` / Location When In Use) | Approximate geographical provenance for stamps | Supabase Postgres | Stored as part of stamp record. Purged on stamp or account deletion. | `NSPrivacyCollectedDataTypeCoarseLocation` | Location > Approximate location |
| **Location** | Place Name / Formatted Address | Location Search / Stamping | User selected / Google Places API | Yes (`public.stamps.location`) | Yes | Yes | No | Human-readable place label on commemorative stamp | Supabase Postgres, Google Places SDK | Stored in stamp record. Purged on stamp or account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Location > Other location |
| **Location** | Gemini Maps Grounding Query | Story Enrichment | User query / coordinate text | Ephemeral processing | Yes | Optional | No | AI postmark historical story generation via server-side Edge Function | Google Gemini API (via Supabase Edge Function) | Ephemeral request/response; not permanently retained in AI provider training data. | `NSPrivacyCollectedDataTypeOtherUserContent` | Other in-app content |
| **Device / App Identifiers** | FCM Device Token | Firebase Cloud Messaging | Device / FCM SDK | Yes (`public.push_device_tokens`) | Yes (`auth.uid`) | Optional (can disable notifications) | Yes (`POST_NOTIFICATIONS` on Android 13+) | Delivering background & terminated push notifications | Firebase Cloud Messaging, Supabase | Updated on token rotation, marked inactive on logout, deleted on account deletion. | `NSPrivacyCollectedDataTypeDeviceID` | Device or other IDs |
| **Device / App Identifiers** | APNs Device Token | Apple Push Notification service | Device / iOS SDK | Yes (`public.push_device_tokens`) | Yes (`auth.uid`) | Optional (can disable notifications) | Yes (User notification prompt on iOS) | Delivering background & foreground remote push notifications | Apple Push Notification service, Supabase | Updated on token rotation, marked inactive on logout, deleted on account deletion. | `NSPrivacyCollectedDataTypeDeviceID` | Device or other IDs |
| **Device / App Identifiers** | Installation UUID | PushTokenManager / App Instance | Generated locally (`UUID.randomUUID()`) | Yes (`public.push_device_tokens.installation_id`) | Yes (`auth.uid`) | Automatic | No | Push token deduplication, multi-device session isolation | Supabase Postgres | Cleared on token reassignment or account deletion. | `NSPrivacyCollectedDataTypeDeviceID` | Device or other IDs |
| **Social Data** | Friendships & Requests | Social Graph (`public.friendships`, `public.friend_requests`) | User initiated | Yes (`public.friendships`, `public.friend_requests`) | Yes | Optional | No | Social connection, feed sharing, DM permissions | Supabase Postgres | Cascaded deletion on account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Social / User generated content |
| **Social Data** | User Blocks | Privacy & Safety (`public.user_blocks`) | User initiated | Yes (`public.user_blocks`) | Yes | Optional | No | Preventing harassment, suppressing push & feeds | Supabase Postgres | Cascaded deletion on account deletion. | `NSPrivacyCollectedDataTypeOtherUserContent` | Social / User generated content |

---

## 3. Third-Party Service Providers & Cloud Sub-Processors

| Processor | Purpose | Data Received | Transmission Security | Privacy Link / Authority |
|---|---|---|---|---|
| **Supabase Inc.** | Backend database (PostgreSQL), user authentication, encrypted file storage, Edge Functions hosting | Email, user ID, stamp media, posts, messages, push tokens, social graph | Encrypted in transit (TLS 1.3/HTTPS), encrypted at rest (AES-256) | [Supabase Privacy Policy](https://supabase.com/privacy) |
| **Google Cloud / Firebase (FCM)** | Server-authoritative push notification delivery for Android | FCM device tokens, push notification title/body, notification metadata | Encrypted in transit (HTTPS / FCM HTTP v1) | [Firebase Privacy & Security](https://firebase.google.com/support/privacy) |
| **Apple Inc. (APNs)** | Server-authoritative remote notification delivery for iOS | APNs device tokens, alert message text, sound, badge counts | Encrypted in transit (HTTPS / HTTP/2 with JWT token authentication) | [Apple Privacy Policy](https://www.apple.com/legal/privacy/) |
| **Google Places API** | Android location autocomplete and place lookup | User search text, coarse device coordinates (client-side only) | Encrypted in transit (HTTPS) | [Google Privacy Policy](https://policies.google.com/privacy) |
| **Google Gemini (Server-Side)** | AI location enrichment & postmark story generation | Location name and coordinates forwarded by server Edge Function (`maps-grounding`) | Encrypted in transit (HTTPS). No mobile API key. | [Google Generative AI Privacy](https://policies.google.com/privacy) |

---

## 4. Account Deletion Truth & Retention Contract

1. **In-App Direct Deletion**: Initiated via Settings > Delete Account. Requires password confirmation.
2. **Execution Flow**:
   - `delete-account` Edge Function authenticates the caller's JWT.
   - Storage media under `stamp-media/<uid>/` is immediately purged via Storage Admin API.
   - `auth.users` row is deleted via Admin API, triggering `ON DELETE CASCADE` across:
     - `public.profiles`
     - `public.stamps`
     - `public.posts`, `post_comments`, `post_replies`, `post_reactions`
     - `public.direct_messages`
     - `public.friendships`, `friend_requests`
     - `public.user_blocks`, `abuse_reports`
     - `public.push_device_tokens`
3. **Trade Stamps Exception**: When user A trades a stamp to user B, user B becomes the durable owner in `public.received_trade_stamps`. Upon user A's account deletion, user B retains their legitimate stamp copy, while user A's linkage is set to `NULL` (`original_sender_id = NULL`, anonymized).
4. **Local Device Purge**: Upon deletion, all local caches, SQLite Room / CoreData entities, user preferences, and push tokens are cleared.
