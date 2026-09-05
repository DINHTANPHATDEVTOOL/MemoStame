import Foundation
import shared

enum IOSSessionCoordinatorError: LocalizedError {
    case invalidAuthUserId
    case authAuthorityMismatch
    case sessionExpired

    var errorDescription: String? {
        switch self {
        case .invalidAuthUserId:
            return "Mã người dùng xác thực không hợp lệ."
        case .authAuthorityMismatch:
            return "Danh tính phiên đăng nhập không trùng khớp."
        case .sessionExpired:
            return "Phiên đăng nhập đã hết hạn."
        }
    }
}

final class IOSAuthenticatedSessionCoordinator {
    static let shared = IOSAuthenticatedSessionCoordinator()

    private init() {}

    func hydrate(
        session: AuthSessionData,
        repository: SharedMemoStampRepository,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        let uid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)

        // 1. Validate UID authority
        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(uid) else {
            completion(.failure(IOSSessionCoordinatorError.invalidAuthUserId))
            return
        }

        // 2. Verify current auth authority
        guard let currentAuthUid = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines),
              currentAuthUid == uid else {
            completion(.failure(IOSSessionCoordinatorError.authAuthorityMismatch))
            return
        }

        // 3. Cross-account safety: reset repository state before establishing new identity
        repository.resetUserScopedState()

        // 4. Create presentation fallback with REAL auth UID
        let cleanEmail = session.email.trimmingCharacters(in: .whitespacesAndNewlines)
        let usernameFromEmail: String
        if let atIdx = cleanEmail.firstIndex(of: "@") {
            usernameFromEmail = String(cleanEmail[..<atIdx])
        } else {
            usernameFromEmail = cleanEmail.isEmpty ? "user" : cleanEmail
        }
        let cleanUsername = usernameFromEmail.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_.")).inverted)
            .joined()

        let fallbackDisplayName = usernameFromEmail.capitalized
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: ".", with: " ")
        let fallbackAvatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
        let fallbackBio = "Sưu tầm ký ức qua từng con tem bưu chính 📮"

        let fallbackProfile = UserProfile(
            uid: uid,
            username: cleanUsername.isEmpty ? "user" : cleanUsername,
            displayName: fallbackDisplayName.isEmpty ? "Collector" : fallbackDisplayName,
            avatarUrl: fallbackAvatarUrl,
            bio: fallbackBio,
            stampsCreatedCount: Int32(0),
            stampsCollectedCount: Int32(0),
            placesVisitedCount: Int32(0)
        )
        repository.setCurrentUser(profile: fallbackProfile)

        // 5. Account-scoped local restore
        IOSLocalPersistenceStore.shared.loadData(into: repository, userId: uid)

        // 6. Cloud hydration (exact UID lookup)
        SupabaseSocialClient.shared.getPublicProfile(userId: uid) { profileResult in
            switch profileResult {
            case .success(let cloudProfile):
                if let cloud = cloudProfile {
                    let cloudUserId = cloud.userId.trimmingCharacters(in: .whitespacesAndNewlines)
                    // Strict cloud profile UID match
                    if cloudUserId == uid {
                        let currentProfile = repository.currentUser.value as? UserProfile
                        let stampsCreated = currentProfile?.stampsCreatedCount ?? Int32(0)
                        let stampsCollected = currentProfile?.stampsCollectedCount ?? Int32(0)
                        let placesVisited = currentProfile?.placesVisitedCount ?? Int32(0)

                        let finalUsername = !cloud.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? cloud.username
                            : (currentProfile?.username ?? fallbackProfile.username)
                        let finalDisplayName = !cloud.displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? cloud.displayName
                            : (currentProfile?.displayName ?? fallbackProfile.displayName)

                        let finalAvatarUrl: String?
                        if let ca = cloud.avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
                           !ca.isEmpty,
                           SupabaseAuthService.isSafeRemoteAvatarUrl(ca) {
                            finalAvatarUrl = ca
                        } else {
                            finalAvatarUrl = currentProfile?.avatarUrl ?? fallbackProfile.avatarUrl
                        }

                        let finalBio: String
                        if let cb = cloud.bio?.trimmingCharacters(in: .whitespacesAndNewlines), !cb.isEmpty {
                            finalBio = cb
                        } else {
                            finalBio = currentProfile?.bio ?? fallbackProfile.bio
                        }

                        let hydratedProfile = UserProfile(
                            uid: uid,
                            username: finalUsername,
                            displayName: finalDisplayName,
                            avatarUrl: finalAvatarUrl,
                            bio: finalBio,
                            stampsCreatedCount: stampsCreated,
                            stampsCollectedCount: stampsCollected,
                            placesVisitedCount: placesVisited
                        )
                        repository.setCurrentUser(profile: hydratedProfile)
                        IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: uid)
                    }
                }
                completion(.success(()))

            case .failure:
                // Network failure after valid session: retain local/fallback cache as authenticated state
                completion(.success(()))
            }
        }
    }
}
