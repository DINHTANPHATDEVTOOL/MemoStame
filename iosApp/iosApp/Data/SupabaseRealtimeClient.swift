import Foundation

class SupabaseRealtimeClient: NSObject {
    static let shared = SupabaseRealtimeClient()

    let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTimer: Timer?

    private(set) var isConnected: Bool = false
    private(set) var isSubscribed: Bool = false
    private(set) var currentToken: String?
    private(set) var currentUid: String?

    // Epoch generation to protect against stale callbacks
    private var connectionGeneration: Int64 = 0
    private var reconnectAttempt: Int = 0
    private var reconnectWorkItem: DispatchWorkItem?
    private var joinWatchdogWorkItem: DispatchWorkItem?

    private var refCounter: Int = 1

    var onMessageReceived: ((SupabaseDirectMessageRecord) -> Void)?
    var onMessageUpdated: ((SupabaseDirectMessageRecord) -> Void)?
    var onSubscriptionReady: ((String) -> Void)?

    private override init() {
        super.init()
    }

    private func nextRef() -> String {
        let r = refCounter
        refCounter += 1
        return "\(r)"
    }

    static func calculateReconnectDelay(attempt: Int) -> TimeInterval {
        let baseDelays: [TimeInterval] = [1.0, 2.0, 4.0, 8.0, 16.0, 30.0]
        let base = attempt < baseDelays.count ? baseDelays[attempt] : 30.0
        let jitter = Double.random(in: 0.0...0.20) * base
        return min(base + jitter, 30.0)
    }

    func connectAndSubscribe(token: String, uid: String) {
        guard !token.isEmpty, !uid.isEmpty else {
            disconnect(clearState: true)
            return
        }

        // Increment generation and tear down previous socket
        disconnect(clearState: false)

        self.currentToken = token
        self.currentUid = uid

        startConnectionLifecycle(generation: self.connectionGeneration)
    }

    private func startConnectionLifecycle(generation: Int64) {
        guard generation == connectionGeneration else { return }
        guard let token = currentToken, let uid = currentUid, !token.isEmpty, !uid.isEmpty else { return }

        let wsUrlString = "wss://mghmhhbyhmuvherlyrqa.supabase.co/realtime/v1/websocket?vsn=1.0.0&apikey=\(anonKey)&token=\(token)"
        guard let url = URL(string: wsUrlString) else { return }

        let configuration = URLSessionConfiguration.default
        let session = URLSession(configuration: configuration, delegate: self, delegateQueue: OperationQueue.main)
        self.urlSession = session
        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        listen(generation: generation)
    }

    func updateTokenOrReconnect(token: String, uid: String) {
        if token != currentToken || uid != currentUid || !isSubscribed || !isConnected {
            connectAndSubscribe(token: token, uid: uid)
        }
    }

    func disconnect(clearState: Bool = true) {
        // Invalidate generation to drop any pending timers or callbacks
        connectionGeneration += 1

        cancelPendingReconnect()
        cancelJoinWatchdog()
        stopHeartbeat()

        if isSubscribed {
            sendPhxLeave()
        }

        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil

        isConnected = false
        isSubscribed = false

        if clearState {
            currentToken = nil
            currentUid = nil
            reconnectAttempt = 0
        }
    }

    private func scheduleReconnect(generation: Int64) {
        guard generation == connectionGeneration else { return }
        guard let uid = currentUid, let token = currentToken, !uid.isEmpty, !token.isEmpty else { return }

        // Clean up socket resources for this failed cycle
        cancelJoinWatchdog()
        stopHeartbeat()
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil
        isConnected = false
        isSubscribed = false

        cancelPendingReconnect()

        let delay = Self.calculateReconnectDelay(attempt: reconnectAttempt)
        reconnectAttempt += 1

        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            guard self.connectionGeneration == generation else { return }
            guard self.currentUid == uid, self.currentToken == token else { return }
            self.startConnectionLifecycle(generation: generation)
        }
        self.reconnectWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: workItem)
    }

    private func cancelPendingReconnect() {
        reconnectWorkItem?.cancel()
        reconnectWorkItem = nil
    }

    private func startJoinWatchdog(generation: Int64) {
        cancelJoinWatchdog()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            guard self.connectionGeneration == generation else { return }
            if !self.isSubscribed {
                // Join ACK timeout (8-12s watchdog): treat connection as unhealthy and reconnect
                self.scheduleReconnect(generation: generation)
            }
        }
        self.joinWatchdogWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 10.0, execute: workItem)
    }

    private func cancelJoinWatchdog() {
        joinWatchdogWorkItem?.cancel()
        joinWatchdogWorkItem = nil
    }

    private func sendPhxJoin(token: String, generation: Int64) {
        guard generation == connectionGeneration else { return }

        let joinPayload: [String: Any] = [
            "topic": "realtime:public:direct_messages",
            "event": "phx_join",
            "payload": [
                "config": [
                    "postgres_changes": [
                        [
                            "event": "*",
                            "schema": "public",
                            "table": "direct_messages"
                        ]
                    ]
                ],
                "access_token": token
            ],
            "ref": nextRef()
        ]

        sendJson(joinPayload) { [weak self] success in
            guard let self = self else { return }
            if !success && self.connectionGeneration == generation {
                self.scheduleReconnect(generation: generation)
            }
        }
    }

    private func sendPhxLeave() {
        let leavePayload: [String: Any] = [
            "topic": "realtime:public:direct_messages",
            "event": "phx_leave",
            "payload": [:],
            "ref": nextRef()
        ]
        sendJson(leavePayload)
    }

    private func startHeartbeat(generation: Int64) {
        stopHeartbeat()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            guard self.connectionGeneration == generation else { return }
            self.sendHeartbeat(generation: generation)
        }
    }

    private func stopHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    private func sendHeartbeat(generation: Int64) {
        guard generation == connectionGeneration else { return }
        let heartbeatPayload: [String: Any] = [
            "topic": "phoenix",
            "event": "phx_heartbeat",
            "payload": [:],
            "ref": nextRef()
        ]
        sendJson(heartbeatPayload) { [weak self] success in
            guard let self = self else { return }
            if !success && self.connectionGeneration == generation {
                self.scheduleReconnect(generation: generation)
            }
        }
    }

    private func sendJson(_ dict: [String: Any], completion: ((Bool) -> Void)? = nil) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let jsonStr = String(data: data, encoding: .utf8) else {
            completion?(false)
            return
        }

        let message = URLSessionWebSocketTask.Message.string(jsonStr)
        webSocketTask?.send(message) { error in
            completion?(error == nil)
        }
    }

    private func listen(generation: Int64) {
        guard generation == connectionGeneration else { return }

        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            guard self.connectionGeneration == generation else { return }

            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleIncomingText(text, generation: generation)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleIncomingText(text, generation: generation)
                    }
                @unknown default:
                    break
                }
                self.listen(generation: generation)
            case .failure:
                self.isSubscribed = false
                self.isConnected = false
                self.scheduleReconnect(generation: generation)
            }
        }
    }

    private func handleIncomingText(_ text: String, generation: Int64) {
        guard generation == connectionGeneration else { return }
        guard let data = text.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }

        let event = (json["event"] as? String) ?? ""
        let topic = (json["topic"] as? String) ?? ""

        if event == "phx_reply" && (topic == "realtime:public:direct_messages" || topic.contains("direct_messages")) {
            if let payload = json["payload"] as? [String: Any] {
                let status = payload["status"] as? String
                if status == "ok" {
                    self.cancelJoinWatchdog()
                    self.isSubscribed = true
                    self.reconnectAttempt = 0
                    if let uid = self.currentUid {
                        self.onSubscriptionReady?(uid)
                    }
                } else if status == "error" {
                    self.cancelJoinWatchdog()
                    self.isSubscribed = false
                    self.scheduleReconnect(generation: generation)
                }
            }
        }

        if event == "postgres_changes" {
            guard let payload = json["payload"] as? [String: Any],
                  let dataDict = payload["data"] as? [String: Any],
                  let changeType = dataDict["type"] as? String,
                  let recordDict = dataDict["record"] as? [String: Any] else { return }

            guard let record = parseMessageRecord(from: recordDict) else { return }

            // Strictly filter by active authenticated user UUID to ensure privacy and isolation
            guard let activeUid = currentUid, !activeUid.isEmpty else { return }
            if record.senderId != activeUid && record.recipientId != activeUid {
                // Ignore third-user Realtime messages
                return
            }

            if changeType == "INSERT" {
                self.onMessageReceived?(record)
            } else if changeType == "UPDATE" {
                self.onMessageUpdated?(record)
            }
        }
    }

    private func parseMessageRecord(from dict: [String: Any]) -> SupabaseDirectMessageRecord? {
        guard let id = dict["id"] as? String, !id.isEmpty,
              let senderId = dict["sender_id"] as? String, !senderId.isEmpty,
              let recipientId = dict["recipient_id"] as? String, !recipientId.isEmpty else { return nil }

        let text = (dict["text"] as? String) ?? ""
        let senderName = (dict["sender_name"] as? String) ?? ""
        let senderAvatar = dict["sender_avatar"] as? String
        let recipientName = (dict["recipient_name"] as? String) ?? ""
        let recipientAvatar = dict["recipient_avatar"] as? String
        let stampId = dict["stamp_id"] as? String
        let stampTitle = dict["stamp_title"] as? String
        let stampImageUrl = dict["stamp_image_url"] as? String
        let stampLocation = dict["stamp_location"] as? String
        let createdAt = dict["created_at"] as? String
        let isRead = dict["is_read"] as? Bool

        return SupabaseDirectMessageRecord(
            id: id,
            senderId: senderId,
            senderName: senderName,
            senderAvatar: senderAvatar,
            recipientId: recipientId,
            recipientName: recipientName,
            recipientAvatar: recipientAvatar,
            text: text,
            stampId: stampId,
            stampTitle: stampTitle,
            stampImageUrl: stampImageUrl,
            stampLocation: stampLocation,
            createdAt: createdAt,
            isRead: isRead
        )
    }
}

extension SupabaseRealtimeClient: URLSessionWebSocketDelegate {
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            guard let token = self.currentToken, !token.isEmpty else { return }
            self.isConnected = true
            let gen = self.connectionGeneration
            self.sendPhxJoin(token: token, generation: gen)
            self.startHeartbeat(generation: gen)
            self.startJoinWatchdog(generation: gen)
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let gen = self.connectionGeneration
            self.isConnected = false
            self.isSubscribed = false
            self.scheduleReconnect(generation: gen)
        }
    }
}
