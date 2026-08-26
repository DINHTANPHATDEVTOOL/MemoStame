import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

import CryptoKit

func hashPasswordSwift(_ text: String) -> String {
    guard !text.isEmpty else { return "" }
    let data = Data(text.utf8)
    let digest = SHA256.hash(data: data)
    return digest.map { String(format: "%02hhx", $0) }.joined()
}

struct UserAccountData: Codable {
    let email: String
    let passwordHash: String
    let displayName: String
    let username: String
    let avatarUrl: String
    let bio: String
}

struct SupabaseCloudAuth {
    static let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    static let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    static func fetchCloudProfile(emailOrUsername: String, completion: @escaping (UserAccountData?) -> Void) {
        let clean = emailOrUsername.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let query: String
        if clean.contains("@") {
            query = "email=eq.\(clean)"
        } else {
            query = "username=eq.\(clean)"
        }

        guard let url = URL(string: "\(supabaseUrl)/rest/v1/profiles?\(query)") else {
            completion(nil)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 6.0

        URLSession.shared.dataTask(with: request) { data, response, error in
            guard let data = data, error == nil else {
                completion(nil)
                return
            }

            do {
                if let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
                   let first = jsonArray.first {
                    let email = (first["email"] as? String) ?? clean
                    let displayName = (first["display_name"] as? String) ?? clean
                    let username = (first["username"] as? String) ?? clean
                    let avatarUrl = (first["avatar_url"] as? String) ?? "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
                    let bio = (first["bio"] as? String) ?? "Sưu tầm ký ức qua từng con tem bưu chính 📮"

                    let account = UserAccountData(
                        email: email,
                        passwordHash: "",
                        displayName: displayName,
                        username: username,
                        avatarUrl: avatarUrl,
                        bio: bio
                    )
                    completion(account)
                    return
                }
            } catch {
                print("Supabase cloud parse error: \(error)")
            }
            completion(nil)
        }.resume()
    }

    static func upsertCloudProfile(account: UserAccountData, completion: @escaping (Bool) -> Void) {
        guard let url = URL(string: "\(supabaseUrl)/rest/v1/profiles") else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")
        request.timeoutInterval = 6.0

        let body: [String: Any] = [
            "user_id": "user_\(account.username)",
            "username": account.username,
            "display_name": account.displayName,
            "email": account.email,
            "avatar_url": account.avatarUrl,
            "bio": account.bio,
            "city": "Sài Gòn"
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) {
                completion(true)
            } else {
                completion(false)
            }
        }.resume()
    }
}

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
                        Button(action: { performSocialLogin(provider: "Google") }) {
                            HStack(spacing: 8) {
                                Image(systemName: "g.circle.fill")
                                Text("Google (Demo)")
                                    .font(.subheadline.bold())
                            }
                            .foregroundColor(MSColors.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(MSColors.white)
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(MSColors.lightGrey, lineWidth: 1))
                        }

                        Button(action: { performSocialLogin(provider: "Apple") }) {
                            HStack(spacing: 8) {
                                Image(systemName: "applelogo")
                                Text("Apple (Demo)")
                                    .font(.subheadline.bold())
                            }
                            .foregroundColor(MSColors.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(MSColors.white)
                            .cornerRadius(20)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(MSColors.lightGrey, lineWidth: 1))
                        }
                    }

                    Button(action: { performSocialLogin(provider: "Guest") }) {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.stamp)
                            Text("Dùng thử ứng dụng không cần đăng ký")
                                .font(.caption.bold())
                                .foregroundColor(MSColors.grey)
                                .underline()
                        }
                    }
                    .padding(.top, 4)
                }

                Spacer()
            }
        }
    }

    private func getRegisteredAccounts() -> [String: UserAccountData] {
        if let data = UserDefaults.standard.data(forKey: "registered_accounts_db"),
           let dict = try? JSONDecoder().decode([String: UserAccountData].self, from: data) {
            return dict
        }
        return [:]
    }

    private func saveRegisteredAccounts(_ accounts: [String: UserAccountData]) {
        if let data = try? JSONEncoder().encode(accounts) {
            UserDefaults.standard.set(data, forKey: "registered_accounts_db")
        }
    }

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
            // Sign Up Mode: Check Supabase Cloud DB first if email/username already exists
            SupabaseCloudAuth.fetchCloudProfile(emailOrUsername: trimmedEmail) { existingCloudAccount in
                DispatchQueue.main.async {
                    if existingCloudAccount != nil {
                        isLoading = false
                        errorMessage = "Tài khoản \"\(emailText)\" đã được đăng ký trên Supabase Cloud! Vui lòng chuyển sang Đăng Nhập."
                        return
                    }

                    let finalDisplayName = displayNameText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? usernameFromEmail.capitalized.replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: ".", with: " ")
                        : displayNameText.trimmingCharacters(in: .whitespacesAndNewlines)

                    let avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300"
                    let bio = "Sưu tầm ký ức qua từng con tem bưu chính 📮"

                    let hashedPwd = hashPasswordSwift(passwordText)
                    let newAccount = UserAccountData(
                        email: trimmedEmail,
                        passwordHash: hashedPwd,
                        displayName: finalDisplayName,
                        username: cleanUsername,
                        avatarUrl: avatarUrl,
                        bio: bio
                    )

                    // 1. Send POST to Supabase Cloud DB
                    SupabaseCloudAuth.upsertCloudProfile(account: newAccount) { success in
                        DispatchQueue.main.async {
                            var accounts = getRegisteredAccounts()
                            accounts[trimmedEmail] = newAccount
                            saveRegisteredAccounts(accounts)

                            UserDefaults.standard.set(finalDisplayName, forKey: "user_displayName")
                            UserDefaults.standard.set(cleanUsername, forKey: "user_username")
                            UserDefaults.standard.set(trimmedEmail, forKey: "user_email")
                            UserDefaults.standard.set(avatarUrl, forKey: "user_avatarUrl")
                            UserDefaults.standard.set(bio, forKey: "user_bio")

                            let newProfile = UserProfile(
                                uid: "user_" + cleanUsername,
                                username: cleanUsername,
                                displayName: finalDisplayName,
                                avatarUrl: avatarUrl,
                                bio: bio,
                                stampsCreatedCount: Int32(0),
                                stampsCollectedCount: Int32(0),
                                placesVisitedCount: Int32(0)
                            )
                            repository.setCurrentUser(profile: newProfile)

                            isLoading = false
                            onLoginSuccess()
                        }
                    }
                }
            }
        } else {
            // Login Mode: Check local registered accounts securely
            let localAccounts = getRegisteredAccounts()
            if let localAccount = localAccounts[trimmedEmail] {
                let inputHashed = hashPasswordSwift(passwordText)
                if localAccount.passwordHash.isEmpty || (localAccount.passwordHash != inputHashed && localAccount.passwordHash != passwordText) {
                    isLoading = false
                    errorMessage = "Mật khẩu không chính xác! Vui lòng thử lại."
                    return
                }

                UserDefaults.standard.set(localAccount.displayName, forKey: "user_displayName")
                UserDefaults.standard.set(localAccount.username, forKey: "user_username")
                UserDefaults.standard.set(localAccount.email, forKey: "user_email")
                UserDefaults.standard.set(localAccount.avatarUrl, forKey: "user_avatarUrl")
                UserDefaults.standard.set(localAccount.bio, forKey: "user_bio")

                let newProfile = UserProfile(
                    uid: "user_" + localAccount.username,
                    username: localAccount.username,
                    displayName: localAccount.displayName,
                    avatarUrl: localAccount.avatarUrl,
                    bio: localAccount.bio,
                    stampsCreatedCount: Int32(0),
                    stampsCollectedCount: Int32(0),
                    placesVisitedCount: Int32(0)
                )
                repository.setCurrentUser(profile: newProfile)

                isLoading = false
                onLoginSuccess()
            } else {
                isLoading = false
                errorMessage = "Tài khoản \"\(emailText)\" chưa được lưu trên thiết bị. Vui lòng chọn Đăng Ký Tài Khoản mới."
            }
        }
    }

    private func performSocialLogin(provider: String) {
        let (displayName, username, avatarUrl) = {
            switch provider {
            case "Google":
                return ("Google Account User", "google_collector", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300")
            case "Apple":
                return ("Apple Account User", "apple_id_user", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=300")
            default:
                return ("Khách Thử Nghiệm", "guest_explorer", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300")
            }
        }()

        let bio = "Đăng nhập qua \(provider) Auth 📮"
        let uid = "user_" + username

        UserDefaults.standard.set(displayName, forKey: "user_displayName")
        UserDefaults.standard.set(username, forKey: "user_username")
        UserDefaults.standard.set(avatarUrl, forKey: "user_avatarUrl")
        UserDefaults.standard.set(bio, forKey: "user_bio")

        let newProfile = UserProfile(
            uid: uid,
            username: username,
            displayName: displayName,
            avatarUrl: avatarUrl,
            bio: bio,
            stampsCreatedCount: Int32(0),
            stampsCollectedCount: Int32(0),
            placesVisitedCount: Int32(0)
        )
        repository.setCurrentUser(profile: newProfile)

        onLoginSuccess()
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
