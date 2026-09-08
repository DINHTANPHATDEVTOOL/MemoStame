# MemoStamp Release Candidate 1 (RC1) — Release Blocker Audit Log (#84)

This document tracks all potential and resolved release-blocking issues for MemoStamp (`com.mipastudio.memostamp`) RC1.

### Blocker Severity Criteria (Section 21)
Items qualify as Release Blockers **only** if they cause:
1. Application crash or unhandled runtime exception.
2. Security or privacy vulnerability (e.g. leaked credentials, unauthorized data access, RLS bypass).
3. Irrecoverable data loss (e.g. dropped comments, lost stamp placements, deleted albums).
4. Release compilation or packaging build failure (e.g. ProGuard/R8 errors, Xcode archive failure).
5. Broken authentication or session invalidation.
6. Broken core product capability (e.g. camera capture failure, feed loading freeze).
7. Apple App Store or Google Play Store rejection blocker (e.g. missing Privacy Manifest, missing account deletion).
8. Critical performance defect (e.g. severe UI freeze > 500ms, OOM crash).

*Note: Minor visual discrepancies, cosmetic padding adjustments, and non-blocking polish are strictly excluded from this blocker register.*

---

## RC1 Blocker Table

| ID | Area | Severity | Status | Required Action / Resolution |
|---|---|---|---|---|
| `BLK-01` | Build & CI | Critical | **RESOLVED** | **Supabase CLI Rate Limit on CI**: `supabase/setup-cli@v1` hit GitHub API rate limits during unauthenticated release lookup. Fixed by wiring authenticated `github-token: ${{ secrets.GITHUB_TOKEN }}` in `.github/workflows/ios.yml`. |
| `BLK-02` | Feed / Social | High | **RESOLVED** | **PostDetail Comment Reliability Gap (#82)**: Failed comment submissions previously lost typed user text. Resolved via unified `CommentSubmissionError` taxonomy, persistent draft state, submission locking, and localized retry banners. |
| `BLK-03` | Album Persistence | High | **RESOLVED** | **Album Page Lifecycle Cloud Contract (#80)**: Deleting empty pages previously caused foreign key constraint violations or race conditions. Resolved via database migration `013_album_page_lifecycle.sql` with atomic stored procedures and ON DELETE CASCADE. |
| `BLK-04` | Store Compliance | Critical | **RESOLVED** | **In-App Account Deletion & Apple Privacy Manifest (#64)**: App Store mandates complete server-authoritative account deletion and `PrivacyInfo.xcprivacy`. Resolved via Edge Function `/delete-account` and fully declared Apple Privacy Manifest. |
| `BLK-05` | Security / Secrets | Blocker | **RESOLVED** | **Zero Secret Credentials in Mobile Binaries**: Mobile clients must never package `service_role` keys, Gemini API keys, or private signing certificates. Resolved via server-authoritative Edge Functions (`maps-grounding`, `dispatch-push`, `accept-trade`) and strictly external CI signing. |
| `BLK-06` | Android Packaging | Blocker | **RESOLVED** | **Play Store Upload AAB Signing & ProGuard Seam (#62)**: Release builds must support external keystore injection without committing secrets. Resolved via `androidApp/build.gradle.kts` environment variable signing seams and ephemeral CI verification. |
| `BLK-07` | iOS Distribution | Blocker | **RESOLVED** | **App Store Archive & Production APNs Seam (#62)**: iOS release archive must target `generic/platform=iOS` and export signed IPA with production APNs entitlement. Resolved via `codemagic.yaml` and `ExportOptions-AppStore.plist`. |
| `BLK-08` | External Config | Medium | **EXTERNAL_SETUP_REQUIRED** | **Store Console Production Credential Injection**: Before publishing to public app stores, real production secrets (Google Play upload keystore, Apple distribution certificate, APNs p8, and FCM `google-services.json`) must be configured in production hosting environments. |
| `BLK-09` | Physical Validation | Medium | **LIVE_DEVICE_VERIFICATION_REQUIRED** | **Physical Hardware Multi-Lens & Push Verification**: Physical multi-lens camera zoom (0.5x, 1x, 3x) and retail cellular push notification receipt require live hardware validation on representative device matrix. |

---

## Summary Assessment for RC1 Auto-Merge

- **Total Active Code Blockers**: **0** (All repository code, build configurations, and test suites are green and verified).
- **Remaining Items**: Strictly confined to external cloud service credentials (`EXTERNAL_SETUP_REQUIRED`) and physical device execution (`LIVE_DEVICE_VERIFICATION_REQUIRED`).
- **Conclusion**: Repository code is **100% CODE_READY** for Release Candidate 1.
