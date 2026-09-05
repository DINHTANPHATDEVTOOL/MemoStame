import Foundation

enum SupabaseAuthError: LocalizedError {
    case invalidUrl
    case networkError(String)
    case serverError(Int, String)
    case parseError
    case sessionExpired

    var errorDescription: String? {
        switch self {
        case .invalidUrl:
            return "URL Supabase không hợp lệ."
        case .networkError(let msg):
            return "Lỗi kết nối mạng: \(msg)"
        case .serverError(let code, let msg):
            return "Supabase phản hồi lỗi [\(code)]: \(msg)"
        case .parseError:
            return "Không thể xử lý phản hồi từ server."
        case .sessionExpired:
            return "Phiên đăng nhập đã hết hạn."
        }
    }
}

class SupabaseAuthService {
    static let shared = SupabaseAuthService()

    let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    private(set) var activeSession: AuthSessionData? {
        didSet {
            let uid = activeSession?.userId ?? ""
            let token = activeSession?.accessToken
            IOSFriendRepository.shared.onUserChanged(newUserId: uid)
            IOSChatRepository.shared.onSessionChanged(userId: uid, accessToken: token)
            IOSFeedRepository.shared.syncUserSession()
        }
    }

    private init() {
        self.activeSession = KeychainStore.loadSession()
        let uid = activeSession?.userId ?? ""
        let token = activeSession?.accessToken
        IOSFriendRepository.shared.onUserChanged(newUserId: uid)
        IOSChatRepository.shared.onSessionChanged(userId: uid, accessToken: token)
    }

    var currentUserId: String? {
        return activeSession?.userId
    }

    func loadOrRefreshSession(completion: @escaping (AuthSessionData?) -> Void) {
        guard let session = KeychainStore.loadSession() else {
            activeSession = nil
            completion(nil)
            return
        }

        if !session.isExpired {
            activeSession = session
            completion(session)
            return
        }

        refreshSession(refreshToken: session.refreshToken) { [weak self] result in
            switch result {
            case .success(let refreshedSession):
                self?.activeSession = refreshedSession
                _ = KeychainStore.saveSession(refreshedSession)
                completion(refreshedSession)
            case .failure:
                self?.activeSession = nil
                KeychainStore.deleteSession()
                completion(nil)
            }
        }
    }

    func signUp(email: String, password: String, completion: @escaping (Result<AuthSessionData, Error>) -> Void) {
        guard let url = URL(string: "\(supabaseUrl)/auth/v1/signup") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "email": email,
            "password": password
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            self?.handleAuthResponse(data: data, response: response, error: error, isSignUp: true, completion: completion)
        }.resume()
    }

    func signIn(email: String, password: String, completion: @escaping (Result<AuthSessionData, Error>) -> Void) {
        guard let url = URL(string: "\(supabaseUrl)/auth/v1/token?grant_type=password") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "email": email,
            "password": password
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            self?.handleAuthResponse(data: data, response: response, error: error, completion: completion)
        }.resume()
    }

    func refreshSession(refreshToken: String, completion: @escaping (Result<AuthSessionData, Error>) -> Void) {
        guard let url = URL(string: "\(supabaseUrl)/auth/v1/token?grant_type=refresh_token") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "refresh_token": refreshToken
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            self?.handleAuthResponse(data: data, response: response, error: error, completion: completion)
        }.resume()
    }

    func signOut(completion: @escaping (Bool) -> Void) {
        let token = activeSession?.accessToken
        activeSession = nil
        KeychainStore.deleteSession()
        SupabaseRealtimeClient.shared.disconnect(clearState: true)
        IOSFriendRepository.shared.onUserChanged(newUserId: "")
        IOSChatRepository.shared.onSessionChanged(userId: "", accessToken: nil)
        IOSFeedRepository.shared.clear()

        guard let accessToken = token, let url = URL(string: "\(supabaseUrl)/auth/v1/logout") else {
            completion(true)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion(true)
        }.resume()
    }

    func upsertProfile(
        userId: String,
        email: String,
        username: String,
        displayName: String,
        avatarUrl: String = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=300",
        bio: String = "Sưu tầm ký ức qua từng con tem bưu chính 📮",
        completion: @escaping (Bool) -> Void
    ) {
        guard let url = URL(string: "\(supabaseUrl)/rest/v1/profiles") else {
            completion(false)
            return
        }

        guard let token = activeSession?.accessToken,
              !token.isEmpty else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")

        let body: [String: Any] = [
            "id": userId,
            "user_id": userId,
            "username": username,
            "display_name": displayName,
            "email": email,
            "avatar_url": avatarUrl,
            "bio": bio,
            "city": "Sài Gòn"
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) {
                completion(true)
            } else {
                completion(false)
            }
        }.resume()
    }

    static func isSafeRemoteAvatarUrl(_ urlString: String?) -> Bool {
        guard let trimmed = urlString?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return true
        }
        let lower = trimmed.lowercased()
        if lower.hasPrefix("file:") ||
           lower.hasPrefix("/") ||
           lower.hasPrefix("content:") ||
           lower.hasPrefix("data:") ||
           lower.hasPrefix("blob:") {
            return false
        }
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              (scheme == "https" || scheme == "http"),
              let host = url.host,
              !host.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        return true
    }

    func updateAuthenticatedProfile(
        userId: String,
        displayName: String,
        bio: String,
        avatarUrl: String?,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let session = activeSession else {
            completion(.failure(SupabaseAuthError.sessionExpired))
            return
        }

        let sessionUid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(sessionUid) else {
            completion(.failure(SupabaseAuthError.serverError(403, "Phiên đăng nhập không có danh tính hợp lệ.")))
            return
        }

        let requestedUid = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard sessionUid == requestedUid else {
            completion(.failure(SupabaseAuthError.serverError(403, "Mã người dùng không khớp với phiên xác thực.")))
            return
        }

        let accessToken = session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !accessToken.isEmpty else {
            completion(.failure(SupabaseAuthError.sessionExpired))
            return
        }

        guard let encodedUid = sessionUid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(supabaseUrl)/rest/v1/profiles?user_id=eq.\(encodedUid)") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var body: [String: Any] = [
            "display_name": displayName,
            "bio": bio
        ]

        if let avatar = avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !avatar.isEmpty {
            guard Self.isSafeRemoteAvatarUrl(avatar) else {
                completion(.failure(SupabaseAuthError.serverError(400, "URL ảnh đại diện không an toàn hoặc không hợp lệ.")))
                return
            }
            body["avatar_url"] = avatar
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let err = error {
                completion(.failure(SupabaseAuthError.networkError(err.localizedDescription)))
                return
            }

            guard let httpRes = response as? HTTPURLResponse else {
                completion(.failure(SupabaseAuthError.parseError))
                return
            }

            guard (200...299).contains(httpRes.statusCode) else {
                var msg = "Profile update failed"
                if let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    msg = (json["message"] as? String) ?? (json["msg"] as? String) ?? (json["error_description"] as? String) ?? (json["error"] as? String) ?? msg
                }
                completion(.failure(SupabaseAuthError.serverError(httpRes.statusCode, msg)))
                return
            }

            guard let data = data else {
                completion(.failure(SupabaseAuthError.parseError))
                return
            }

            var returnedUid: String? = nil
            if let jsonArray = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]],
               let first = jsonArray.first {
                returnedUid = (first["user_id"] as? String) ?? (first["id"] as? String)
            } else if let jsonDict = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                returnedUid = (jsonDict["user_id"] as? String) ?? (jsonDict["id"] as? String)
            }

            guard let matchedUid = returnedUid?.trimmingCharacters(in: .whitespacesAndNewlines),
                  matchedUid == sessionUid else {
                completion(.failure(SupabaseAuthError.serverError(httpRes.statusCode, "Hồ sơ cập nhật không trả về người dùng hợp lệ.")))
                return
            }

            completion(.success(()))
        }.resume()
    }

    func updateUserPassword(accessToken: String, newPassword: String, completion: @escaping (Result<Void, Error>) -> Void) {
        guard let url = URL(string: "\(supabaseUrl)/auth/v1/user") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "password": newPassword
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let err = error {
                completion(.failure(SupabaseAuthError.networkError(err.localizedDescription)))
                return
            }

            guard let httpRes = response as? HTTPURLResponse else {
                completion(.failure(SupabaseAuthError.parseError))
                return
            }

            if (200...299).contains(httpRes.statusCode) {
                completion(.success(()))
            } else {
                var msg = "Password update failed"
                if let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    msg = (json["msg"] as? String) ?? (json["error_description"] as? String) ?? (json["message"] as? String) ?? (json["error"] as? String) ?? msg
                }
                completion(.failure(SupabaseAuthError.serverError(httpRes.statusCode, msg)))
            }
        }.resume()
    }

    private func isValidAuthUid(_ uid: String) -> Bool {
        let trimmed = uid.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return false }
        if trimmed == "user_me" { return false }
        if trimmed == "guest" { return false }
        if trimmed.hasPrefix("guest") { return false }
        return true
    }

    private func reauthenticateForSensitiveAction(
        email: String,
        password: String,
        completion: @escaping (Result<AuthSessionData, Error>) -> Void
    ) {
        guard let url = URL(string: "\(supabaseUrl)/auth/v1/token?grant_type=password") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "email": email,
            "password": password
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            let result = self.parseAuthSessionData(data: data, response: response, error: error)
            completion(result)
        }.resume()
    }

    private func parseAuthSessionData(
        data: Data?,
        response: URLResponse?,
        error: Error?
    ) -> Result<AuthSessionData, Error> {
        if let err = error {
            return .failure(SupabaseAuthError.networkError(err.localizedDescription))
        }

        guard let httpRes = response as? HTTPURLResponse else {
            return .failure(SupabaseAuthError.parseError)
        }

        guard let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .failure(SupabaseAuthError.parseError)
        }

        if !(200...299).contains(httpRes.statusCode) {
            let msg = (json["msg"] as? String) ?? (json["error_description"] as? String) ?? (json["message"] as? String) ?? "Auth error"
            return .failure(SupabaseAuthError.serverError(httpRes.statusCode, msg))
        }

        let accessToken = json["access_token"] as? String
        let refreshToken = json["refresh_token"] as? String
        let userDict = json["user"] as? [String: Any]
        let userId = userDict?["id"] as? String

        guard let validToken = accessToken, !validToken.isEmpty,
              let validRefresh = refreshToken, !validRefresh.isEmpty,
              let validUid = userId, !validUid.isEmpty else {
            return .failure(SupabaseAuthError.parseError)
        }

        let userEmail = (userDict?["email"] as? String) ?? ""
        let expiresIn = (json["expires_in"] as? Double) ?? 3600
        let expiresAt: Int64
        if let expAt = json["expires_at"] as? Double {
            expiresAt = Int64(expAt)
        } else {
            expiresAt = Int64(Date().timeIntervalSince1970 + expiresIn)
        }

        let session = AuthSessionData(
            accessToken: validToken,
            refreshToken: validRefresh,
            expiresAt: expiresAt,
            userId: validUid,
            email: userEmail
        )
        return .success(session)
    }

    func changePassword(
        currentPassword: String,
        newPassword: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let currentSession = activeSession,
              isValidAuthUid(currentSession.userId) else {
            completion(.failure(SupabaseAuthError.sessionExpired))
            return
        }

        let expectedUid = currentSession.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let emailToUse = currentSession.email.trimmingCharacters(in: .whitespacesAndNewlines)

        if emailToUse.isEmpty {
            completion(.failure(SupabaseAuthError.serverError(400, "Chưa xác định được email tài khoản.")))
            return
        }

        reauthenticateForSensitiveAction(email: emailToUse, password: currentPassword) { [weak self] reauthResult in
            guard let self = self else { return }
            switch reauthResult {
            case .failure(let err):
                completion(.failure(err))
            case .success(let reauthSession):
                let reauthUid = reauthSession.userId.trimmingCharacters(in: .whitespacesAndNewlines)
                guard self.isValidAuthUid(reauthUid), reauthUid == expectedUid else {
                    completion(.failure(SupabaseAuthError.serverError(403, "Mã người dùng xác thực không trùng khớp.")))
                    return
                }

                let accessToken = reauthSession.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !accessToken.isEmpty else {
                    completion(.failure(SupabaseAuthError.sessionExpired))
                    return
                }

                self.updateUserPassword(accessToken: accessToken, newPassword: newPassword) { updateResult in
                    switch updateResult {
                    case .failure(let updateErr):
                        completion(.failure(updateErr))
                    case .success:
                        self.activeSession = reauthSession
                        _ = KeychainStore.saveSession(reauthSession)
                        completion(.success(()))
                    }
                }
            }
        }
    }

    func deleteCurrentAccount(
        currentPassword: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let currentSession = activeSession,
              isValidAuthUid(currentSession.userId) else {
            completion(.failure(SupabaseAuthError.sessionExpired))
            return
        }

        let expectedUid = currentSession.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let emailToUse = currentSession.email.trimmingCharacters(in: .whitespacesAndNewlines)

        if emailToUse.isEmpty {
            completion(.failure(SupabaseAuthError.serverError(400, "Chưa xác định được email tài khoản.")))
            return
        }

        reauthenticateForSensitiveAction(email: emailToUse, password: currentPassword) { [weak self] reauthResult in
            guard let self = self else { return }
            switch reauthResult {
            case .failure(let err):
                completion(.failure(err))
            case .success(let reauthSession):
                let reauthUid = reauthSession.userId.trimmingCharacters(in: .whitespacesAndNewlines)
                guard self.isValidAuthUid(reauthUid), reauthUid == expectedUid else {
                    completion(.failure(SupabaseAuthError.serverError(403, "Mã người dùng xác thực không trùng khớp.")))
                    return
                }

                let accessToken = reauthSession.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !accessToken.isEmpty else {
                    completion(.failure(SupabaseAuthError.sessionExpired))
                    return
                }

                self.callDeleteAccountEndpoint(accessToken: accessToken) { deleteResult in
                    switch deleteResult {
                    case .failure(let deleteErr):
                        completion(.failure(deleteErr))
                    case .success:
                        // ONLY after verified server success: purge account-local data
                        self.purgeAccountLocalData(userId: expectedUid)
                        self.clearLocalSessionAndReset()
                        completion(.success(()))
                    }
                }
            }
        }
    }

    private func callDeleteAccountEndpoint(
        accessToken: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let url = URL(string: "\(supabaseUrl)/functions/v1/delete-account") else {
            completion(.failure(SupabaseAuthError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = "{}".data(using: .utf8)
        request.timeoutInterval = 20

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let err = error {
                completion(.failure(SupabaseAuthError.networkError(err.localizedDescription)))
                return
            }

            guard let httpRes = response as? HTTPURLResponse else {
                completion(.failure(SupabaseAuthError.parseError))
                return
            }

            if (200...299).contains(httpRes.statusCode) {
                completion(.success(()))
                return
            }

            var msg = "Xóa tài khoản thất bại"
            if let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                msg = (json["message"] as? String) ?? (json["msg"] as? String) ?? (json["error"] as? String) ?? msg
            }
            completion(.failure(SupabaseAuthError.serverError(httpRes.statusCode, msg)))
        }.resume()
    }

    private func purgeAccountLocalData(userId: String) {
        // 1. Purge account-scoped persistent data file
        IOSLocalPersistenceStore.shared.deleteData(userId: userId)

        // 2. Purge account chat & friend local files
        IOSChatRepository.shared.deleteAccountLocalData(userId: userId)
        IOSFriendRepository.shared.deleteAccountLocalData(userId: userId)
        IOSFeedRepository.shared.clear()
    }

    private func clearLocalSessionAndReset() {
        self.activeSession = nil
        KeychainStore.deleteSession()
        SupabaseRealtimeClient.shared.disconnect(clearState: true)
    }

    private func handleAuthResponse(
        data: Data?,
        response: URLResponse?,
        error: Error?,
        isSignUp: Bool = false,
        completion: @escaping (Result<AuthSessionData, Error>) -> Void
    ) {
        if let err = error {
            completion(.failure(SupabaseAuthError.networkError(err.localizedDescription)))
            return
        }

        guard let httpRes = response as? HTTPURLResponse else {
            completion(.failure(SupabaseAuthError.parseError))
            return
        }

        guard let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            completion(.failure(SupabaseAuthError.parseError))
            return
        }

        if !(200...299).contains(httpRes.statusCode) {
            let msg = (json["msg"] as? String) ?? (json["error_description"] as? String) ?? (json["message"] as? String) ?? "Auth error"
            completion(.failure(SupabaseAuthError.serverError(httpRes.statusCode, msg)))
            return
        }

        let accessToken = json["access_token"] as? String
        let refreshToken = json["refresh_token"] as? String
        let userDict = json["user"] as? [String: Any]
        let userId = userDict?["id"] as? String

        if isSignUp && (accessToken == nil || accessToken?.isEmpty == true) {
            // Confirm email is enabled on backend, handle explicitly without setting active session
            completion(.failure(SupabaseAuthError.serverError(200, "Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản trước khi đăng nhập.")))
            return
        }

        guard let validToken = accessToken, !validToken.isEmpty,
              let validRefresh = refreshToken, !validRefresh.isEmpty,
              let validUid = userId, !validUid.isEmpty else {
            completion(.failure(SupabaseAuthError.parseError))
            return
        }

        let userEmail = (userDict?["email"] as? String) ?? ""
        let expiresIn = (json["expires_in"] as? Double) ?? 3600
        let expiresAt: Int64
        if let expAt = json["expires_at"] as? Double {
            expiresAt = Int64(expAt)
        } else {
            expiresAt = Int64(Date().timeIntervalSince1970 + expiresIn)
        }

        let session = AuthSessionData(
            accessToken: validToken,
            refreshToken: validRefresh,
            expiresAt: expiresAt,
            userId: validUid,
            email: userEmail
        )

        self.activeSession = session
        _ = KeychainStore.saveSession(session)
        completion(.success(session))
    }
}
