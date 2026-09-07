import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct PassportScreenView: View {
    let repository: SharedMemoStampRepository
    var onLogout: (() -> Void)? = nil
    var onAccountDeleted: (() -> Void)? = nil
    @Environment(\.presentationMode) var presentationMode

    @StateObject private var langManager = AppLanguageManager.shared
    @State private var showEditProfile: Bool = false
    @State private var showSettingsModal: Bool = false

    var user: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "",
            username: "memostamp_collector",
            displayName: "MemoStamp Collector",
            avatarUrl: nil,
            bio: "Sưu tầm ký ức qua từng con tem bưu chính",
            stampsCreatedCount: Int32(0),
            stampsCollectedCount: Int32(0),
            placesVisitedCount: Int32(0)
        )
    }

    var badges: [PassportBadge] {
        (repository.badges.value as? [PassportBadge]) ?? []
    }

    var friendsCount: Int {
        ((repository.friends.value as? [FriendItem]) ?? []).count
    }

    var collectionsCount: Int {
        ((repository.collections.value as? [CollectionItem]) ?? []).count
    }

    private func badgeIconName(_ key: String) -> String {
        switch key.lowercased() {
        case "travel", "plane", "✈️": return "airplane"
        case "cafe", "coffee", "☕": return "cup.and.saucer.fill"
        case "art", "palette", "🎨": return "paintpalette.fill"
        case "special", "crown", "👑": return "crown.fill"
        case "nature", "tree", "🌲": return "leaf.fill"
        case "heart", "love", "💖": return "heart.fill"
        case "camera", "📸": return "camera.fill"
        default: return "star.fill"
        }
    }

    var actualStampsCount: Int {
        ((repository.stamps.value as? [StampItem]) ?? []).count
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.title3.bold())
                        .foregroundColor(MSColors.ink)
                }

                Spacer()

                Text("PROFILE")
                    .font(.headline.bold())
                    .foregroundColor(MSColors.ink)

                Spacer()

                HStack(spacing: 14) {
                    Button(action: { showEditProfile = true }) {
                        Image(systemName: "pencil")
                            .font(.title3)
                            .foregroundColor(MSColors.stamp)
                    }

                    Button(action: { showSettingsModal = true }) {
                        Image(systemName: "gearshape.fill")
                            .font(.title3)
                            .foregroundColor(MSColors.ink)
                    }
                }
            }
            .padding()

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Profile Header Card (Golden Ring Viền mạ vàng)
                    VStack(spacing: 12) {
                        AsyncImage(url: URL(string: user.avatarUrl ?? "")) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(MSColors.lightGrey)
                            }
                        }
                        .frame(width: 88, height: 88)
                        .clipShape(Circle())
                        .overlay(
                            ZStack {
                                Circle().stroke(MSColors.gold, lineWidth: 3)
                                Circle().stroke(MSColors.gold.opacity(0.5), lineWidth: 1).padding(-4)
                            }
                        )
                        .shadow(color: MSColors.gold.opacity(0.2), radius: 6, x: 0, y: 3)

                        Text(user.displayName)
                            .font(.title3.bold())
                            .foregroundColor(MSColors.ink)

                        Text("@" + user.username)
                            .font(.subheadline)
                            .foregroundColor(MSColors.grey)

                        Text("“" + user.bio + "”")
                            .font(.caption)
                            .italic()
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(.top, 12)

                    // Stats Counters Row (Tem dán, Bạn bè, Bộ sưu tập)
                    HStack(spacing: 14) {
                        StatBox(title: langManager.localized("profile_stat_stamps"), value: "\(max(actualStampsCount, Int(user.stampsCreatedCount)))")
                        StatBox(title: langManager.localized("profile_stat_friends"), value: "\(friendsCount)")
                        StatBox(title: langManager.localized("profile_stat_collections"), value: "\(collectionsCount)")
                    }
                    .padding(.horizontal)

                    // Passport Badges & Stamps
                    VStack(alignment: .leading, spacing: 14) {
                        Text(langManager.localized("profile_title"))
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.grey)
                            .padding(.horizontal)

                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            ForEach(badges, id: \.title) { badge in
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack(spacing: 6) {
                                        Image(systemName: badgeIconName(badge.iconEmoji))
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(MSColors.stamp)
                                        Text(badge.title)
                                            .font(.subheadline.bold())
                                            .foregroundColor(MSColors.ink)
                                        Spacer()
                                        if badge.isUnlocked {
                                            Image(systemName: "checkmark.seal.fill")
                                                .foregroundColor(MSColors.stamp)
                                        }
                                    }
                                    Text(badge.subtitle)
                                        .font(.caption2)
                                        .foregroundColor(MSColors.grey)
                                }
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(14)
                                .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
                            }
                        }
                        .padding(.horizontal)

                        // Settings & Logout Row Buttons
                        HStack(spacing: 12) {
                            Button(action: { showSettingsModal = true }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "gearshape.fill")
                                    Text(langManager.localized("profile_settings_title"))
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(MSColors.ink)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.white)
                                .cornerRadius(16)
                                .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                            }

                            Button(action: {
                                presentationMode.wrappedValue.dismiss()
                                if let onLogout = onLogout {
                                    onLogout()
                                } else {
                                    let currentUid = (repository.currentUser.value as? UserProfile)?.uid ?? ""
                                    if IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) {
                                        _ = IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                    }
                                    SupabaseAuthService.shared.signOut { _ in }
                                    repository.resetUserScopedState()
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "rectangle.portrait.and.arrow.right")
                                    Text(langManager.localized("profile_logout"))
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color.red)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(Color.red.opacity(0.08))
                                .cornerRadius(16)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 10)
                    }
                }
                .padding(.bottom, 140)
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(isPresented: $showEditProfile) {
            EditProfileSheetView(repository: repository)
        }
        .sheet(isPresented: $showSettingsModal) {
            ProfileSettingsSheetView(repository: repository, onLogout: onLogout, onAccountDeleted: onAccountDeleted)
        }
    }
}

struct EditProfileSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode

    @State private var displayName: String = ""
    @State private var bio: String = ""
    @State private var isSavingProfile: Bool = false
    @State private var saveMessage: String? = nil

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("Edit Passport Profile")
                .font(.headline.bold())
                .foregroundColor(MSColors.ink)

            VStack(alignment: .leading, spacing: 14) {
                Text("DISPLAY NAME")
                    .font(.caption2.bold())
                    .foregroundColor(MSColors.grey)

                TextField("Enter Display Name", text: $displayName)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                Text("BIO NOTE")
                    .font(.caption2.bold())
                    .foregroundColor(MSColors.grey)

                TextEditor(text: $bio)
                    .frame(height: 80)
                    .padding(4)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                    )
            }
            .padding(.horizontal, 20)

            if let msg = saveMessage {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.red)
                    Text(msg)
                        .font(.caption.bold())
                        .foregroundColor(Color.red)
                }
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
            }

            Spacer()

            Button(action: {
                if isSavingProfile { return }

                // 1. get auth UID from Supabase session & 2. validate authenticated UID
                guard let authUid = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines),
                      IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(authUid) else {
                    saveMessage = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                    return
                }

                // 3. require repository UID == auth UID & 4. capture previous profile
                guard let previousProfile = repository.currentUser.value as? UserProfile,
                      previousProfile.uid == authUid else {
                    saveMessage = "Danh tính tài khoản không trùng khớp."
                    return
                }

                // 5. calculate displayName/bio candidate
                let trimmedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                let candidateDisplayName = trimmedName.isEmpty ? previousProfile.displayName : trimmedName
                if candidateDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    saveMessage = "Tên hiển thị không được để trống."
                    return
                }
                let candidateBio = bio.trimmingCharacters(in: .whitespacesAndNewlines)

                // 6. update repository candidate
                repository.updateProfile(displayName: candidateDisplayName, bio: candidateBio, avatarUrl: previousProfile.avatarUrl)

                // 7. save account-scoped local data
                let persisted = IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: authUid)
                if !persisted {
                    repository.setCurrentUser(profile: previousProfile)
                    saveMessage = "Không thể lưu trữ dữ liệu cục bộ."
                    return
                }

                // 8. call existing cloud profile update method
                isSavingProfile = true
                saveMessage = nil

                SupabaseAuthService.shared.updateAuthenticatedProfile(
                    userId: authUid,
                    displayName: candidateDisplayName,
                    bio: candidateBio,
                    avatarUrl: nil
                ) { result in
                    DispatchQueue.main.async {
                        self.isSavingProfile = false
                        switch result {
                        case .success:
                            // 9. cloud success: dismiss
                            self.saveMessage = nil
                            self.presentationMode.wrappedValue.dismiss()

                        case .failure(let error):
                            // 10. cloud failure: restore previous profile & local persistence, show visible failure, KEEP SHEET OPEN
                            self.repository.setCurrentUser(profile: previousProfile)
                            _ = IOSLocalPersistenceStore.shared.saveData(repository: self.repository, userId: authUid)
                            self.saveMessage = error.localizedDescription
                        }
                    }
                }
            }) {
                HStack {
                    if isSavingProfile {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .padding(.trailing, 4)
                    }
                    Text(isSavingProfile ? "Đang lưu..." : "Save Profile Changes")
                        .font(.body.bold())
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(isSavingProfile ? MSColors.stamp.opacity(0.6) : MSColors.stamp)
                .cornerRadius(12)
            }
            .disabled(isSavingProfile)
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
        .onAppear {
            if let user = repository.currentUser.value as? UserProfile {
                displayName = user.displayName
                bio = user.bio
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
    }
}

struct StatBox: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundColor(MSColors.stamp)
            Text(title)
                .font(.caption2.bold())
                .foregroundColor(MSColors.grey)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.white)
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
    }
}

struct ProfileSettingsSheetView: View {
    let repository: SharedMemoStampRepository
    var onLogout: (() -> Void)? = nil
    var onAccountDeleted: (() -> Void)? = nil
    @Environment(\.presentationMode) var presentationMode
    @StateObject private var langManager = AppLanguageManager.shared

    @State private var displayName: String = ""
    @State private var bio: String = ""
    @State private var avatarUrl: String = ""
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var confirmPassword: String = ""
    @State private var passwordToastMessage: String? = nil
    @State private var isUpdatingPassword: Bool = false
    @State private var showPhotoPicker: Bool = false
    @State private var selectedAvatarImage: UIImage? = nil
    @State private var isSavingProfile: Bool = false
    @State private var profileSaveMessage: String? = nil

    @State private var showBlockedUsersSheet: Bool = false
    @State private var showDeleteAccountSheet: Bool = false
    @State private var deletePassword: String = ""
    @State private var isDeletingAccount: Bool = false
    @State private var deleteAccountError: String? = nil

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Sheet Grabber Indicator
                    Capsule()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 36, height: 4)
                        .padding(.top, 8)

                    // 1. Language Preference Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "globe")
                                .foregroundColor(MSColors.stamp)
                            Text(langManager.localized("settings_language_section"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        HStack(spacing: 8) {
                            ForEach(AppLanguageMode.allCases) { mode in
                                Button(action: {
                                    langManager.setLanguageMode(mode)
                                }) {
                                    VStack(spacing: 2) {
                                        HStack(spacing: 4) {
                                            Text(mode.displayName)
                                                .font(.system(size: 12, weight: .bold))
                                            if langManager.currentMode == mode {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .font(.system(size: 12))
                                            }
                                        }
                                        if mode == .system && langManager.currentMode == .system {
                                            Text("(\(langManager.effectiveLanguageCode == "vi" ? "Tiếng Việt" : "English"))")
                                                .font(.system(size: 10))
                                                .opacity(0.8)
                                        }
                                    }
                                    .foregroundColor(langManager.currentMode == mode ? .white : MSColors.ink)
                                    .padding(.vertical, 8)
                                    .padding(.horizontal, 6)
                                    .frame(maxWidth: .infinity)
                                    .background(langManager.currentMode == mode ? MSColors.stamp : Color.white)
                                    .cornerRadius(12)
                                    .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                                }
                            }
                        }
                    }
                    .padding(.horizontal)

                    Divider().padding(.horizontal)

                    // 2. Profile Details Edit Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "person.text.rectangle")
                                .foregroundColor(MSColors.stamp)
                            Text(langManager.localized("profile_edit"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.localized("auth_display_name"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            TextField(langManager.localized("auth_display_name_hint"), text: $displayName)
                                .font(.body)
                                .foregroundColor(MSColors.ink)
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(12)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.localized("profile_avatar_title"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)

                            HStack(spacing: 16) {
                                ZStack {
                                    if let img = selectedAvatarImage {
                                        Image(uiImage: img)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 60, height: 60)
                                            .clipShape(Circle())
                                    } else {
                                        AsyncImage(url: URL(string: avatarUrl)) { phase in
                                            if let image = phase.image {
                                                image.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                Circle().fill(MSColors.mint)
                                                    .overlay(Text(String(displayName.prefix(1)).uppercased()).font(.title2.bold()).foregroundColor(MSColors.ink))
                                            }
                                        }
                                        .frame(width: 60, height: 60)
                                        .clipShape(Circle())
                                    }
                                    Circle()
                                        .stroke(MSColors.stamp, lineWidth: 2)
                                        .frame(width: 62, height: 62)
                                }

                                Button(action: { showPhotoPicker = true }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "photo.on.rectangle.angled")
                                            .font(.subheadline)
                                        Text(langManager.localized("profile_choose_library"))
                                            .font(.caption.bold())
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(MSColors.stamp.opacity(0.12))
                                    .foregroundColor(MSColors.stamp)
                                    .cornerRadius(10)
                                }

                                if selectedAvatarImage != nil {
                                    Button(action: {
                                        selectedAvatarImage = nil
                                        let current = repository.currentUser.value as? UserProfile
                                        avatarUrl = current?.avatarUrl ?? ""
                                        profileSaveMessage = nil
                                    }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.gray)
                                            .font(.title3)
                                    }
                                }
                            }

                            TextField("Hoặc nhập URL: https://...", text: $avatarUrl)
                                .font(.caption)
                                .foregroundColor(MSColors.ink)
                                .padding(10)
                                .background(Color.white)
                                .cornerRadius(8)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.2), lineWidth: 1))
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.localized("auth_bio"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            TextEditor(text: $bio)
                                .font(.body)
                                .foregroundColor(MSColors.ink)
                                .frame(height: 70)
                                .padding(4)
                                .background(Color.white)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                        }
                    }
                    .padding(.horizontal)

                    Divider().padding(.horizontal)

                    // 3. Password & Security Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "lock.shield")
                                .foregroundColor(MSColors.stamp)
                            Text(langManager.localized("settings_password_section"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        SecureField(langManager.localized("auth_password_current"), text: $currentPassword)
                            .font(.body)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))

                        SecureField(langManager.localized("auth_password_new"), text: $newPassword)
                            .font(.body)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))

                        SecureField(langManager.localized("auth_password_confirm"), text: $confirmPassword)
                            .font(.body)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.2), lineWidth: 1))

                        if let toast = passwordToastMessage {
                            let isSuccess = toast.contains("thành công") || toast.contains("Success") || toast.contains("successfully")
                            HStack(spacing: 6) {
                                Image(systemName: isSuccess ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                                    .foregroundColor(isSuccess ? Color.green : Color.red)
                                Text(toast)
                                    .font(.caption.bold())
                                    .foregroundColor(isSuccess ? Color.green : Color.red)
                            }
                        }

                        Button(action: {
                            if isUpdatingPassword { return }
                            if currentPassword.isEmpty {
                                passwordToastMessage = langManager.localized("auth_err_empty_identifier")
                                return
                            }
                            if newPassword.count < 6 {
                                passwordToastMessage = langManager.localized("auth_err_password_too_short")
                                return
                            }
                            if newPassword != confirmPassword {
                                passwordToastMessage = langManager.localized("auth_err_password_match")
                                return
                            }

                            isUpdatingPassword = true
                            passwordToastMessage = langManager.localized("common_loading")

                            SupabaseAuthService.shared.changePassword(currentPassword: currentPassword, newPassword: newPassword) { result in
                                DispatchQueue.main.async {
                                    self.isUpdatingPassword = false
                                    switch result {
                                    case .failure(let error):
                                        let errMsg = error.localizedDescription
                                        self.passwordToastMessage = errMsg
                                    case .success:
                                        self.passwordToastMessage = self.langManager.localized("settings_password_updated")
                                        self.currentPassword = ""
                                        self.newPassword = ""
                                        self.confirmPassword = ""
                                    }
                                }
                            }
                        }) {
                            HStack {
                                Image(systemName: "key.fill")
                                Text(langManager.localized("settings_update_password"))
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MSColors.stamp)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(MSColors.stamp.opacity(0.1))
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)

                    Divider().padding(.horizontal)

                    // Privacy & Safety Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "hand.raised.slash.fill")
                                .foregroundColor(MSColors.stamp)
                            Text(langManager.localized("settings_safety_section"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        Button(action: {
                            showBlockedUsersSheet = true
                        }) {
                            HStack {
                                Image(systemName: "person.crop.circle.badge.xmark")
                                    .foregroundColor(MSColors.stamp)
                                Text(langManager.localized("settings_blocked_users"))
                                    .font(.subheadline.bold())
                                    .foregroundColor(MSColors.ink)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption)
                                    .foregroundColor(MSColors.grey)
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.lightGrey, lineWidth: 1))
                        }
                    }
                    .padding(.horizontal)

                    Divider().padding(.horizontal)

                    // 4. Save & Logout Action Row
                    VStack(spacing: 10) {
                        if let msg = profileSaveMessage {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.red)
                                Text(msg)
                                    .font(.caption.bold())
                                    .foregroundColor(Color.red)
                            }
                        }

                        Button(action: {
                            if isSavingProfile { return }

                            // 1. validate authenticated UID
                            guard let authUid = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines),
                                  IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(authUid) else {
                                profileSaveMessage = langManager.localized("profile_error_session_invalid")
                                return
                            }

                            // 2. validate repository current user UID == auth UID
                            guard let previousProfile = repository.currentUser.value as? UserProfile,
                                  previousProfile.uid == authUid else {
                                profileSaveMessage = langManager.localized("profile_error_identity_mismatch")
                                return
                            }

                            // 3. capture previous UserProfile snapshot: previousProfile

                            // 4. calculate candidate displayName/bio/avatar
                            let trimmedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
                            let candidateDisplayName = trimmedName.isEmpty ? previousProfile.displayName : trimmedName
                            if candidateDisplayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                profileSaveMessage = langManager.localized("profile_error_display_name_empty")
                                return
                            }
                            let candidateBio = bio.trimmingCharacters(in: .whitespacesAndNewlines)

                            // 5. validate avatar remote URL contract
                            if selectedAvatarImage != nil {
                                let lower = avatarUrl.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                                if lower.hasPrefix("file:") || lower.hasPrefix("/") || !SupabaseAuthService.isSafeRemoteAvatarUrl(avatarUrl) {
                                    profileSaveMessage = langManager.localized("profile_error_avatar_local_sync")
                                    return
                                }
                            }

                            let trimmedAvatarInput = avatarUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !trimmedAvatarInput.isEmpty && !SupabaseAuthService.isSafeRemoteAvatarUrl(trimmedAvatarInput) {
                                profileSaveMessage = langManager.localized("profile_error_avatar_local_sync")
                                return
                            }

                            let candidateAvatarUrl: String? = trimmedAvatarInput.isEmpty ? previousProfile.avatarUrl : trimmedAvatarInput
                            if let candidate = candidateAvatarUrl, !candidate.isEmpty && !SupabaseAuthService.isSafeRemoteAvatarUrl(candidate) {
                                profileSaveMessage = langManager.localized("profile_error_avatar_local_sync")
                                return
                            }

                            // 6. apply candidate to repository
                            repository.updateProfile(displayName: candidateDisplayName, bio: candidateBio, avatarUrl: candidateAvatarUrl)

                            // 7. persist using account-scoped persistence
                            let persisted = IOSLocalPersistenceStore.shared.saveData(
                                repository: repository,
                                userId: authUid
                            )
                            if !persisted {
                                repository.setCurrentUser(profile: previousProfile)
                                profileSaveMessage = langManager.localized("profile_error_local_persist")
                                return
                            }

                            // 8. call SupabaseAuthService.shared.updateAuthenticatedProfile
                            isSavingProfile = true
                            profileSaveMessage = nil

                            SupabaseAuthService.shared.updateAuthenticatedProfile(
                                userId: authUid,
                                displayName: candidateDisplayName,
                                bio: candidateBio,
                                avatarUrl: candidateAvatarUrl
                            ) { result in
                                DispatchQueue.main.async {
                                    self.isSavingProfile = false
                                    switch result {
                                    case .success:
                                        // 9. if CLOUD SUCCESS
                                        self.profileSaveMessage = nil
                                        self.presentationMode.wrappedValue.dismiss()

                                    case .failure(let error):
                                        // 10. if CLOUD FAILURE: rollback & persist
                                        self.repository.setCurrentUser(profile: previousProfile)
                                        _ = IOSLocalPersistenceStore.shared.saveData(
                                            repository: self.repository,
                                            userId: authUid
                                        )
                                        let errMsg = error.localizedDescription
                                        self.profileSaveMessage = errMsg
                                    }
                                }
                            }
                        }) {
                            HStack {
                                if isSavingProfile {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .padding(.trailing, 4)
                                }
                                Text(isSavingProfile ? langManager.localized("profile_saving") : langManager.localized("profile_save_changes"))
                                    .font(.body.bold())
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(isSavingProfile ? MSColors.stamp.opacity(0.6) : MSColors.stamp)
                            .cornerRadius(14)
                        }
                        .disabled(isSavingProfile)

                        Button(action: {
                            if let url = URL(string: PrivacyConfig.privacyPolicyUrl), UIApplication.shared.canOpenURL(url) {
                                UIApplication.shared.open(url)
                            }
                        }) {
                            HStack {
                                Image(systemName: "hand.raised.fill")
                                Text(langManager.localized("profile_privacy_policy"))
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MSColors.stamp)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(MSColors.stamp.opacity(0.08))
                            .cornerRadius(14)
                        }

                        Button(action: {
                            presentationMode.wrappedValue.dismiss()
                            if let onLogout = onLogout {
                                onLogout()
                            } else {
                                let currentUid = (repository.currentUser.value as? UserProfile)?.uid ?? ""
                                if IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) {
                                    _ = IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                }
                                SupabaseAuthService.shared.signOut { _ in }
                                repository.resetUserScopedState()
                            }
                        }) {
                            HStack {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                Text(langManager.localized("profile_logout"))
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.red.opacity(0.08))
                            .cornerRadius(14)
                        }

                        Button(action: {
                            deletePassword = ""
                            deleteAccountError = nil
                            showDeleteAccountSheet = true
                        }) {
                            HStack {
                                Image(systemName: "trash.fill")
                                Text(langManager.localized("profile_delete_account"))
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.red.opacity(0.12))
                            .cornerRadius(14)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 30)
                }
            }
            .background(MSColors.paper.ignoresSafeArea())
            .navigationTitle(langManager.localized("profile_settings_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(langManager.localized("common_close")) {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(MSColors.stamp)
                }
            }
        }
        .sheet(isPresented: $showBlockedUsersSheet) {
            BlockedUsersManagementSheetView()
        }
        .sheet(isPresented: $showDeleteAccountSheet) {
            NavigationView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(langManager.localized("profile_delete_account_warning"))
                    .font(.subheadline)
                    .foregroundColor(MSColors.ink)

                    Text(langManager.localized("auth_password_current") + ":")
                    .font(.caption.bold())
                    .foregroundColor(MSColors.grey)

                    SecureField(langManager.localized("auth_password_current"), text: $deletePassword)
                        .font(.subheadline)
                        .foregroundColor(MSColors.ink)
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                        .disabled(isDeletingAccount)

                    if let err = deleteAccountError {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(err)
                                .font(.caption.bold())
                                .foregroundColor(Color.red)
                        }
                    }

                    Spacer()

                    Button(action: {
                        if isDeletingAccount { return }
                        let pass = deletePassword.trimmingCharacters(in: .whitespacesAndNewlines)
                        if pass.isEmpty {
                            deleteAccountError = langManager.localized("auth_err_empty_identifier")
                            return
                        }

                        isDeletingAccount = true
                        deleteAccountError = nil

                        SupabaseAuthService.shared.deleteCurrentAccount(currentPassword: pass) { result in
                            DispatchQueue.main.async {
                                self.isDeletingAccount = false
                                switch result {
                                case .failure(let error):
                                    self.deleteAccountError = error.localizedDescription
                                case .success:
                                    self.showDeleteAccountSheet = false
                                    self.presentationMode.wrappedValue.dismiss()
                                    if let onAccountDeleted = self.onAccountDeleted {
                                        onAccountDeleted()
                                    } else if let onLogout = self.onLogout {
                                        onLogout()
                                    } else {
                                        self.repository.resetUserScopedState()
                                    }
                                }
                            }
                        }
                    }) {
                        HStack {
                            if isDeletingAccount {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(.trailing, 4)
                            }
                            Text(isDeletingAccount ? langManager.localized("common_loading") : langManager.localized("profile_delete_account_confirm"))
                                .font(.body.bold())
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(isDeletingAccount || deletePassword.isEmpty ? Color.red.opacity(0.6) : Color.red)
                        .cornerRadius(14)
                    }
                    .disabled(isDeletingAccount || deletePassword.isEmpty)
                }
                .padding()
                .background(MSColors.paper.ignoresSafeArea())
                .navigationTitle(langManager.localized("profile_delete_account"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button(langManager.localized("common_cancel")) {
                            if !isDeletingAccount {
                                showDeleteAccountSheet = false
                                deletePassword = ""
                                deleteAccountError = nil
                            }
                        }
                        .disabled(isDeletingAccount)
                        .foregroundColor(MSColors.stamp)
                    }
                }
            }
        }
        .sheet(isPresented: $showPhotoPicker) {
            PhotoLibraryPicker { img in
                selectedAvatarImage = img
                if let data = img.jpegData(compressionQuality: 0.8) {
                    let fileUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("user_avatar_\(Date().timeIntervalSince1970).jpg")
                    try? data.write(to: fileUrl)
                    avatarUrl = fileUrl.absoluteString
                }
            }
        }
        .onAppear {
            if let user = repository.currentUser.value as? UserProfile {
                displayName = user.displayName
                bio = user.bio
                avatarUrl = user.avatarUrl ?? ""
            }
        }
    }
}

struct PhotoLibraryPicker: UIViewControllerRepresentable {
    @Environment(\.presentationMode) var presentationMode
    var onImagePicked: (UIImage) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: PhotoLibraryPicker

        init(_ parent: PhotoLibraryPicker) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.onImagePicked(image)
            }
            parent.presentationMode.wrappedValue.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

struct BlockedUsersManagementSheetView: View {
    @Environment(\.presentationMode) var presentationMode
    @StateObject private var langManager = AppLanguageManager.shared
    @State private var blockedUsers: [SupabaseBlockedUserRecord] = []
    @State private var isLoading: Bool = true
    @State private var unblockingId: String? = nil
    @State private var message: String? = nil

    var body: some View {
        NavigationView {
            VStack {
                if isLoading {
                    ProgressView()
                        .padding(.top, 40)
                    Spacer()
                } else if blockedUsers.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "hand.raised.slash")
                            .font(.system(size: 40))
                            .foregroundColor(MSColors.grey.opacity(0.6))
                        Text(langManager.localized("safety_no_blocked_users"))
                            .font(.headline)
                            .foregroundColor(MSColors.ink)
                        Text(langManager.localized("safety_block_desc"))
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .padding(.top, 60)
                    Spacer()
                } else {
                    List {
                        ForEach(blockedUsers, id: \.id) { block in
                            HStack {
                                Circle()
                                    .fill(Color.red.opacity(0.12))
                                    .frame(width: 36, height: 36)
                                    .overlay(
                                        Image(systemName: "person.fill.xmark")
                                            .foregroundColor(.red)
                                            .font(.caption)
                                    )

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(langManager.localized("safety_blocked_user_item"))
                                        .font(.subheadline.bold())
                                        .foregroundColor(MSColors.ink)
                                    Text("ID: \(block.blockedId.prefix(12))...")
                                        .font(.caption2)
                                        .foregroundColor(MSColors.grey)
                                }

                                Spacer()

                                Button(action: {
                                    unblock(block.blockedId)
                                }) {
                                    if unblockingId == block.blockedId {
                                        ProgressView()
                                    } else {
                                        Text(langManager.localized("safety_unblock_button"))
                                            .font(.caption.bold())
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(MSColors.stamp.opacity(0.12))
                                            .foregroundColor(MSColors.stamp)
                                            .cornerRadius(10)
                                    }
                                }
                                .disabled(unblockingId != nil)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .listStyle(PlainListStyle())
                }

                if let msg = message {
                    Text(msg)
                        .font(.caption.bold())
                        .foregroundColor(MSColors.stamp)
                        .padding()
                }
            }
            .background(MSColors.paper.ignoresSafeArea())
            .navigationTitle(langManager.localized("settings_blocked_users"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(langManager.localized("common_close")) {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(MSColors.stamp)
                }
            }
            .onAppear {
                loadData()
            }
        }
    }

    private func loadData() {
        isLoading = true
        IOSFriendRepository.shared.loadBlockedUsers { result in
            isLoading = false
            switch result {
            case .success(let list):
                blockedUsers = list
            case .failure(let err):
                message = err.localizedDescription
            }
        }
    }

    private func unblock(_ blockedId: String) {
        unblockingId = blockedId
        IOSFriendRepository.shared.unblockUser(blockedId: blockedId) { result in
            unblockingId = nil
            switch result {
            case .success:
                blockedUsers.removeAll { $0.blockedId == blockedId }
                message = langManager.localized("safety_unblock_success")
            case .failure(let err):
                message = "Lỗi: \(err.localizedDescription)"
            }
        }
    }
}

enum PrivacyConfig {
    static let privacyPolicyUrl = "https://memostamp.mipastudio.com/privacy"
    static let accountDeletionUrl = "https://memostamp.mipastudio.com/account-deletion"
}

