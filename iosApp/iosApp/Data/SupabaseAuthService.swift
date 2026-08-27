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

    private(set) var activeSession: AuthSessionData?

    private init() {
        self.activeSession = KeychainStore.loadSession()
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
            self?.handleAuthResponse(data: data, response: response, error: error, completion: completion)
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

        let token = activeSession?.accessToken ?? anonKey
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

    private func handleAuthResponse(
        data: Data?,
        response: URLResponse?,
        error: Error?,
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

        guard let accessToken = json["access_token"] as? String,
              let refreshToken = json["refresh_token"] as? String,
              let userDict = json["user"] as? [String: Any],
              let userId = userDict["id"] as? String else {
            completion(.failure(SupabaseAuthError.parseError))
            return
        }

        let userEmail = (userDict["email"] as? String) ?? ""
        let expiresIn = (json["expires_in"] as? Double) ?? 3600
        let expiresAt: Int64
        if let expAt = json["expires_at"] as? Double {
            expiresAt = Int64(expAt)
        } else {
            expiresAt = Int64(Date().timeIntervalSince1970 + expiresIn)
        }

        let session = AuthSessionData(
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt,
            userId: userId,
            email: userEmail
        )

        self.activeSession = session
        _ = KeychainStore.saveSession(session)
        completion(.success(session))
    }
}
