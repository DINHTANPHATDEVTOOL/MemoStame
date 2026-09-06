# Google Play Console Data Safety Questionnaire Mapping (#64)

This guide maps the actual production data practices of MemoStamp (`com.mipastudio.memostamp`) directly to the **Google Play Console Data Safety** questionnaire sections.

Use this document to complete the Play Console submission without ambiguity.

---

## 1. High-Level Data Safety Overview

| Question in Play Console | MemoStamp Declaration | Details |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Collects data for app functionality (accounts, photos, location, DMs, push tokens). |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | All network traffic uses secure HTTPS / TLS 1.3 to Supabase, FCM, and Edge Functions. |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Users can permanently delete their account and all associated data inside the app (Settings > Delete Account) or via the public web deletion form. |
| Is your app built for or directed at children? | **No** | General audience application (Terms require 13+ or local age of digital consent). |

---

## 2. Detailed Data Category Declarations

### 2.1 Personal Info

#### Name
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored in `public.profiles.display_name`)
- **Required or Optional?**: Optional (user can customize or leave default)
- **Purposes**: App functionality, Account management, Personalization
- **Deletion**: Deleted immediately when account is deleted.

#### Email Address
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored in `auth.users.email`)
- **Required or Optional?**: Required (for account creation & login)
- **Purposes**: App functionality, Account management, Developer communications (password recovery)
- **Deletion**: Deleted immediately when account is deleted.

#### User IDs
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (Supabase Auth UUID `auth.uid`)
- **Required or Optional?**: Required (core account identification)
- **Purposes**: App functionality, Account management
- **Deletion**: Deleted immediately when account is deleted.

---

### 2.2 Photos and Videos

#### Photos
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored in `stamp-media` bucket and `public.stamps`)
- **Required or Optional?**: Required for creating stamps (core feature)
- **Purposes**: App functionality
- **Deletion**: Deleted when the stamp is deleted by the user or when the account is deleted.

---

### 2.3 Messages

#### Emails or Text Messages (In-App Direct Messages)
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored in `public.direct_messages`)
- **Required or Optional?**: Optional (feature only used if user chats with mutual friends)
- **Purposes**: App functionality
- **Deletion**: Deleted when the user deletes their account.

---

### 2.4 Location

#### Approximate Location
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored as part of stamp record)
- **Required or Optional?**: Optional (user can skip location tagging or select a general city)
- **Purposes**: App functionality
- **Deletion**: Deleted when stamp or account is deleted.

#### Precise Location
- **Collected?**: Yes
- **Shared?**: No
- **Processed ephemerally?**: No (stored in `public.stamps.latitude`, `public.stamps.longitude`)
- **Required or Optional?**: Optional (requires explicit `ACCESS_FINE_LOCATION` runtime grant)
- **Purposes**: App functionality (attaching postmark geographical coordinates to stamp)
- **Deletion**: Deleted when stamp or account is deleted.

---

### 2.5 Device or Other IDs

#### Device or Other IDs
- **Collected?**: Yes
- **Shared?**: No (FCM tokens are sent to Google FCM for notification routing only)
- **Processed ephemerally?**: No (stored in `public.push_device_tokens`)
- **Required or Optional?**: Optional (user can disable notifications)
- **Purposes**: App functionality (delivering push notifications for chats, friend requests, trades)
- **Deletion**: Marked inactive on logout; purged on account deletion.

---

### 2.6 User Content & Social Interactions

#### Other In-App User Content
- **Collected?**: Yes (feed posts, comments, replies, post reactions, trade requests, abuse reports)
- **Shared?**: No
- **Processed ephemerally?**: No (stored in Supabase PostgreSQL tables)
- **Required or Optional?**: Optional (user choice to participate in community feed and trading)
- **Purposes**: App functionality, Fraud prevention and safety (abuse reports)
- **Deletion**: Deleted on account deletion. (Accepted trade copies owned by recipient are retained with sender anonymized).

---

## 3. Android Runtime Permissions Breakdown

| Permission | Category | Justification & User Experience |
|---|---|---|
| `android.permission.CAMERA` | Camera | Required to take live photos for creating commemorative stamps. Prompted when opening camera screen. |
| `android.permission.ACCESS_FINE_LOCATION` | Location | Used to tag stamp with precise GPS coordinates for geographical postmark authenticity. Prompted only during location picker. |
| `android.permission.ACCESS_COARSE_LOCATION` | Location | Used as fallback when precise GPS is unavailable or denied by user. |
| `android.permission.POST_NOTIFICATIONS` | Notifications | Android 13+ (API 33+) runtime prompt for push alerts (direct messages, friend requests, stamp trades). |
| `android.permission.INTERNET` | Network | Connecting to Supabase backend API and Edge Functions over TLS. |
| `android.permission.ACCESS_NETWORK_STATE` | Network | Checking device offline/online connectivity status. |

*No advertising permissions (`AD_ID`) are declared or requested.*
