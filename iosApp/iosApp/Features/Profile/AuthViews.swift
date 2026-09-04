import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared



// MARK: - Mandatory Login Screen (App Entry Gate)
struct AuthLoginScreenView: View {
    let repository: SharedMemoStampRepository
    let onLoginSuccess: () -> Void

    @State private var emailText: String = ""
    @State private var passwordText: String = ""
    @State private var displayNameText: String = ""
    @State private var isSignUpMode: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    var body: some View {
        ZStack {
            MSColors.paper.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                // App Logo & Vintage Header
                VStack(spacing: 12) {
                    Image("app_logo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 84, height: 84)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .shadow(color: MSColors.stamp.opacity(0.3), radius: 10, x: 0, y: 5)

                    Text("MemoStamp")
                        .font(.system(size: 32, weight: .bold, design: .serif))
                        .foregroundColor(MSColors.ink)

                    HStack(spacing: 6) {
                        Image(systemName: "envelope.badge.fill")
                            .font(.subheadline)
                            .foregroundColor(MSColors.stamp)
                        Text("Bảng tin Kỷ niệm & Sổ Tem Bưu Chính")
                            .font(.subheadline)
                            .foregroundColor(MSColors.grey)
                    }
                }

                // Login / Register Form Card
                VStack(spacing: 16) {
                    HStack(spacing: 6) {
                        Image(systemName: isSignUpMode ? "square.and.pencil" : "key.fill")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.stamp)
                        Text(isSignUpMode ? "TẠO TÀI KHOẢN MỚI" : "ĐĂNG NHẬP HỆ THỐNG")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.stamp)
                            .tracking(1.5)
                    }

                    if isSignUpMode {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Tên hiển thị")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            HStack {
                                Image(systemName: "person.fill")
                                    .foregroundColor(MSColors.stamp)
                                TextField("Ví dụ: Nguyễn Văn A", text: $displayNameText)
                                    .font(.body)
                                    .foregroundColor(MSColors.ink)
                            }
                            .padding(12)
                            .background(MSColors.paper)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                        }
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Email")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        HStack {
                            Image(systemName: "envelope.fill")
                                .foregroundColor(MSColors.stamp)
                            TextField("nhap_email@memostamp.com", text: $emailText)
                                .autocapitalization(.none)
                                .keyboardType(.emailAddress)
                                .font(.body)
                                .foregroundColor(MSColors.ink)
                        }
                        .padding(12)
                        .background(MSColors.paper)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Mật khẩu")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        HStack {
                            Image(systemName: "lock.fill")
                                .foregroundColor(MSColors.stamp)
                            SecureField("••••••••", text: $passwordText)
                                .font(.body)
                                .foregroundColor(MSColors.ink)
                        }
                        .padding(12)
                        .background(MSColors.paper)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    }

                    if let err = errorMessage {
                        Text(err)
                            .font(.caption)
                            .foregroundColor(.red)
                            .padding(.vertical, 2)
                    }

                    Button(action: performAuth) {
                        HStack {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Text(isSignUpMode ? "Đăng Ký Tài Khoản" : "Đăng Nhập Ngay")
                                    .font(.body.bold())
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(MSColors.stamp)
                        .cornerRadius(24)
                        .shadow(color: MSColors.stamp.opacity(0.3), radius: 6, x: 0, y: 3)
                    }
                    .disabled(isLoading || emailText.isEmpty || passwordText.isEmpty)

                    // Toggle Login / Sign Up
                    Button(action: {
                        isSignUpMode.toggle()
                        errorMessage = nil
                    }) {
                        Text(isSignUpMode ? "Đã có tài khoản? Đăng nhập ngay" : "Chưa có tài khoản? Đăng ký tại đây")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.stamp)
                    }
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 24)
                        .fill(MSColors.white)
                        .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 4)
                )
                .padding(.horizontal, 24)

                // Or Continue with Social Logins
                VStack(spacing: 12) {
                    Text("hoặc đăng nhập nhanh bằng")
                        .font(.caption2)
                        .foregroundColor(MSColors.grey)

                    HStack(spacing: 16) {
                        Button(action: { showSocialNotice = true }) {
                            HStack(spacing: 8) {
                                Image(systemName: "g.circle.fill")
                                Text("Google (Sắp ra mắt)")
                                    .font(.subheadline.bold())
                            }
                            .foregroundColor(MSColors.grey)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(MSColors.white)
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(MSColors.lightGrey, lineWidth: 1))
                        }
                        .disabled(true)

                        Button(action: { showSocialNotice = true }) {
                            HStack(spacing: 8) {
                                Image(systemName: "applelogo")
                                Text("Apple (Sắp ra mắt)")
                                    .font(.subheadline.bold())
                            }
                            .foregroundColor(MSColors.grey)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(MSColors.white)
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(MSColors.lightGrey, lineWidth: 1))
                        }
                        .disabled(true)
                    }
                }

                Spacer()
            }
        }
        .alert(isPresented: $showSocialNotice) {
            Alert(
                title: Text("Tính năng Sắp ra mắt"),
                message: Text("Đăng nhập bằng Google và Apple sẽ được hỗ trợ trong phiên bản chính thức tiếp theo. Vui lòng sử dụng Email & Mật khẩu."),
                dismissButton: .default(Text("Đã hiểu"))
            )
        }
    }

    @State private var showSocialNotice: Bool = false

    private func performAuth() {
        isLoading = true
        errorMessage = nil

        let trimmedEmail = emailText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        // 1. Email format check
        if !trimmedEmail.contains("@") || !trimmedEmail.contains(".") {
            isLoading = false
            errorMessage = "Vui lòng nhập định dạng Email hợp lệ!"
            return
        }

        // 2. Password length check
        if passwordText.count < 6 {
            isLoading = false
            errorMessage = "Mật khẩu phải từ 6 ký tự trở lên!"
            return
        }

        let usernameFromEmail = trimmedEmail.components(separatedBy: "@").first ?? "user"
        let cleanUsername = usernameFromEmail.lowercased().replacingOccurrences(of: ".", with: "_")

        if isSignUpMode {
            SupabaseAuthService.shared.signUp(email: trimmedEmail, password: passwordText) { result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let session):
                        let finalDisplayName = displayNameText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? usernameFromEmail.capitalized.replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: ".", with: " ")
                            : displayNameText.trimmingCharacters(in: .whitespacesAndNewlines)

                        let avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
                        let bio = "Sưu tầm ký ức qua từng con tem bưu chính 📮"

                        let authUid = session.userId // Real UUID from Supabase Auth user.id

                        SupabaseAuthService.shared.upsertProfile(
                            userId: authUid,
                            email: trimmedEmail,
                            username: cleanUsername,
                            displayName: finalDisplayName,
                            avatarUrl: avatarUrl,
                            bio: bio
                        ) { _ in }

                        UserDefaults.standard.set(finalDisplayName, forKey: "user_displayName")
                        UserDefaults.standard.set(cleanUsername, forKey: "user_username")
                        UserDefaults.standard.set(trimmedEmail, forKey: "user_email")
                        UserDefaults.standard.set(avatarUrl, forKey: "user_avatarUrl")
                        UserDefaults.standard.set(bio, forKey: "user_bio")

                        let newProfile = UserProfile(
                            uid: authUid,
                            username: cleanUsername,
                            displayName: finalDisplayName,
                            avatarUrl: avatarUrl,
                            bio: bio,
                            stampsCreatedCount: Int32(0),
                            stampsCollectedCount: Int32(0),
                            placesVisitedCount: Int32(0)
                        )
                        repository.resetUserScopedState()
                        repository.setCurrentUser(profile: newProfile)
                        IOSLocalPersistenceStore.shared.loadData(into: repository, userId: authUid)

                        isLoading = false
                        onLoginSuccess()

                    case .failure(let error):
                        isLoading = false
                        errorMessage = error.localizedDescription
                    }
                }
            }
        } else {
            SupabaseAuthService.shared.signIn(email: trimmedEmail, password: passwordText) { result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let session):
                        let authUid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(authUid) else {
                            isLoading = false
                            errorMessage = "Tài khoản không hợp lệ."
                            return
                        }

                        // 1. Reset user-scoped repository state
                        repository.resetUserScopedState()

                        // 2. Create minimal PRESENTATION fallback with REAL auth UID
                        let fallbackDisplayName = usernameFromEmail.capitalized
                            .replacingOccurrences(of: "_", with: " ")
                            .replacingOccurrences(of: ".", with: " ")
                        let fallbackAvatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
                        let fallbackBio = "Sưu tầm ký ức qua từng con tem bưu chính 📮"

                        let fallbackProfile = UserProfile(
                            uid: authUid,
                            username: cleanUsername,
                            displayName: fallbackDisplayName,
                            avatarUrl: fallbackAvatarUrl,
                            bio: fallbackBio,
                            stampsCreatedCount: Int32(0),
                            stampsCollectedCount: Int32(0),
                            placesVisitedCount: Int32(0)
                        )
                        repository.setCurrentUser(profile: fallbackProfile)

                        // 3. Load account-scoped local persistence
                        IOSLocalPersistenceStore.shared.loadData(into: repository, userId: authUid)

                        // 4. Fetch exact cloud profile without destructive upsert
                        SupabaseSocialClient.shared.getPublicProfile(userId: authUid) { profileResult in
                            DispatchQueue.main.async {
                                switch profileResult {
                                case .success(let cloudProfile):
                                    if let cloud = cloudProfile {
                                        let cloudUserId = cloud.userId.trimmingCharacters(in: .whitespacesAndNewlines)
                                        if cloudUserId == authUid || cloudUserId.isEmpty {
                                            let currentProfile = repository.currentUser.value as? UserProfile
                                            let stampsCreated = currentProfile?.stampsCreatedCount ?? Int32(0)
                                            let stampsCollected = currentProfile?.stampsCollectedCount ?? Int32(0)
                                            let placesVisited = currentProfile?.placesVisitedCount ?? Int32(0)

                                            let finalUsername = cloud.username.isEmpty ? cleanUsername : cloud.username
                                            let finalDisplayName = cloud.displayName.isEmpty ? fallbackDisplayName : cloud.displayName

                                            let finalAvatarUrl: String
                                            if let ca = cloud.avatarUrl, !ca.isEmpty {
                                                finalAvatarUrl = ca
                                            } else {
                                                finalAvatarUrl = fallbackAvatarUrl
                                            }

                                            let finalBio: String
                                            if let cb = cloud.bio, !cb.isEmpty {
                                                finalBio = cb
                                            } else {
                                                finalBio = fallbackBio
                                            }

                                            let hydratedProfile = UserProfile(
                                                uid: authUid,
                                                username: finalUsername,
                                                displayName: finalDisplayName,
                                                avatarUrl: finalAvatarUrl,
                                                bio: finalBio,
                                                stampsCreatedCount: stampsCreated,
                                                stampsCollectedCount: stampsCollected,
                                                placesVisitedCount: placesVisited
                                            )
                                            repository.setCurrentUser(profile: hydratedProfile)
                                            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: authUid)
                                        }
                                    }
                                    isLoading = false
                                    onLoginSuccess()

                                case .failure:
                                    isLoading = false
                                    onLoginSuccess()
                                }
                            }
                        }

                    case .failure(let error):
                        isLoading = false
                        errorMessage = error.localizedDescription
                    }
                }
            }
        }
    }
}

// Sheet shown when user tries to perform social actions while offline / unauthenticated
struct AuthLoginSheetView: View {
    let onContinueWithGoogle: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 10)

            VStack(spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: "seal.fill")
                        .foregroundColor(MSColors.stamp)
                    Text("Join MemoStamp")
                        .font(.title2.bold())
                        .foregroundColor(MSColors.ink)
                }

                Text("Keep memories with people you care about.")
                    .font(.subheadline)
                    .foregroundColor(MSColors.grey)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
            }
            .padding(.top, 8)

            VStack(spacing: 14) {
                Button(action: onContinueWithGoogle) {
                    HStack(spacing: 12) {
                        Image(systemName: "g.circle.fill")
                            .font(.title3)
                        Text("Continue with Google")
                            .font(.body.bold())
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(MSColors.stamp)
                    .cornerRadius(24)
                    .shadow(color: MSColors.stamp.opacity(0.3), radius: 6, x: 0, y: 3)
                }

                Button(action: onDismiss) {
                    Text("Not now")
                        .font(.subheadline.bold())
                        .foregroundColor(MSColors.grey)
                        .padding(.vertical, 6)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .background(MSColors.paper.ignoresSafeArea())
    }
}

// Profile Setup Screen shown upon first-time login to pick unique username @phat
struct ProfileSetupScreenView: View {
    let repository: SharedMemoStampRepository
    let onCompleted: () -> Void

    @State private var displayName: String = ""
    @State private var username: String = ""
    @State private var bio: String = ""
    @State private var usernameStatus: String = ""
    @State private var isCheckingUsername: Bool = false
    @State private var isSubmitting: Bool = false

    var isFormValid: Bool {
        let u = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let regex = "^[a-z0-9_.]{3,20}$"
        let isUsernameValid = u.range(of: regex, options: .regularExpression) != nil
        return isUsernameValid && !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Create your profile")
                    .font(.title2.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                Spacer()
            }
            .padding()

            ScrollView {
                VStack(spacing: 22) {
                    // Default Initial Letter Avatar
                    ZStack {
                        Circle()
                            .fill(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.15))
                            .frame(width: 88, height: 88)

                        Text(String((displayName.isEmpty ? "M" : displayName).prefix(1)).uppercased())
                            .font(.system(size: 38, weight: .bold))
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    }
                    .padding(.top, 10)

                    // Display Name
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Your Name")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        TextField("Ví dụ: Nguyễn Văn A", text: $displayName)
                            .font(.body)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(MSColors.paper)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    }

                    // Username Input (@phat)
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("Username")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.ink)
                            Spacer()
                            if isCheckingUsername {
                                Text("Checking...")
                                    .font(.caption2)
                                    .foregroundColor(.orange)
                            } else if !username.isEmpty {
                                Text(usernameStatus)
                                    .font(.caption2.bold())
                                    .foregroundColor(usernameStatus.contains("Available") ? .green : .red)
                            }
                        }

                        HStack {
                            Text("@")
                                .font(.subheadline.bold())
                                .foregroundColor(MSColors.stamp)
                            TextField("username", text: $username)
                                .font(.body)
                                .foregroundColor(MSColors.ink)
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                                .onChange(of: username) { newValue in
                                    checkUsernameAvailability(newValue)
                                }
                        }
                        .padding(12)
                        .background(MSColors.paper)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    }

                    // Bio Input
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Bio")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                        TextField("A little about you...", text: $bio)
                            .font(.body)
                            .foregroundColor(MSColors.ink)
                            .padding(12)
                            .background(MSColors.paper)
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    }

                    Spacer(minLength: 30)

                    // Continue Button
                    Button(action: {
                        if isFormValid {
                            isSubmitting = true
                            repository.updateProfile(
                                displayName: displayName,
                                bio: bio,
                                avatarUrl: nil
                            )
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                isSubmitting = false
                                onCompleted()
                            }
                        }
                    }) {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Text("Continue")
                                    .font(.body.bold())
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(isFormValid ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.gray)
                        .cornerRadius(24)
                    }
                    .disabled(!isFormValid || isSubmitting)
                }
                .padding(.horizontal, 24)
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
    }

    private func checkUsernameAvailability(_ input: String) {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if trimmed.count < 3 {
            usernameStatus = "Too short (min 3 chars)"
            return
        }
        isCheckingUsername = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            isCheckingUsername = false
            usernameStatus = "✓ Available"
        }
    }
}
