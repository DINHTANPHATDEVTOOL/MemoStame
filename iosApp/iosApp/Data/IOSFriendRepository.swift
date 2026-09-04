import Foundation
import Combine
import shared

struct PersistedIOSFriendPayload: Codable {
    let userId: String
    let friends: [PersistedFriendData]
    let incomingRequests: [PersistedFriendRequestData]
    let outgoingRequests: [PersistedFriendRequestData]
}

class IOSFriendRepository: ObservableObject {
    static let shared = IOSFriendRepository()

    @Published var friends: [FriendItem] = []
    @Published var incomingRequests: [FriendRequestItem] = []
    @Published var outgoingRequests: [FriendRequestItem] = []
    @Published var searchedProfiles: [UserProfile] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    private(set) var activeUserId: String = ""
    private let fileManager = FileManager.default

    private init() {}

    func sanitizeUserId(_ userId: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_-"))
        let clean = userId.lowercased().components(separatedBy: allowed.inverted).joined(separator: "_")
        var hash: UInt32 = 2166136261
        for byte in userId.utf8 {
            hash = (hash ^ UInt32(byte)).multipliedReportingOverflow(by: 16777619).partialValue
        }
        let hexHash = String(format: "%08x", hash)
        let base = clean.isEmpty ? "user" : clean
        return "\(base)_\(hexHash)"
    }

    private func storageUrl(userId: String) -> URL {
        let safeUid = sanitizeUserId(userId)
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("memostamp_friends_v2_\(safeUid).json")
    }

    func onUserChanged(newUserId: String) {
        let cleanUid = newUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanUid == activeUserId { return }

        // Clear in-memory state for user switch isolation
        self.friends = []
        self.incomingRequests = []
        self.outgoingRequests = []
        self.searchedProfiles = []
        self.errorMessage = nil
        self.activeUserId = cleanUid

        guard !cleanUid.isEmpty else { return }

        // Load offline cache
        loadLocalCache(userId: cleanUid)

        // Load cloud state
        loadCloudData()
    }

    // MARK: - Local Cache Management
    private func loadLocalCache(userId: String) {
        let url = storageUrl(userId: userId)
        guard fileManager.fileExists(atPath: url.path),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(PersistedIOSFriendPayload.self, from: data) else { return }

        guard payload.userId == userId else {
            print("Friend cache identity mismatch: file uid '\(payload.userId)' != active '\(userId)'")
            return
        }

        self.friends = payload.friends.map { f in
            FriendItem(id: f.id, displayName: f.displayName, username: f.username, avatarUrl: f.avatarUrl, isOnline: f.isOnline, tradeCount: f.tradeCount)
        }

        self.incomingRequests = payload.incomingRequests.map { r in
            FriendRequestItem(id: r.id, senderName: r.senderName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar, status: r.status, createdAt: r.createdAt, senderId: r.senderId ?? "", recipientId: r.recipientId ?? "", recipientUsername: r.recipientUsername ?? "")
        }

        self.outgoingRequests = payload.outgoingRequests.map { r in
            FriendRequestItem(id: r.id, senderName: r.senderName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar, status: r.status, createdAt: r.createdAt, senderId: r.senderId ?? "", recipientId: r.recipientId ?? "", recipientUsername: r.recipientUsername ?? "")
        }
    }

    private func saveLocalCache() {
        guard !activeUserId.isEmpty else { return }
        let url = storageUrl(userId: activeUserId)

        let friendDatas = friends.map { f in
            PersistedFriendData(id: f.id, displayName: f.displayName, username: f.username, avatarUrl: f.avatarUrl, isOnline: f.isOnline, tradeCount: f.tradeCount)
        }

        let incomingDatas = incomingRequests.map { r in
            PersistedFriendRequestData(id: r.id, senderName: r.senderName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar, status: r.status, createdAt: r.createdAt, senderId: r.senderId, recipientId: r.recipientId, recipientUsername: r.recipientUsername)
        }

        let outgoingDatas = outgoingRequests.map { r in
            PersistedFriendRequestData(id: r.id, senderName: r.senderName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar, status: r.status, createdAt: r.createdAt, senderId: r.senderId, recipientId: r.recipientId, recipientUsername: r.recipientUsername)
        }

        let payload = PersistedIOSFriendPayload(userId: activeUserId, friends: friendDatas, incomingRequests: incomingDatas, outgoingRequests: outgoingDatas)
        if let encoded = try? JSONEncoder().encode(payload) {
            try? encoded.write(to: url, options: .atomic)
        }
    }

    // MARK: - Cloud Synchronization
    func loadCloudData() {
        guard let activeUserId = SupabaseAuthService.shared.currentUserId,
              !activeUserId.isEmpty else {
            self.isLoading = false
            return
        }

        self.isLoading = true
        let group = DispatchGroup()

        // 1. Fetch friend requests
        group.enter()
        SupabaseSocialClient.shared.getFriendRequests(userId: activeUserId) { [weak self] result in
            DispatchQueue.main.async {
                defer { group.leave() }
                switch result {
                case .success(let requests):
                    self?.incomingRequests = requests.filter { $0.recipientId == activeUserId }.map { r in
                        FriendRequestItem(id: r.id, senderName: r.senderDisplayName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar ?? "https://i.pravatar.cc/150?u=\(r.senderId)", status: r.status, createdAt: Int64(r.createdAt ?? Double(Date().timeIntervalSince1970 * 1000)), senderId: r.senderId, recipientId: r.recipientId, recipientUsername: r.recipientUsername)
                    }
                    self?.outgoingRequests = requests.filter { $0.senderId == activeUserId }.map { r in
                        FriendRequestItem(id: r.id, senderName: r.senderDisplayName, senderUsername: r.senderUsername, senderAvatar: r.senderAvatar ?? "https://i.pravatar.cc/150?u=\(r.senderId)", status: r.status, createdAt: Int64(r.createdAt ?? Double(Date().timeIntervalSince1970 * 1000)), senderId: r.senderId, recipientId: r.recipientId, recipientUsername: r.recipientUsername)
                    }
                case .failure(let err):
                    // Network failure retains cached state
                    print("Friend requests cloud sync error (retaining offline cache): \(err.localizedDescription)")
                }
            }
        }

        // 2. Fetch accepted friends list
        group.enter()
        SupabaseSocialClient.shared.getFriends(userId: activeUserId) { [weak self] result in
            switch result {
            case .success(let records):
                let friendIds = Array(Set(records.compactMap { record -> String? in
                    if record.userId1 == activeUserId {
                        return record.userId2
                    }
                    if record.userId2 == activeUserId {
                        return record.userId1
                    }
                    return nil
                }))

                if friendIds.isEmpty {
                    DispatchQueue.main.async {
                        self?.friends = []
                        group.leave()
                    }
                } else {
                    SupabaseSocialClient.shared.searchPublicProfiles(query: "") { profileRes in
                        DispatchQueue.main.async {
                            defer { group.leave() }
                            if case .success(let profiles) = profileRes {
                                let profileMap = Dictionary(profiles.map { ($0.userId, $0) }, uniquingKeysWith: { first, _ in first })
                                let updatedFriends = friendIds.map { fid in
                                    let prof = profileMap[fid]
                                    return FriendItem(
                                        id: fid,
                                        displayName: prof?.displayName ?? "Bạn bè",
                                        username: prof?.username ?? fid,
                                        avatarUrl: prof?.avatarUrl ?? "https://i.pravatar.cc/150?u=\(fid)",
                                        isOnline: true,
                                        tradeCount: Int32(0)
                                    )
                                }
                                self?.friends = updatedFriends
                            }
                        }
                    }
                }
            case .failure(let err):
                // Network failure retains cached state
                print("Friends list cloud sync error (retaining offline cache): \(err.localizedDescription)")
                DispatchQueue.main.async {
                    group.leave()
                }
            }
        }

        group.notify(queue: .main) { [weak self] in
            self?.isLoading = false
            self?.saveLocalCache()
        }
    }

    // MARK: - Actions & RPCs
    func searchProfiles(query: String) {
        guard !query.isEmpty else {
            self.searchedProfiles = []
            return
        }

        SupabaseSocialClient.shared.searchPublicProfiles(query: query) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let profiles):
                    self?.searchedProfiles = profiles.map { p in
                        UserProfile(uid: p.userId, username: p.username, displayName: p.displayName, avatarUrl: p.avatarUrl, bio: p.bio ?? "", stampsCreatedCount: Int32(0), stampsCollectedCount: Int32(0), placesVisitedCount: Int32(0))
                    }
                case .failure(let err):
                    self?.errorMessage = err.localizedDescription
                }
            }
        }
    }

    func sendFriendRequest(targetUserId: String, targetUsername: String, targetDisplayName: String, targetAvatar: String, completion: @escaping (Result<String, Error>) -> Void) {
        guard let currentUid = SupabaseAuthService.shared.currentUserId, !currentUid.isEmpty else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }

        let record = SupabaseFriendRequestRecord(
            id: UUID().uuidString.lowercased(),
            senderId: currentUid,
            senderUsername: (SupabaseAuthService.shared.activeSession?.email.components(separatedBy: "@").first) ?? "user",
            senderDisplayName: "Me",
            senderAvatar: "https://i.pravatar.cc/150?u=\(currentUid)",
            recipientId: targetUserId,
            recipientUsername: targetUsername,
            recipientDisplayName: targetDisplayName,
            recipientAvatar: targetAvatar,
            status: "PENDING",
            createdAt: Double(Date().timeIntervalSince1970 * 1000)
        )

        SupabaseSocialClient.shared.sendFriendRequest(request: record) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadCloudData()
                    completion(.success("Đã gửi lời mời kết bạn tới @\(targetUsername)!"))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    func acceptRequest(requestId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        SupabaseSocialClient.shared.acceptFriendRequestRpc(requestId: requestId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadCloudData()
                    completion(.success(()))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    func declineRequest(requestId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        SupabaseSocialClient.shared.declineFriendRequestRpc(requestId: requestId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadCloudData()
                    completion(.success(()))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    func cancelRequest(requestId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        SupabaseSocialClient.shared.cancelFriendRequestRpc(requestId: requestId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadCloudData()
                    completion(.success(()))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    func sendFriendRequestByUsernameOrId(input: String, completion: @escaping (Result<String, Error>) -> Void) {
        let clean = input.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "@", with: "")
        guard !clean.isEmpty else {
            completion(.failure(SupabaseSocialError.invalidData("Vui lòng nhập tên người dùng hoặc ID.")))
            return
        }

        if SupabaseSocialClient.shared.isValidUuid(clean) {
            sendFriendRequest(targetUserId: clean, targetUsername: clean, targetDisplayName: clean, targetAvatar: "", completion: completion)
        } else {
            SupabaseSocialClient.shared.searchPublicProfiles(query: clean) { [weak self] result in
                guard let self = self else { return }
                switch result {
                case .success(let profiles):
                    if let target = profiles.first(where: { $0.username.lowercased() == clean.lowercased() }) ?? profiles.first {
                        self.sendFriendRequest(targetUserId: target.userId, targetUsername: target.username, targetDisplayName: target.displayName, targetAvatar: target.avatarUrl ?? "", completion: completion)
                    } else {
                        completion(.failure(SupabaseSocialError.invalidData("Không tìm thấy người dùng '@\(clean)' trên hệ thống.")))
                    }
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    func unfriendUser(friendId: String, completion: @escaping (Result<Void, Error>) -> Void) {
        SupabaseSocialClient.shared.unfriendUserRpc(friendId: friendId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadCloudData()
                    completion(.success(()))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }
}
