import Foundation
import Combine

struct PersistedIOSChatPayload: Codable {
    let userId: String
    let recipientId: String
    let messages: [PersistedChatMessageData]
}

struct PersistedChatMessageData: Codable {
    let id: String
    let senderId: String
    let senderName: String
    let senderAvatar: String
    let recipientId: String
    let text: String
    let createdAt: Int64
    let isMe: Bool
    let isRead: Bool
    let stampId: String?
    let stampTitle: String?
    let stampImageUrl: String?
    let stampLocation: String?
}

class IOSChatRepository: ObservableObject {
    static let shared = IOSChatRepository()

    @Published var conversationMessages: [String: [ChatMessage]] = [:] // recipientId -> [ChatMessage]
    @Published var activeRecipientId: String? = nil
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    private(set) var activeUserId: String = ""
    private var messageDedupeSet: Set<String> = []
    private let fileManager = FileManager.default

    private init() {}

    private func sanitizeUserId(_ userId: String) -> String {
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

    private func storageUrl(userId: String, recipientId: String) -> URL {
        let safeUid = sanitizeUserId(userId)
        let safeRid = sanitizeUserId(recipientId)
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("memostamp_chat_v2_\(safeUid)_\(safeRid).json")
    }

    func onUserChanged(newUserId: String) {
        let cleanUid = newUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanUid == activeUserId { return }

        // Disconnect Realtime for user switch isolation
        SupabaseRealtimeClient.shared.disconnect(clearState: true)

        // Clear in-memory state
        self.conversationMessages = [:]
        self.messageDedupeSet = []
        self.activeRecipientId = nil
        self.errorMessage = nil
        self.activeUserId = cleanUid

        guard !cleanUid.isEmpty else { return }

        // Setup Realtime WebSocket for new user
        if let token = SupabaseAuthService.shared.activeSession?.accessToken, !token.isEmpty {
            setupRealtimeSubscriptions(token: token, uid: cleanUid)
        }
    }

    func setupRealtimeSubscriptions(token: String, uid: String) {
        SupabaseRealtimeClient.shared.onMessageReceived = { [weak self] record in
            DispatchQueue.main.async {
                self?.handleRealtimeMessageInsert(record)
            }
        }

        SupabaseRealtimeClient.shared.onMessageUpdated = { [weak self] record in
            DispatchQueue.main.async {
                self?.handleRealtimeMessageUpdate(record)
            }
        }

        SupabaseRealtimeClient.shared.connectAndSubscribe(token: token, uid: uid)
    }

    // MARK: - Local Cache Management
    private func loadLocalCache(recipientId: String) {
        guard !activeUserId.isEmpty else { return }
        let url = storageUrl(userId: activeUserId, recipientId: recipientId)
        guard fileManager.fileExists(atPath: url.path),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(PersistedIOSChatPayload.self, from: data) else { return }

        guard payload.userId == activeUserId, payload.recipientId == recipientId else { return }

        let domainMsgs = payload.messages.map { m in
            var stamp: StampItem? = nil
            if let sid = m.stampId, !sid.isEmpty {
                stamp = StampItem(id: sid, title: m.stampTitle ?? "", stampImagePath: m.stampImageUrl ?? "", locationName: m.stampLocation ?? "", dateCreated: "")
            }
            return ChatMessage(
                id: m.id,
                senderId: m.senderId,
                senderName: m.senderName,
                senderAvatar: m.senderAvatar,
                recipientId: m.recipientId,
                text: m.text,
                createdAt: m.createdAt,
                isMe: m.senderId == activeUserId,
                isRead: m.isRead,
                stamp: stamp
            )
        }

        for msg in domainMsgs {
            messageDedupeSet.insert(msg.id)
        }

        self.conversationMessages[recipientId] = domainMsgs
    }

    private func saveLocalCache(recipientId: String) {
        guard !activeUserId.isEmpty else { return }
        guard let msgs = conversationMessages[recipientId] else { return }
        let url = storageUrl(userId: activeUserId, recipientId: recipientId)

        let persistedMsgs = msgs.map { m in
            PersistedChatMessageData(
                id: m.id,
                senderId: m.senderId,
                senderName: m.senderName,
                senderAvatar: m.senderAvatar,
                recipientId: m.recipientId,
                text: m.text,
                createdAt: m.createdAt,
                isMe: m.senderId == activeUserId,
                isRead: m.isRead,
                stampId: m.stamp?.id,
                stampTitle: m.stamp?.title,
                stampImageUrl: m.stamp?.stampImagePath,
                stampLocation: m.stamp?.locationName
            )
        }

        let payload = PersistedIOSChatPayload(userId: activeUserId, recipientId: recipientId, messages: persistedMsgs)
        if let encoded = try? JSONEncoder().encode(payload) {
            try? encoded.write(to: url, options: .atomic)
        }
    }

    // MARK: - Cloud Conversation Loading
    func loadConversation(otherUserId: String, completion: ((Result<[ChatMessage], Error>) -> Void)? = nil) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        // Load cached messages first for immediate display
        loadLocalCache(recipientId: otherUserId)

        isLoading = true

        SupabaseSocialClient.shared.getConversation(userId1: activeUserId, userId2: otherUserId) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.isLoading = false
                switch result {
                case .success(let records):
                    let domainMsgs = records.compactMap { r -> ChatMessage? in
                        guard SupabaseSocialClient.shared.isValidUuid(r.id) else { return nil }
                        let isMe = r.senderId == self.activeUserId

                        var timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
                        if let dateStr = r.createdAt {
                            let formatter = ISO8601DateFormatter()
                            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
                            if let date = formatter.date(from: dateStr) {
                                timestamp = Int64(date.timeIntervalSince1970 * 1000)
                            } else {
                                formatter.formatOptions = [.withInternetDateTime]
                                if let date = formatter.date(from: dateStr) {
                                    timestamp = Int64(date.timeIntervalSince1970 * 1000)
                                } else if let millis = Int64(dateStr) {
                                    timestamp = millis
                                }
                            }
                        }

                        var stamp: StampItem? = nil
                        if let sid = r.stampId, !sid.isEmpty {
                            stamp = StampItem(id: sid, title: r.stampTitle ?? "", stampImagePath: r.stampImageUrl ?? "", locationName: r.stampLocation ?? "", dateCreated: "")
                        }

                        return ChatMessage(
                            id: r.id,
                            senderId: r.senderId,
                            senderName: r.senderName.isEmpty ? (isMe ? "Me" : "Bạn bè") : r.senderName,
                            senderAvatar: r.senderAvatar ?? "https://i.pravatar.cc/150?u=\(r.senderId)",
                            recipientId: r.recipientId,
                            text: r.text,
                            createdAt: timestamp,
                            isMe: isMe,
                            isRead: r.isRead ?? false,
                            stamp: stamp
                        )
                    }.sorted { (m1, m2) -> Bool in
                        if m1.createdAt == m2.createdAt {
                            return m1.id < m2.id
                        }
                        return m1.createdAt < m2.createdAt
                    }

                    // Update deduplication set
                    for msg in domainMsgs {
                        self.messageDedupeSet.insert(msg.id)
                    }

                    self.conversationMessages[otherUserId] = domainMsgs
                    self.saveLocalCache(recipientId: otherUserId)
                    completion?(.success(domainMsgs))

                case .failure(let err):
                    // Network failure retains cached state
                    print("Load conversation cloud failure (retaining cached state): \(err.localizedDescription)")
                    completion?(.failure(err))
                }
            }
        }
    }

    // MARK: - Sending Direct Messages
    func sendMessageCloud(recipientId: String, text: String, stamp: StampItem? = nil, completion: @escaping (Result<ChatMessage, Error>) -> Void) {
        guard !activeUserId.isEmpty else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }

        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedText.isEmpty && stamp == nil {
            completion(.failure(SupabaseSocialError.invalidData("Tin nhắn không được để trống.")))
            return
        }

        // Generate strict UUID string for new message ID
        let validUuid = UUID().uuidString.lowercased()

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let isoCreatedAt = formatter.string(from: Date())

        let record = SupabaseDirectMessageRecord(
            id: validUuid,
            senderId: activeUserId,
            senderName: "Me",
            senderAvatar: "https://i.pravatar.cc/150?u=\(activeUserId)",
            recipientId: recipientId,
            recipientName: "Recipient",
            recipientAvatar: "https://i.pravatar.cc/150?u=\(recipientId)",
            text: trimmedText,
            stampId: stamp?.id,
            stampTitle: stamp?.title,
            stampImageUrl: stamp?.stampImagePath,
            stampLocation: stamp?.locationName,
            createdAt: isoCreatedAt,
            isRead: false
        )

        SupabaseSocialClient.shared.sendDirectMessage(message: record) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                switch result {
                case .success(let serverRecord):
                    let isMe = serverRecord.senderId == self.activeUserId
                    let timestamp = Int64(Date().timeIntervalSince1970 * 1000)

                    var msgStamp: StampItem? = nil
                    if let sid = serverRecord.stampId, !sid.isEmpty {
                        msgStamp = StampItem(id: sid, title: serverRecord.stampTitle ?? "", stampImagePath: serverRecord.stampImageUrl ?? "", locationName: serverRecord.stampLocation ?? "", dateCreated: "")
                    }

                    let serverMsg = ChatMessage(
                        id: serverRecord.id,
                        senderId: serverRecord.senderId,
                        senderName: isMe ? "Me" : serverRecord.senderName,
                        senderAvatar: serverRecord.senderAvatar ?? "https://i.pravatar.cc/150?u=\(serverRecord.senderId)",
                        recipientId: serverRecord.recipientId,
                        text: serverRecord.text,
                        createdAt: timestamp,
                        isMe: isMe,
                        isRead: serverRecord.isRead ?? false,
                        stamp: msgStamp
                    )

                    self.messageDedupeSet.insert(serverMsg.id)

                    var currentList = self.conversationMessages[recipientId] ?? []
                    if !currentList.contains(where: { $0.id == serverMsg.id }) {
                        currentList.append(serverMsg)
                        self.conversationMessages[recipientId] = currentList
                        self.saveLocalCache(recipientId: recipientId)
                    }

                    completion(.success(serverMsg))

                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }

    // MARK: - Mark Read RPC
    func markMessagesAsRead(senderId: String, completion: ((Result<Void, Error>) -> Void)? = nil) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        // Strictly use Security Definer RPC mark_direct_messages_read
        SupabaseSocialClient.shared.markMessagesAsReadRpc(senderId: senderId) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if var currentList = self.conversationMessages[senderId] {
                        for i in 0..<currentList.count {
                            if currentList[i].senderId == senderId {
                                currentList[i].isRead = true
                            }
                        }
                        self.conversationMessages[senderId] = currentList
                        self.saveLocalCache(recipientId: senderId)
                    }
                    completion?(.success(()))
                case .failure(let err):
                    completion?(.failure(err))
                }
            }
        }
    }

    // MARK: - Realtime Push Event Handlers
    private func handleRealtimeMessageInsert(_ record: SupabaseDirectMessageRecord) {
        guard !activeUserId.isEmpty else { return }

        // Ignore messages not belonging to current user
        guard record.senderId == activeUserId || record.recipientId == activeUserId else { return }

        let otherUserId = (record.senderId == activeUserId) ? record.recipientId : record.senderId

        // Deduplicate by message ID
        if messageDedupeSet.contains(record.id) { return }
        messageDedupeSet.insert(record.id)

        let isMe = record.senderId == activeUserId
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)

        var stamp: StampItem? = nil
        if let sid = record.stampId, !sid.isEmpty {
            stamp = StampItem(id: sid, title: record.stampTitle ?? "", stampImagePath: record.stampImageUrl ?? "", locationName: record.stampLocation ?? "", dateCreated: "")
        }

        let newMsg = ChatMessage(
            id: record.id,
            senderId: record.senderId,
            senderName: isMe ? "Me" : record.senderName,
            senderAvatar: record.senderAvatar ?? "https://i.pravatar.cc/150?u=\(record.senderId)",
            recipientId: record.recipientId,
            text: record.text,
            createdAt: timestamp,
            isMe: isMe,
            isRead: record.isRead ?? false,
            stamp: stamp
        )

        var currentList = conversationMessages[otherUserId] ?? []
        currentList.append(newMsg)
        conversationMessages[otherUserId] = currentList
        saveLocalCache(recipientId: otherUserId)

        // If the open conversation screen matches sender of incoming message, auto mark read
        if activeRecipientId == otherUserId && !isMe {
            markMessagesAsRead(senderId: otherUserId)
        }
    }

    private func handleRealtimeMessageUpdate(_ record: SupabaseDirectMessageRecord) {
        guard !activeUserId.isEmpty else { return }

        let otherUserId = (record.senderId == activeUserId) ? record.recipientId : record.senderId
        guard var currentList = conversationMessages[otherUserId] else { return }

        if let idx = currentList.firstIndex(where: { $0.id == record.id }) {
            currentList[idx].isRead = record.isRead ?? currentList[idx].isRead
            conversationMessages[otherUserId] = currentList
            saveLocalCache(recipientId: otherUserId)
        }
    }
}
