import Foundation
import Combine
import shared

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

struct IOSChatConversation: Identifiable {
    var id: String { otherUser.id }
    let otherUser: FriendItem
    let lastMessage: ChatMessage?
    let unreadCount: Int
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

    private var isReconcileInFlight: Bool = false
    private var reconcileRequestedWhileBusy: Bool = false

    private init() {}

    static func parseServerTimestampMillis(_ value: String?) -> Int64? {
        guard let dateStr = value?.trimmingCharacters(in: .whitespacesAndNewlines), !dateStr.isEmpty else {
            return nil
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: dateStr) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        formatter.formatOptions = [.withInternetDateTime]
        if let date = formatter.date(from: dateStr) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        if let millis = Int64(dateStr) {
            return millis < 10_000_000_000 ? millis * 1000 : millis
        }
        return nil
    }

    static func parseIsoStringToMillis(_ dateStr: String?) -> Int64 {
        return parseServerTimestampMillis(dateStr) ?? Int64(Date().timeIntervalSince1970 * 1000)
    }

    var totalUnreadCount: Int {
        var count = 0
        for (_, msgs) in conversationMessages {
            count += msgs.filter { !$0.isMe && $0.recipientId == activeUserId && !$0.isRead }.count
        }
        return count
    }

    func getConversationList(friends: [FriendItem]) -> [IOSChatConversation] {
        guard !activeUserId.isEmpty else { return [] }
        return friends.map { friend in
            let msgs = conversationMessages[friend.id] ?? []
            let sortedHistory = msgs.sorted { (m1, m2) -> Bool in
                if m1.createdAt == m2.createdAt {
                    return m1.id > m2.id
                }
                return m1.createdAt > m2.createdAt
            }
            let last = sortedHistory.first
            let unread = msgs.filter { !$0.isMe && $0.recipientId == activeUserId && !$0.isRead }.count
            return IOSChatConversation(
                otherUser: friend,
                lastMessage: last,
                unreadCount: unread
            )
        }.sorted { (c1, c2) -> Bool in
            let t1 = c1.lastMessage?.createdAt ?? 0
            let t2 = c2.lastMessage?.createdAt ?? 0
            if t1 == t2 {
                return c1.otherUser.displayName < c2.otherUser.displayName
            }
            return t1 > t2
        }
    }

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

    func onLogout() {
        SupabaseRealtimeClient.shared.disconnect(clearState: true)
        self.conversationMessages = [:]
        self.messageDedupeSet = []
        self.activeRecipientId = nil
        self.errorMessage = nil
        self.activeUserId = ""
    }

    func onUserChanged(newUserId: String) {
        let token = SupabaseAuthService.shared.activeSession?.accessToken
        onSessionChanged(userId: newUserId, accessToken: token)
    }

    func onSessionChanged(userId: String, accessToken: String?) {
        let cleanUid = userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanToken = accessToken?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if cleanUid.isEmpty || cleanToken.isEmpty {
            onLogout()
            return
        }

        if cleanUid != activeUserId {
            // User switch isolation: clear previous user state & disconnect old realtime
            SupabaseRealtimeClient.shared.disconnect(clearState: true)
            self.conversationMessages = [:]
            self.messageDedupeSet = []
            self.activeRecipientId = nil
            self.errorMessage = nil
            self.activeUserId = cleanUid

            setupRealtimeSubscriptions(token: cleanToken, uid: cleanUid)
        } else {
            // Same user token rotation or health check: preserve in-memory chat state & update token/reconnect
            setupRealtimeSubscriptions(token: cleanToken, uid: cleanUid)
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

        SupabaseRealtimeClient.shared.onSubscriptionReady = { [weak self] uid in
            DispatchQueue.main.async {
                guard let self = self, self.activeUserId == uid else { return }
                self.reconcileMessagesFromCloud()
            }
        }

        SupabaseRealtimeClient.shared.updateTokenOrReconnect(token: token, uid: uid)
    }

    func onAppBecameActive() {
        guard !activeUserId.isEmpty,
              let session = SupabaseAuthService.shared.activeSession,
              session.userId == activeUserId,
              !session.accessToken.isEmpty else { return }

        // 1. Health-check / reconnect realtime
        SupabaseRealtimeClient.shared.updateTokenOrReconnect(token: session.accessToken, uid: activeUserId)
        // 2. Trigger one REST reconciliation
        reconcileMessagesFromCloud()
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
                stamp = StampItem(
                    id: sid,
                    originalImagePath: m.stampImageUrl ?? "",
                    stampImagePath: m.stampImageUrl ?? "",
                    title: m.stampTitle ?? "",
                    note: "",
                    createdAt: m.createdAt,
                    memoryDate: m.createdAt,
                    location: m.stampLocation,
                    mood: nil,
                    collectionId: nil,
                    favorite: false,
                    filterId: nil,
                    shape: "classic",
                    preset: "NATURAL"
                )
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
                stampLocation: m.stamp?.location
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
                        guard let timestamp = IOSChatRepository.parseServerTimestampMillis(r.createdAt) else { return nil }
                        let isMe = r.senderId == self.activeUserId

                        var stamp: StampItem? = nil
                        if let sid = r.stampId, !sid.isEmpty {
                            stamp = StampItem(
                                id: sid,
                                originalImagePath: r.stampImageUrl ?? "",
                                stampImagePath: r.stampImageUrl ?? "",
                                title: r.stampTitle ?? "",
                                note: "",
                                createdAt: timestamp,
                                memoryDate: timestamp,
                                location: r.stampLocation,
                                mood: nil,
                                collectionId: nil,
                                favorite: false,
                                filterId: nil,
                                shape: "classic",
                                preset: "NATURAL"
                            )
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

        let remoteStampUrl = isValidRemoteStampUrl(stamp?.stampImagePath) ? stamp?.stampImagePath : nil

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
            stampImageUrl: remoteStampUrl,
            stampLocation: stamp?.location,
            createdAt: isoCreatedAt,
            isRead: false
        )

        SupabaseSocialClient.shared.sendDirectMessage(message: record) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                switch result {
                case .success(let serverRecord):
                    guard let timestamp = IOSChatRepository.parseServerTimestampMillis(serverRecord.createdAt) else {
                        completion(.failure(SupabaseSocialError.invalidData("Phản hồi máy chủ chứa thời gian không hợp lệ.")))
                        return
                    }
                    let isMe = serverRecord.senderId == self.activeUserId

                    var msgStamp: StampItem? = nil
                    if let sid = serverRecord.stampId, !sid.isEmpty {
                        msgStamp = StampItem(
                            id: sid,
                            originalImagePath: serverRecord.stampImageUrl ?? "",
                            stampImagePath: serverRecord.stampImageUrl ?? "",
                            title: serverRecord.stampTitle ?? "",
                            note: "",
                            createdAt: timestamp,
                            memoryDate: timestamp,
                            location: serverRecord.stampLocation,
                            mood: nil,
                            collectionId: nil,
                            favorite: false,
                            filterId: nil,
                            shape: "classic",
                            preset: "NATURAL"
                        )
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
        guard let timestamp = IOSChatRepository.parseServerTimestampMillis(record.createdAt) else {
            // Drop malformed event missing authoritative server timestamp
            return
        }

        var stamp: StampItem? = nil
        if let sid = record.stampId, !sid.isEmpty {
            stamp = StampItem(
                id: sid,
                originalImagePath: record.stampImageUrl ?? "",
                stampImagePath: record.stampImageUrl ?? "",
                title: record.stampTitle ?? "",
                note: "",
                createdAt: timestamp,
                memoryDate: timestamp,
                location: record.stampLocation,
                mood: nil,
                collectionId: nil,
                favorite: false,
                filterId: nil,
                shape: "classic",
                preset: "NATURAL"
            )
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
        if !currentList.contains(where: { $0.id == newMsg.id }) {
            currentList.append(newMsg)
        } else if let idx = currentList.firstIndex(where: { $0.id == newMsg.id }) {
            currentList[idx] = newMsg
        }
        currentList.sort { (m1, m2) -> Bool in
            if m1.createdAt == m2.createdAt {
                return m1.id < m2.id
            }
            return m1.createdAt < m2.createdAt
        }
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

    // MARK: - Gap Reconciliation
    func reconcileMessagesFromCloud(completion: ((Result<Void, Error>) -> Void)? = nil) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        if isReconcileInFlight {
            reconcileRequestedWhileBusy = true
            return
        }
        isReconcileInFlight = true

        SupabaseSocialClient.shared.getMessagesForUser(userId: activeUserId) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                defer {
                    self.isReconcileInFlight = false
                    if self.reconcileRequestedWhileBusy {
                        self.reconcileRequestedWhileBusy = false
                        self.reconcileMessagesFromCloud()
                    }
                }

                switch result {
                case .success(let records):
                    self.mergeAuthoritativeCloudMessages(records)
                    completion?(.success(()))
                case .failure(let err):
                    // Network failure retains cached state
                    print("Gap reconciliation cloud failure (retaining cached state): \(err.localizedDescription)")
                    completion?(.failure(err))
                }
            }
        }
    }

    private func mergeAuthoritativeCloudMessages(_ records: [SupabaseDirectMessageRecord]) {
        guard !activeUserId.isEmpty else { return }

        var conversationMap = [String: [ChatMessage]]()

        for r in records {
            guard SupabaseSocialClient.shared.isValidUuid(r.id) else { continue }
            guard r.senderId == activeUserId || r.recipientId == activeUserId else { continue }
            guard let timestamp = IOSChatRepository.parseServerTimestampMillis(r.createdAt) else { continue }

            let isMe = r.senderId == activeUserId
            let otherUserId = isMe ? r.recipientId : r.senderId

            var stamp: StampItem? = nil
            if let sid = r.stampId, !sid.isEmpty {
                let safeUrl = isValidRemoteStampUrl(r.stampImageUrl) ? r.stampImageUrl : nil
                stamp = StampItem(
                    id: sid,
                    originalImagePath: safeUrl ?? "",
                    stampImagePath: safeUrl ?? "",
                    title: r.stampTitle ?? "",
                    note: "",
                    createdAt: timestamp,
                    memoryDate: timestamp,
                    location: r.stampLocation,
                    mood: nil,
                    collectionId: nil,
                    favorite: false,
                    filterId: nil,
                    shape: "classic",
                    preset: "NATURAL"
                )
            }

            let msg = ChatMessage(
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

            var list = conversationMap[otherUserId] ?? []
            list.append(msg)
            conversationMap[otherUserId] = list
        }

        for (otherUserId, cloudMsgs) in conversationMap {
            let existing = self.conversationMessages[otherUserId] ?? []
            var msgMap = [String: ChatMessage]()
            for m in existing { msgMap[m.id] = m }
            for m in cloudMsgs { msgMap[m.id] = m }

            let merged = msgMap.values.sorted { (m1, m2) -> Bool in
                if m1.createdAt == m2.createdAt {
                    return m1.id < m2.id
                }
                return m1.createdAt < m2.createdAt
            }
            self.conversationMessages[otherUserId] = merged
            for m in merged { self.messageDedupeSet.insert(m.id) }
            self.saveLocalCache(recipientId: otherUserId)
        }
    }
}
