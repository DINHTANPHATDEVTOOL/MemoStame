import Foundation

class SupabaseRealtimeClient: NSObject {
    static let shared = SupabaseRealtimeClient()

    let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var heartbeatTimer: Timer?

    private(set) var isSubscribed: Bool = false
    private(set) var currentToken: String?
    private(set) var currentUid: String?

    private var refCounter: Int = 1

    var onMessageReceived: ((SupabaseDirectMessageRecord) -> Void)?
    var onMessageUpdated: ((SupabaseDirectMessageRecord) -> Void)?

    private override init() {
        super.init()
    }

    private func nextRef() -> String {
        let r = refCounter
        refCounter += 1
        return "\(r)"
    }

    func connectAndSubscribe(token: String, uid: String) {
        guard !token.isEmpty, !uid.isEmpty else {
            disconnect(clearState: true)
            return
        }

        // Enforce strict disconnect-before-reconnect lifecycle to prevent duplicate WebSocket channels
        disconnect(clearState: false)

        self.currentToken = token
        self.currentUid = uid

        let wsUrlString = "wss://mghmhhbyhmuvherlyrqa.supabase.co/realtime/v1/websocket?vsn=1.0.0&apikey=\(anonKey)&token=\(token)"
        guard let url = URL(string: wsUrlString) else { return }

        let configuration = URLSessionConfiguration.default
        urlSession = URLSession(configuration: configuration, delegate: self, delegateQueue: OperationQueue.main)
        webSocketTask = urlSession?.webSocketTask(with: url)
        webSocketTask?.resume()

        listen()
        sendPhxJoin(token: token)
        startHeartbeat()
    }

    func updateTokenOrReconnect(token: String, uid: String) {
        if token != currentToken || uid != currentUid || !isSubscribed {
            connectAndSubscribe(token: token, uid: uid)
        }
    }

    func disconnect(clearState: Bool = true) {
        stopHeartbeat()

        if isSubscribed {
            sendPhxLeave()
        }

        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil

        isSubscribed = false

        if clearState {
            currentToken = nil
            currentUid = nil
        }
    }

    private func sendPhxJoin(token: String) {
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

        sendJson(joinPayload)
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

    private func startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            self?.sendHeartbeat()
        }
    }

    private func stopHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    private func sendHeartbeat() {
        let heartbeatPayload: [String: Any] = [
            "topic": "phoenix",
            "event": "phx_heartbeat",
            "payload": [:],
            "ref": nextRef()
        ]
        sendJson(heartbeatPayload)
    }

    private func sendJson(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let jsonStr = String(data: data, encoding: .utf8) else { return }

        let message = URLSessionWebSocketTask.Message.string(jsonStr)
        webSocketTask?.send(message) { _ in }
    }

    private func listen() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleIncomingText(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleIncomingText(text)
                    }
                @unknown default:
                    break
                }
                self.listen()
            case .failure:
                self.isSubscribed = false
            }
        }
    }

    private func handleIncomingText(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return }

        let event = (json["event"] as? String) ?? ""
        let topic = (json["topic"] as? String) ?? ""

        if event == "phx_reply" && (topic == "realtime:public:direct_messages" || topic.contains("direct_messages")) {
            if let payload = json["payload"] as? [String: Any],
               let status = payload["status"] as? String, status == "ok" {
                self.isSubscribed = true
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
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        self.isSubscribed = false
    }
}
