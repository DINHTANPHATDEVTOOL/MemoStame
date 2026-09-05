import Foundation

enum SupabaseSocialError: LocalizedError {
    case invalidUrl
    case unauthorized
    case networkError(String)
    case serverError(Int, String)
    case parseError(String)
    case invalidData(String)

    var errorDescription: String? {
        switch self {
        case .invalidUrl:
            return "URL Supabase không hợp lệ."
        case .unauthorized:
            return "Phiên làm việc đã hết hạn hoặc chưa đăng nhập (Thiếu JWT Token)."
        case .networkError(let msg):
            return "Lỗi kết nối mạng: \(msg)"
        case .serverError(let code, let msg):
            return "Supabase phản hồi lỗi [\(code)]: \(msg)"
        case .parseError(let msg):
            return "Không thể xử lý dữ liệu: \(msg)"
        case .invalidData(let msg):
            return "Dữ liệu không hợp lệ: \(msg)"
        }
    }
}

struct SupabaseProfile: Codable, Identifiable {
    let id: String
    let userId: String
    let username: String
    let displayName: String
    let email: String?
    let avatarUrl: String?
    let coverUrl: String?
    let bio: String?
    let city: String?

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case username
        case displayName = "display_name"
        case email
        case avatarUrl = "avatar_url"
        case coverUrl = "cover_url"
        case bio
        case city
    }

    init(id: String, userId: String, username: String, displayName: String, email: String? = nil, avatarUrl: String? = nil, coverUrl: String? = nil, bio: String? = nil, city: String? = nil) {
        self.id = id
        self.userId = userId
        self.username = username
        self.displayName = displayName
        self.email = email
        self.avatarUrl = avatarUrl
        self.coverUrl = coverUrl
        self.bio = bio
        self.city = city
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawId = try? container.decode(String.self, forKey: .id)
        let rawUserId = try? container.decode(String.self, forKey: .userId)
        self.userId = rawUserId ?? rawId ?? ""
        self.id = rawId ?? rawUserId ?? ""
        self.username = (try? container.decode(String.self, forKey: .username)) ?? ""
        self.displayName = (try? container.decode(String.self, forKey: .displayName)) ?? self.username
        self.email = try? container.decode(String.self, forKey: .email)
        self.avatarUrl = try? container.decode(String.self, forKey: .avatarUrl)
        self.coverUrl = try? container.decode(String.self, forKey: .coverUrl)
        self.bio = try? container.decode(String.self, forKey: .bio)
        self.city = try? container.decode(String.self, forKey: .city)
    }
}

struct SupabaseFriendRequestRecord: Codable, Identifiable {
    let id: String
    let senderId: String
    let senderUsername: String
    let senderDisplayName: String
    let senderAvatar: String?
    let recipientId: String
    let recipientUsername: String
    let recipientDisplayName: String
    let recipientAvatar: String?
    let status: String
    let createdAt: Double?

    enum CodingKeys: String, CodingKey {
        case id
        case senderId = "sender_id"
        case senderUsername = "sender_username"
        case senderDisplayName = "sender_display_name"
        case senderAvatar = "sender_avatar"
        case recipientId = "recipient_id"
        case recipientUsername = "recipient_username"
        case recipientDisplayName = "recipient_display_name"
        case recipientAvatar = "recipient_avatar"
        case status
        case createdAt = "created_at"
    }
}

struct SupabaseFriendRecord: Codable, Identifiable {
    var id: String { "\(userId1)_\(userId2)" }
    let userId1: String
    let userId2: String

    enum CodingKeys: String, CodingKey {
        case userId1 = "user_id_1"
        case userId2 = "user_id_2"
    }

    init(userId1: String, userId2: String) {
        self.userId1 = userId1
        self.userId2 = userId2
    }
}

struct SupabaseDirectMessageRecord: Codable, Identifiable {
    let id: String
    let senderId: String
    let senderName: String
    let senderAvatar: String?
    let recipientId: String
    let recipientName: String
    let recipientAvatar: String?
    let text: String
    let stampId: String?
    let stampTitle: String?
    let stampImageUrl: String?
    let stampLocation: String?
    let createdAt: String?
    let isRead: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case senderId = "sender_id"
        case senderName = "sender_name"
        case senderAvatar = "sender_avatar"
        case recipientId = "recipient_id"
        case recipientName = "recipient_name"
        case recipientAvatar = "recipient_avatar"
        case text
        case stampId = "stamp_id"
        case stampTitle = "stamp_title"
        case stampImageUrl = "stamp_image_url"
        case stampLocation = "stamp_location"
        case createdAt = "created_at"
        case isRead = "is_read"
    }

    init(id: String, senderId: String, senderName: String, senderAvatar: String?, recipientId: String, recipientName: String, recipientAvatar: String?, text: String, stampId: String?, stampTitle: String?, stampImageUrl: String?, stampLocation: String?, createdAt: String?, isRead: Bool?) {
        self.id = id
        self.senderId = senderId
        self.senderName = senderName
        self.senderAvatar = senderAvatar
        self.recipientId = recipientId
        self.recipientName = recipientName
        self.recipientAvatar = recipientAvatar
        self.text = text
        self.stampId = stampId
        self.stampTitle = stampTitle
        self.stampImageUrl = isValidRemoteStampUrl(stampImageUrl) ? stampImageUrl?.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        self.stampLocation = stampLocation
        self.createdAt = createdAt
        self.isRead = isRead
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decode(String.self, forKey: .id)) ?? ""
        self.senderId = (try? container.decode(String.self, forKey: .senderId)) ?? ""
        self.senderName = (try? container.decode(String.self, forKey: .senderName)) ?? ""
        self.senderAvatar = try? container.decode(String.self, forKey: .senderAvatar)
        self.recipientId = (try? container.decode(String.self, forKey: .recipientId)) ?? ""
        self.recipientName = (try? container.decode(String.self, forKey: .recipientName)) ?? ""
        self.recipientAvatar = try? container.decode(String.self, forKey: .recipientAvatar)
        self.text = (try? container.decode(String.self, forKey: .text)) ?? ""
        self.stampId = try? container.decode(String.self, forKey: .stampId)
        self.stampTitle = try? container.decode(String.self, forKey: .stampTitle)
        let rawUrl = try? container.decode(String.self, forKey: .stampImageUrl)
        self.stampImageUrl = isValidRemoteStampUrl(rawUrl) ? rawUrl?.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        self.stampLocation = try? container.decode(String.self, forKey: .stampLocation)
        self.createdAt = try? container.decode(String.self, forKey: .createdAt)
        self.isRead = try? container.decode(Bool.self, forKey: .isRead)
    }
}

func isValidRemoteStampUrl(_ url: String?) -> Bool {
    guard let url = url?.trimmingCharacters(in: .whitespacesAndNewlines), !url.isEmpty else {
        return false
    }
    let lower = url.lowercased()
    guard lower.hasPrefix("http://") || lower.hasPrefix("https://") else {
        return false
    }
    if lower.hasPrefix("data:image/") ||
       lower.hasPrefix("file://") ||
       lower.hasPrefix("content://") ||
       lower.hasPrefix("/data/") ||
       lower.hasPrefix("/storage/") {
        return false
    }
    return true
}

struct SupabaseFeedPostRecord: Codable, Identifiable {
    let id: String
    let stampId: String?
    let stampUrl: String?
    let stampTitle: String?
    let shape: String?
    let authorId: String
    let authorName: String?
    let authorAvatar: String?
    let caption: String?
    let audienceType: String?
    let circleId: String?
    let circleName: String?
    let createdAt: String?
    let type: String?
    let location: String?

    enum CodingKeys: String, CodingKey {
        case id
        case stampId = "stamp_id"
        case stampUrl = "stamp_url"
        case stampTitle = "stamp_title"
        case shape
        case authorId = "author_id"
        case authorName = "author_name"
        case authorAvatar = "author_avatar"
        case caption
        case audienceType = "audience_type"
        case circleId = "circle_id"
        case circleName = "circle_name"
        case createdAt = "created_at"
        case type
        case location
    }

    init(id: String, stampId: String?, stampUrl: String?, stampTitle: String?, shape: String?, authorId: String, authorName: String?, authorAvatar: String?, caption: String?, audienceType: String?, circleId: String?, circleName: String?, createdAt: String?, type: String?, location: String?) {
        self.id = id
        self.stampId = stampId
        self.stampUrl = stampUrl
        self.stampTitle = stampTitle
        self.shape = shape
        self.authorId = authorId
        self.authorName = authorName
        self.authorAvatar = authorAvatar
        self.caption = caption
        self.audienceType = audienceType
        self.circleId = circleId
        self.circleName = circleName
        self.createdAt = createdAt
        self.type = type
        self.location = location
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decode(String.self, forKey: .id)) ?? ""
        self.stampId = try? container.decode(String.self, forKey: .stampId)
        let rawUrl = try? container.decode(String.self, forKey: .stampUrl)
        self.stampUrl = isValidRemoteStampUrl(rawUrl) ? rawUrl : nil
        self.stampTitle = try? container.decode(String.self, forKey: .stampTitle)
        self.shape = try? container.decode(String.self, forKey: .shape)
        self.authorId = (try? container.decode(String.self, forKey: .authorId)) ?? ""
        self.authorName = try? container.decode(String.self, forKey: .authorName)
        self.authorAvatar = try? container.decode(String.self, forKey: .authorAvatar)
        self.caption = try? container.decode(String.self, forKey: .caption)
        self.audienceType = try? container.decode(String.self, forKey: .audienceType)
        self.circleId = try? container.decode(String.self, forKey: .circleId)
        self.circleName = try? container.decode(String.self, forKey: .circleName)
        if let str = try? container.decode(String.self, forKey: .createdAt) {
            self.createdAt = str
        } else if let num = try? container.decode(Double.self, forKey: .createdAt) {
            self.createdAt = String(Int64(num))
        } else {
            self.createdAt = nil
        }
        self.type = try? container.decode(String.self, forKey: .type)
        self.location = try? container.decode(String.self, forKey: .location)
    }
}

struct SupabaseFeedReactionRecord: Codable, Identifiable {
    let id: String
    let postId: String
    let userId: String
    let userName: String?
    let emoji: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case postId = "post_id"
        case userId = "user_id"
        case userName = "user_name"
        case emoji
        case createdAt = "created_at"
    }

    init(id: String, postId: String, userId: String, userName: String?, emoji: String?, createdAt: String?) {
        self.id = id
        self.postId = postId
        self.userId = userId
        self.userName = userName
        self.emoji = emoji
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decode(String.self, forKey: .id)) ?? ""
        self.postId = (try? container.decode(String.self, forKey: .postId)) ?? ""
        self.userId = (try? container.decode(String.self, forKey: .userId)) ?? ""
        self.userName = try? container.decode(String.self, forKey: .userName)
        self.emoji = try? container.decode(String.self, forKey: .emoji)
        if let str = try? container.decode(String.self, forKey: .createdAt) {
            self.createdAt = str
        } else if let num = try? container.decode(Double.self, forKey: .createdAt) {
            self.createdAt = String(Int64(num))
        } else {
            self.createdAt = nil
        }
    }
}

struct SupabaseFeedCommentRecord: Codable, Identifiable {
    let id: String
    let postId: String
    let authorId: String
    let authorName: String?
    let authorAvatar: String?
    let content: String
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case postId = "post_id"
        case authorId = "author_id"
        case authorName = "author_name"
        case authorAvatar = "author_avatar"
        case content
        case createdAt = "created_at"
    }

    init(id: String, postId: String, authorId: String, authorName: String?, authorAvatar: String?, content: String, createdAt: String?) {
        self.id = id
        self.postId = postId
        self.authorId = authorId
        self.authorName = authorName
        self.authorAvatar = authorAvatar
        self.content = content
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decode(String.self, forKey: .id)) ?? ""
        self.postId = (try? container.decode(String.self, forKey: .postId)) ?? ""
        self.authorId = (try? container.decode(String.self, forKey: .authorId)) ?? ""
        self.authorName = try? container.decode(String.self, forKey: .authorName)
        self.authorAvatar = try? container.decode(String.self, forKey: .authorAvatar)
        self.content = (try? container.decode(String.self, forKey: .content)) ?? ""
        if let str = try? container.decode(String.self, forKey: .createdAt) {
            self.createdAt = str
        } else if let num = try? container.decode(Double.self, forKey: .createdAt) {
            self.createdAt = String(Int64(num))
        } else {
            self.createdAt = nil
        }
    }
}

struct SupabaseFeedReplyRecord: Codable, Identifiable {
    let id: String
    let postId: String
    let authorId: String
    let authorName: String?
    let authorAvatar: String?
    let replyStampId: String?
    let replyStampUrl: String
    let shape: String?
    let note: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id
        case postId = "post_id"
        case authorId = "author_id"
        case authorName = "author_name"
        case authorAvatar = "author_avatar"
        case replyStampId = "reply_stamp_id"
        case replyStampUrl = "reply_stamp_url"
        case shape
        case note
        case createdAt = "created_at"
    }

    init(id: String, postId: String, authorId: String, authorName: String?, authorAvatar: String?, replyStampId: String?, replyStampUrl: String, shape: String?, note: String?, createdAt: String?) {
        self.id = id
        self.postId = postId
        self.authorId = authorId
        self.authorName = authorName
        self.authorAvatar = authorAvatar
        self.replyStampId = replyStampId
        self.replyStampUrl = replyStampUrl
        self.shape = shape
        self.note = note
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decode(String.self, forKey: .id)) ?? ""
        self.postId = (try? container.decode(String.self, forKey: .postId)) ?? ""
        self.authorId = (try? container.decode(String.self, forKey: .authorId)) ?? ""
        self.authorName = try? container.decode(String.self, forKey: .authorName)
        self.authorAvatar = try? container.decode(String.self, forKey: .authorAvatar)
        self.replyStampId = try? container.decode(String.self, forKey: .replyStampId)
        let rawUrl = (try? container.decode(String.self, forKey: .replyStampUrl)) ?? ""
        self.replyStampUrl = isValidRemoteStampUrl(rawUrl) ? rawUrl : ""
        self.shape = try? container.decode(String.self, forKey: .shape)
        self.note = try? container.decode(String.self, forKey: .note)
        if let str = try? container.decode(String.self, forKey: .createdAt) {
            self.createdAt = str
        } else if let num = try? container.decode(Double.self, forKey: .createdAt) {
            self.createdAt = String(Int64(num))
        } else {
            self.createdAt = nil
        }
    }
}

class SupabaseSocialClient {
    static let shared = SupabaseSocialClient()

    let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    private init() {}

    private func executeHttp(
        endpoint: String,
        method: String = "GET",
        jsonBody: String? = nil,
        prefer: String? = nil,
        requireUserAuth: Bool = true,
        completion: @escaping (Result<Data, Error>) -> Void
    ) {
        let token = SupabaseAuthService.shared.activeSession?.accessToken
        if requireUserAuth && (token == nil || token?.isEmpty == true) {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }

        guard let url = URL(string: endpoint) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        if let userToken = token, !userToken.isEmpty {
            request.setValue("Bearer \(userToken)", forHTTPHeaderField: "Authorization")
        } else if !requireUserAuth {
            request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        }
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let p = prefer, !p.isEmpty {
            request.setValue(p, forHTTPHeaderField: "Prefer")
        }

        if let bodyStr = jsonBody, let data = bodyStr.data(using: .utf8) {
            request.httpBody = data
        }

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let err = error {
                completion(.failure(SupabaseSocialError.networkError(err.localizedDescription)))
                return
            }

            guard let httpRes = response as? HTTPURLResponse else {
                completion(.failure(SupabaseSocialError.parseError("Phản hồi HTTP không hợp lệ")))
                return
            }

            let responseData = data ?? Data()

            if (200...299).contains(httpRes.statusCode) {
                completion(.success(responseData))
            } else {
                let errText = String(data: responseData, encoding: .utf8) ?? "Unknown HTTP error"
                completion(.failure(SupabaseSocialError.serverError(httpRes.statusCode, errText)))
            }
        }.resume()
    }

    // MARK: - Helper UUID Validator
    func isValidUuid(_ string: String) -> Bool {
        return UUID(uuidString: string) != nil
    }

    // MARK: - Profiles & Public Discovery
    func getPublicProfile(userId: String, completion: @escaping (Result<SupabaseProfile?, Error>) -> Void) {
        let cleanId = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty,
              let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        let endpoint = "\(supabaseUrl)/rest/v1/public_profiles?user_id=eq.\(encodedId)&select=*&limit=1"

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let profiles = try decoder.decode([SupabaseProfile].self, from: data)
                    completion(.success(profiles.first))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func searchPublicProfiles(query: String, completion: @escaping (Result<[SupabaseProfile], Error>) -> Void) {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "@", with: "")
        guard let encodedClean = clean.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let encodedWild = "*\(clean)*".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        let endpoint: String
        if clean.isEmpty {
            endpoint = "\(supabaseUrl)/rest/v1/public_profiles?select=*&order=created_at.desc&limit=50"
        } else {
            endpoint = "\(supabaseUrl)/rest/v1/public_profiles?or=(username.ilike.\(encodedWild),display_name.ilike.\(encodedWild),username.eq.\(encodedClean))&select=*"
        }

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: false) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let profiles = try decoder.decode([SupabaseProfile].self, from: data)
                    completion(.success(profiles))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    // MARK: - Friend Requests
    func getFriendRequests(userId: String, completion: @escaping (Result<[SupabaseFriendRequestRecord], Error>) -> Void) {
        guard let encodedId = userId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        let endpoint = "\(supabaseUrl)/rest/v1/friend_requests?or=(recipient_id.eq.\(encodedId),sender_id.eq.\(encodedId))&select=*&order=created_at.desc"

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let reqs = try decoder.decode([SupabaseFriendRequestRecord].self, from: data)
                    completion(.success(reqs))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func sendFriendRequest(request: SupabaseFriendRequestRecord, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/friend_requests?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(request)
            let jsonString = String(data: data, encoding: .utf8)

            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "resolution=merge-duplicates", requireUserAuth: true) { result in
                switch result {
                case .success:
                    completion(.success(true))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    // MARK: - Friend RPCs
    func acceptFriendRequestRpc(requestId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/rpc/accept_friend_request"
        let body: [String: String] = ["p_request_id": requestId.trimmingCharacters(in: .whitespacesAndNewlines)]
        let jsonBody = try? String(data: JSONEncoder().encode(body), encoding: .utf8)

        executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonBody, requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func declineFriendRequestRpc(requestId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/rpc/decline_friend_request"
        let body: [String: String] = ["p_request_id": requestId.trimmingCharacters(in: .whitespacesAndNewlines)]
        let jsonBody = try? String(data: JSONEncoder().encode(body), encoding: .utf8)

        executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonBody, requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func cancelFriendRequestRpc(requestId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/rpc/cancel_friend_request"
        let body: [String: String] = ["p_request_id": requestId.trimmingCharacters(in: .whitespacesAndNewlines)]
        let jsonBody = try? String(data: JSONEncoder().encode(body), encoding: .utf8)

        executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonBody, requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func unfriendUserRpc(friendId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/rpc/unfriend_user"
        let body: [String: String] = ["p_friend_id": friendId.trimmingCharacters(in: .whitespacesAndNewlines)]
        let jsonBody = try? String(data: JSONEncoder().encode(body), encoding: .utf8)

        executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonBody, requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func getFriends(userId: String, completion: @escaping (Result<[SupabaseFriendRecord], Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/friends?select=*"

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let records = try decoder.decode([SupabaseFriendRecord].self, from: data)
                    completion(.success(records))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    // MARK: - Direct Messages
    func sendDirectMessage(message: SupabaseDirectMessageRecord, completion: @escaping (Result<SupabaseDirectMessageRecord, Error>) -> Void) {
        guard isValidUuid(message.id) else {
            completion(.failure(SupabaseSocialError.invalidData("ID tin nhắn phải là một UUID hợp lệ: \(message.id)")))
            return
        }

        let endpoint = "\(supabaseUrl)/rest/v1/direct_messages?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(message)
            let jsonString = String(data: data, encoding: .utf8)

            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "return=representation", requireUserAuth: true) { result in
                switch result {
                case .success(let resData):
                    let resString = String(data: resData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                    if resString.isEmpty {
                        completion(.failure(SupabaseSocialError.invalidData("Server phản hồi payload rỗng.")))
                        return
                    }

                    do {
                        let decoder = JSONDecoder()
                        let serverRecord: SupabaseDirectMessageRecord
                        if resString.hasPrefix("[") {
                            let list = try decoder.decode([SupabaseDirectMessageRecord].self, from: resData)
                            guard let first = list.first else {
                                completion(.failure(SupabaseSocialError.invalidData("Danh sách từ server rỗng.")))
                                return
                            }
                            serverRecord = first
                        } else {
                            serverRecord = try decoder.decode(SupabaseDirectMessageRecord.self, from: resData)
                        }

                        guard !serverRecord.id.isEmpty, self.isValidUuid(serverRecord.id) else {
                            completion(.failure(SupabaseSocialError.invalidData("ID phản hồi từ server không phải UUID hợp lệ: \(serverRecord.id)")))
                            return
                        }

                        guard serverRecord.senderId == message.senderId else {
                            completion(.failure(SupabaseSocialError.invalidData("sender_id không khớp: \(serverRecord.senderId) vs \(message.senderId)")))
                            return
                        }

                        guard serverRecord.recipientId == message.recipientId else {
                            completion(.failure(SupabaseSocialError.invalidData("recipient_id không khớp: \(serverRecord.recipientId) vs \(message.recipientId)")))
                            return
                        }

                        completion(.success(serverRecord))
                    } catch {
                        completion(.failure(SupabaseSocialError.parseError("Không thể parse DirectMessage từ server: \(error.localizedDescription)")))
                    }
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    func getMessagesForUser(userId: String, completion: @escaping (Result<[SupabaseDirectMessageRecord], Error>) -> Void) {
        guard let session = SupabaseAuthService.shared.activeSession else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }
        let sessionUid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let reqUid = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(sessionUid), sessionUid == reqUid else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }
        guard let encoded = reqUid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        let endpoint = "\(supabaseUrl)/rest/v1/direct_messages?or=(sender_id.eq.\(encoded),recipient_id.eq.\(encoded))&select=*&order=created_at.asc,id.asc"

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let messages = try decoder.decode([SupabaseDirectMessageRecord].self, from: data)
                    completion(.success(messages))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func getConversation(userId1: String, userId2: String, completion: @escaping (Result<[SupabaseDirectMessageRecord], Error>) -> Void) {
        guard let u1 = userId1.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let u2 = userId2.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        let endpoint = "\(supabaseUrl)/rest/v1/direct_messages?or=(and(sender_id.eq.\(u1),recipient_id.eq.\(u2)),and(sender_id.eq.\(u2),recipient_id.eq.\(u1)))&select=*&order=created_at.asc,id.asc"

        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let messages = try decoder.decode([SupabaseDirectMessageRecord].self, from: data)
                    completion(.success(messages))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func markMessagesAsReadRpc(senderId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/rpc/mark_direct_messages_read"
        let body: [String: String] = ["p_sender_id": senderId.trimmingCharacters(in: .whitespacesAndNewlines)]
        let jsonBody = try? String(data: JSONEncoder().encode(body), encoding: .utf8)

        executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonBody, requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    // MARK: - Feed Cloud Authority (Task #41)
    func getFeedPosts(completion: @escaping (Result<[SupabaseFeedPostRecord], Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_posts?select=*&order=created_at.desc&limit=100"
        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let posts = try decoder.decode([SupabaseFeedPostRecord].self, from: data)
                    completion(.success(posts))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError("Không thể đọc feed_posts: \(error.localizedDescription)")))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func getFeedReactions(completion: @escaping (Result<[SupabaseFeedReactionRecord], Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_reactions?select=*&limit=500"
        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let reactions = try decoder.decode([SupabaseFeedReactionRecord].self, from: data)
                    completion(.success(reactions))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError("Không thể đọc feed_reactions: \(error.localizedDescription)")))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func getFeedComments(completion: @escaping (Result<[SupabaseFeedCommentRecord], Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_comments?select=*&order=created_at.asc&limit=500"
        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let comments = try decoder.decode([SupabaseFeedCommentRecord].self, from: data)
                    completion(.success(comments))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError("Không thể đọc feed_comments: \(error.localizedDescription)")))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func getFeedReplies(completion: @escaping (Result<[SupabaseFeedReplyRecord], Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_replies?select=*&order=created_at.asc&limit=500"
        executeHttp(endpoint: endpoint, method: "GET", requireUserAuth: true) { result in
            switch result {
            case .success(let data):
                do {
                    let decoder = JSONDecoder()
                    let replies = try decoder.decode([SupabaseFeedReplyRecord].self, from: data)
                    completion(.success(replies))
                } catch {
                    completion(.failure(SupabaseSocialError.parseError("Không thể đọc feed_replies: \(error.localizedDescription)")))
                }
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func createFeedPost(post: SupabaseFeedPostRecord, completion: @escaping (Result<SupabaseFeedPostRecord, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_posts?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(post)
            let jsonString = String(data: data, encoding: .utf8)
            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "return=representation", requireUserAuth: true) { result in
                switch result {
                case .success(let resData):
                    do {
                        let decoder = JSONDecoder()
                        let resString = String(data: resData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        if resString.hasPrefix("[") {
                            let list = try decoder.decode([SupabaseFeedPostRecord].self, from: resData)
                            if let first = list.first {
                                completion(.success(first))
                            } else {
                                completion(.failure(SupabaseSocialError.invalidData("Server phản hồi rỗng")))
                            }
                        } else {
                            let item = try decoder.decode(SupabaseFeedPostRecord.self, from: resData)
                            completion(.success(item))
                        }
                    } catch {
                        completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                    }
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    func addFeedReaction(reaction: SupabaseFeedReactionRecord, completion: @escaping (Result<Bool, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_reactions?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(reaction)
            let jsonString = String(data: data, encoding: .utf8)
            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "resolution=merge-duplicates", requireUserAuth: true) { result in
                switch result {
                case .success:
                    completion(.success(true))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    func deleteFeedReaction(postId: String, userId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        guard let encPost = postId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let encUser = userId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }
        let endpoint = "\(supabaseUrl)/rest/v1/feed_reactions?post_id=eq.\(encPost)&user_id=eq.\(encUser)"
        executeHttp(endpoint: endpoint, method: "DELETE", requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func addFeedComment(comment: SupabaseFeedCommentRecord, completion: @escaping (Result<SupabaseFeedCommentRecord, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_comments?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(comment)
            let jsonString = String(data: data, encoding: .utf8)
            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "return=representation", requireUserAuth: true) { result in
                switch result {
                case .success(let resData):
                    do {
                        let decoder = JSONDecoder()
                        let resString = String(data: resData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        if resString.hasPrefix("[") {
                            let list = try decoder.decode([SupabaseFeedCommentRecord].self, from: resData)
                            if let first = list.first {
                                completion(.success(first))
                            } else {
                                completion(.failure(SupabaseSocialError.invalidData("Server phản hồi rỗng")))
                            }
                        } else {
                            let item = try decoder.decode(SupabaseFeedCommentRecord.self, from: resData)
                            completion(.success(item))
                        }
                    } catch {
                        completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                    }
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    func deleteFeedComment(commentId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        guard let encId = commentId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }
        let endpoint = "\(supabaseUrl)/rest/v1/feed_comments?id=eq.\(encId)"
        executeHttp(endpoint: endpoint, method: "DELETE", requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }

    func addFeedReply(reply: SupabaseFeedReplyRecord, completion: @escaping (Result<SupabaseFeedReplyRecord, Error>) -> Void) {
        let endpoint = "\(supabaseUrl)/rest/v1/feed_replies?on_conflict=id"
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(reply)
            let jsonString = String(data: data, encoding: .utf8)
            executeHttp(endpoint: endpoint, method: "POST", jsonBody: jsonString, prefer: "return=representation", requireUserAuth: true) { result in
                switch result {
                case .success(let resData):
                    do {
                        let decoder = JSONDecoder()
                        let resString = String(data: resData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        if resString.hasPrefix("[") {
                            let list = try decoder.decode([SupabaseFeedReplyRecord].self, from: resData)
                            if let first = list.first {
                                completion(.success(first))
                            } else {
                                completion(.failure(SupabaseSocialError.invalidData("Server phản hồi rỗng")))
                            }
                        } else {
                            let item = try decoder.decode(SupabaseFeedReplyRecord.self, from: resData)
                            completion(.success(item))
                        }
                    } catch {
                        completion(.failure(SupabaseSocialError.parseError(error.localizedDescription)))
                    }
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        } catch {
            completion(.failure(SupabaseSocialError.invalidData(error.localizedDescription)))
        }
    }

    func deleteFeedReply(replyId: String, completion: @escaping (Result<Bool, Error>) -> Void) {
        guard let encId = replyId.trimmingCharacters(in: .whitespacesAndNewlines).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }
        let endpoint = "\(supabaseUrl)/rest/v1/feed_replies?id=eq.\(encId)"
        executeHttp(endpoint: endpoint, method: "DELETE", requireUserAuth: true) { result in
            switch result {
            case .success:
                completion(.success(true))
            case .failure(let err):
                completion(.failure(err))
            }
        }
    }
}
