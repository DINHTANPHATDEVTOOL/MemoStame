import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct PassportScreenView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode

    @StateObject private var langManager = AppLanguageManager.shared
    @State private var showEditProfile: Bool = false
    @State private var showSettingsModal: Bool = false

    var user: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "user_me",
            username: "user_memostamp",
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
        switch key {
        case "plane", "✈️": return "paperplane.fill"
        case "coffee", "☕": return "cup.and.saucer.fill"
        case "palette", "🎨": return "paintpalette.fill"
        case "crown", "👑": return "crown.fill"
        case "tree", "🌲": return "leaf.fill"
        case "heart", "💖": return "heart.fill"
        default: return "star.fill"
        }
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
                        StatBox(title: langManager.string(vi: "TEM DÁN", en: "STAMPS"), value: "\(user.stampsCreatedCount)")
                        StatBox(title: langManager.string(vi: "BẠN BÈ", en: "FRIENDS"), value: "\(friendsCount)")
                        StatBox(title: langManager.string(vi: "BỘ SƯU TẬP", en: "COLLECTIONS"), value: "\(collectionsCount)")
                    }
                    .padding(.horizontal)

                    // Passport Badges & Stamps
                    VStack(alignment: .leading, spacing: 14) {
                        Text(langManager.string(vi: "PASSPORT STAMPS & BADGES", en: "PASSPORT STAMPS & BADGES"))
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
                                    Text(langManager.string(vi: "Cài Đặt", en: "Settings"))
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
                                UserDefaults.standard.set(false, forKey: "isAuthenticated")
                                presentationMode.wrappedValue.dismiss()
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "rectangle.portrait.and.arrow.right")
                                    Text(langManager.string(vi: "Đăng Xuất", en: "Logout"))
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
        .onAppear {
            syncSavedProfile()
        }
        .sheet(isPresented: $showEditProfile) {
            EditProfileSheetView(repository: repository)
        }
        .sheet(isPresented: $showSettingsModal) {
            ProfileSettingsSheetView(repository: repository)
        }
    }

    private func syncSavedProfile() {
        let defaults = UserDefaults.standard
        if let savedName = defaults.string(forKey: "user_displayName"), !savedName.isEmpty {
            let savedUsername = defaults.string(forKey: "user_username") ?? "phat_memostamp"
            let savedBio = defaults.string(forKey: "user_bio") ?? "Sưu tầm ký ức qua từng con tem bưu chính 📮"
            let savedAvatar = defaults.string(forKey: "user_avatarUrl")
            let current = (repository.currentUser.value as? UserProfile)
            let updated = UserProfile(
                uid: current?.uid ?? "user_me",
                username: savedUsername,
                displayName: savedName,
                avatarUrl: savedAvatar ?? current?.avatarUrl,
                bio: savedBio,
                stampsCreatedCount: current?.stampsCreatedCount ?? 0,
                stampsCollectedCount: current?.stampsCollectedCount ?? 0,
                placesVisitedCount: current?.placesVisitedCount ?? 0
            )
            repository.setCurrentUser(profile: updated)
        }
    }
}

struct EditProfileSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode

    @State private var displayName: String = ""
    @State private var bio: String = ""

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

            Spacer()

            Button(action: {
                let current = (repository.currentUser.value as? UserProfile)
                let name = displayName.isEmpty ? (current?.displayName ?? "") : displayName
                let note = bio.isEmpty ? (current?.bio ?? "") : bio
                repository.updateProfile(displayName: name, bio: note, avatarUrl: nil)
                
                let defaults = UserDefaults.standard
                defaults.set(name, forKey: "user_displayName")
                defaults.set(note, forKey: "user_bio")
                
                presentationMode.wrappedValue.dismiss()
            }) {
                Text("Save Profile Changes")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(MSColors.stamp)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
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
    @Environment(\.presentationMode) var presentationMode
    @StateObject private var langManager = AppLanguageManager.shared

    @State private var displayName: String = ""
    @State private var bio: String = ""
    @State private var avatarUrl: String = ""
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var confirmPassword: String = ""
    @State private var passwordToastMessage: String? = nil

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
                            Text(langManager.string(vi: "NGÔN NGỮ ỨNG DỤNG", en: "APP LANGUAGE"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        HStack(spacing: 12) {
                            ForEach(AppLanguage.allCases) { lang in
                                Button(action: {
                                    langManager.setLanguage(lang)
                                }) {
                                    HStack(spacing: 6) {
                                        Text(lang.displayName)
                                            .font(.system(size: 13, weight: .bold))
                                        if langManager.currentLanguage == lang {
                                            Image(systemName: "checkmark.circle.fill")
                                                .font(.system(size: 14))
                                        }
                                    }
                                    .foregroundColor(langManager.currentLanguage == lang ? .white : MSColors.ink)
                                    .padding(.vertical, 10)
                                    .padding(.horizontal, 14)
                                    .frame(maxWidth: .infinity)
                                    .background(langManager.currentLanguage == lang ? MSColors.stamp : Color.white)
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
                            Text(langManager.string(vi: "THÔNG TIN HỒ SƠ", en: "PROFILE DETAILS"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.string(vi: "Tên hiển thị", en: "Display Name"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            TextField(langManager.string(vi: "Nhập tên hiển thị", en: "Enter display name"), text: $displayName)
                                .font(.subheadline)
                                .foregroundColor(MSColors.ink)
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.string(vi: "Link Ảnh Đại Diện (Avatar URL)", en: "Avatar Image URL"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            TextField("https://...", text: $avatarUrl)
                                .font(.subheadline)
                                .foregroundColor(MSColors.ink)
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(10)
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))
                        }

                        VStack(alignment: .leading, spacing: 6) {
                            Text(langManager.string(vi: "Tiểu sử / Giới thiệu", en: "Bio Note"))
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            TextEditor(text: $bio)
                                .font(.subheadline)
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

                    // 3. Security & Password Change Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Image(systemName: "lock.shield")
                                .foregroundColor(MSColors.stamp)
                            Text(langManager.string(vi: "TÀI KHOẢN & MẬT KHẨU", en: "ACCOUNT & PASSWORD"))
                                .font(.caption2.bold())
                                .foregroundColor(MSColors.grey)
                        }

                        SecureField(langManager.string(vi: "Mật khẩu hiện tại", en: "Current Password"), text: $currentPassword)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

                        SecureField(langManager.string(vi: "Mật khẩu mới", en: "New Password"), text: $newPassword)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

                        SecureField(langManager.string(vi: "Xác nhận mật khẩu mới", en: "Confirm New Password"), text: $confirmPassword)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.25), lineWidth: 1))

                        if let toast = passwordToastMessage {
                            Text(toast)
                                .font(.caption.bold())
                                .foregroundColor(toast.contains("thành công") || toast.contains("Success") ? Color.green : Color.red)
                        }

                        Button(action: {
                            if currentPassword.isEmpty {
                                passwordToastMessage = langManager.string(vi: "⚠️ Vui lòng nhập mật khẩu hiện tại", en: "⚠️ Please enter current password")
                                return
                            }
                            if newPassword.count < 6 {
                                passwordToastMessage = langManager.string(vi: "⚠️ Mật khẩu mới phải có ít nhất 6 ký tự", en: "⚠️ New password must be at least 6 characters")
                                return
                            }
                            if newPassword != confirmPassword {
                                passwordToastMessage = langManager.string(vi: "⚠️ Mật khẩu xác nhận không khớp", en: "⚠️ Passwords do not match")
                                return
                            }
                            passwordToastMessage = langManager.string(vi: "✅ Đã đổi mật khẩu thành công!", en: "✅ Password updated successfully!")
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        }) {
                            HStack {
                                Image(systemName: "key.fill")
                                Text(langManager.string(vi: "Đổi Mật Khẩu", en: "Update Password"))
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

                    // 4. Save & Logout Action Row
                    VStack(spacing: 10) {
                        Button(action: {
                            let current = (repository.currentUser.value as? UserProfile)
                            let name = displayName.isEmpty ? (current?.displayName ?? "") : displayName
                            let note = bio.isEmpty ? (current?.bio ?? "") : bio
                            let avatar = avatarUrl.isEmpty ? current?.avatarUrl : avatarUrl

                            repository.updateProfile(displayName: name, bio: note, avatarUrl: avatar)

                            let defaults = UserDefaults.standard
                            defaults.set(name, forKey: "user_displayName")
                            defaults.set(note, forKey: "user_bio")
                            if let av = avatar {
                                defaults.set(av, forKey: "user_avatarUrl")
                            }

                            presentationMode.wrappedValue.dismiss()
                        }) {
                            Text(langManager.string(vi: "Lưu Cài Đặt Hồ Sơ", en: "Save Settings Changes"))
                                .font(.body.bold())
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(MSColors.stamp)
                                .cornerRadius(14)
                        }

                        Button(action: {
                            UserDefaults.standard.set(false, forKey: "isAuthenticated")
                            presentationMode.wrappedValue.dismiss()
                        }) {
                            HStack {
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                Text(langManager.string(vi: "Đăng Xuất Tài Khoản", en: "Logout Account"))
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.red.opacity(0.08))
                            .cornerRadius(14)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 30)
                }
            }
            .background(MSColors.paper.ignoresSafeArea())
            .navigationTitle(langManager.string(vi: "Cài Đặt & Tài Khoản", en: "Settings & Account"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(langManager.string(vi: "Đóng", en: "Close")) {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(MSColors.stamp)
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
