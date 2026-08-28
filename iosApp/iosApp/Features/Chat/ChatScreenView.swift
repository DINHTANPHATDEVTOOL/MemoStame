import SwiftUI
import shared

struct ChatMessage: Identifiable, Codable {
    let id: String
    let senderId: String
    let senderName: String
    let senderAvatar: String
    let recipientId: String
    let text: String
    let createdAt: Int64
    let isMe: Bool
    var isRead: Bool
    let stamp: StampItem?

    var timestamp: Date {
        Date(timeIntervalSince1970: TimeInterval(createdAt) / 1000.0)
    }

    var isFromCurrentUser: Bool {
        isMe
    }

    var stampUrl: String? {
        stamp?.stampImagePath
    }

    var stampTitle: String? {
        stamp?.title
    }

    init(id: String, senderId: String, senderName: String, senderAvatar: String = "", recipientId: String, text: String, createdAt: Int64, isMe: Bool, isRead: Bool = false, stamp: StampItem? = nil) {
        self.id = id
        self.senderId = senderId
        self.senderName = senderName
        self.senderAvatar = senderAvatar
        self.recipientId = recipientId
        self.text = text
        self.createdAt = createdAt
        self.isMe = isMe
        self.isRead = isRead
        self.stamp = stamp
    }
}

struct ChatScreenView: View {
    let recipientUserId: String
    let recipientName: String
    var recipientIsOnline: Bool = true
    let currentUserId: String
    var repository: SharedMemoStampRepository? = nil
    var availableStamps: [StampItem] = []
    var onDismiss: (() -> Void)? = nil

    @Environment(\.presentationMode) var presentationMode
    @ObservedObject private var chatRepo = IOSChatRepository.shared

    @State private var messageText: String = ""
    @State private var showStampPicker: Bool = false
    @State private var toastMessage: String? = nil

    private var messages: [ChatMessage] {
        chatRepo.conversationMessages[recipientUserId] ?? []
    }

    private func formatDate(_ timestamp: Int64) -> String {
        guard timestamp > 0 else {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy.MM.dd"
            return formatter.string(from: Date())
        }
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: date)
    }

    var userStamps: [StampItem] {
        if !availableStamps.isEmpty {
            return availableStamps
        }
        if let repo = repository {
            return (repo.stamps.value as? [StampItem]) ?? []
        }
        return []
    }

    var body: some View {
        VStack(spacing: 0) {
            // Chat Header
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(MSColors.mint)
                        .frame(width: 40, height: 40)
                    Text(String(recipientName.prefix(1)).uppercased())
                        .font(.headline.bold())
                        .foregroundColor(MSColors.ink)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(recipientName)
                        .font(.headline)
                        .foregroundColor(MSColors.ink)
                    HStack(spacing: 4) {
                        Circle()
                            .fill(recipientIsOnline ? Color.green : Color.gray)
                            .frame(width: 6, height: 6)
                        Text(recipientIsOnline ? "Online" : "Offline")
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                    }
                }

                Spacer()

                Button(action: {
                    chatRepo.activeRecipientId = nil
                    if let dismiss = onDismiss {
                        dismiss()
                    } else {
                        presentationMode.wrappedValue.dismiss()
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(MSColors.grey)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.white)
            .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 2)

            Divider()

            // Chat Messages Scroll Area
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 12) {
                        if messages.isEmpty && !chatRepo.isLoading {
                            VStack(spacing: 8) {
                                Image(systemName: "bubble.left.and.bubble.right.fill")
                                    .font(.system(size: 40))
                                    .foregroundColor(MSColors.stamp.opacity(0.6))
                                Text("Bắt đầu cuộc trò chuyện với \(recipientName)")
                                    .font(.subheadline.bold())
                                    .foregroundColor(MSColors.ink)
                                Text("Gửi tin nhắn hoặc đính kèm Tem kỷ niệm để trao đổi!")
                                    .font(.caption)
                                    .foregroundColor(MSColors.grey)
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.top, 60)
                            .padding(.horizontal, 32)
                        }

                        ForEach(messages) { msg in
                            ChatMessageBubble(message: msg)
                                .id(msg.id)
                        }
                    }
                    .padding(16)
                }
                .onChange(of: messages.count) { _ in
                    if let lastMsg = messages.last {
                        withAnimation {
                            proxy.scrollTo(lastMsg.id, anchor: .bottom)
                        }
                    }
                }
            }

            Divider()

            // Bottom Chat Input Bar
            HStack(spacing: 10) {
                // Attach Stamp Button
                Button(action: { showStampPicker = true }) {
                    ZStack {
                        Circle()
                            .fill(MSColors.stamp.opacity(0.12))
                            .frame(width: 38, height: 38)
                        Image(systemName: "square.stack.3d.up.fill")
                            .font(.system(size: 16))
                            .foregroundColor(MSColors.stamp)
                    }
                }

                // Text Input Field
                TextField("Nhập tin nhắn...", text: $messageText)
                    .font(.subheadline)
                    .foregroundColor(MSColors.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(MSColors.lightGrey, lineWidth: 1)
                    )

                // Send Button
                Button(action: sendMessage) {
                    ZStack {
                        Circle()
                            .fill(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? MSColors.lightGrey : MSColors.stamp)
                            .frame(width: 38, height: 38)
                        Image(systemName: "paperplane.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                    }
                }
                .disabled(messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(MSColors.paper)
        }
        .background(MSColors.paper.ignoresSafeArea())
        .onAppear {
            chatRepo.activeRecipientId = recipientUserId
            chatRepo.loadConversation(otherUserId: recipientUserId) { _ in }
            chatRepo.markMessagesAsRead(senderId: recipientUserId)
        }
        .onDisappear {
            if chatRepo.activeRecipientId == recipientUserId {
                chatRepo.activeRecipientId = nil
            }
        }
        .sheet(isPresented: $showStampPicker) {
            VStack(spacing: 16) {
                HStack {
                    Text("Chọn Tem Đặt Trong Tin Nhắn")
                        .font(.headline)
                        .foregroundColor(MSColors.ink)
                    Spacer()
                    Button("Đóng") { showStampPicker = false }
                        .foregroundColor(MSColors.stamp)
                }
                .padding()

                if userStamps.isEmpty {
                    VStack(spacing: 8) {
                        Text("Chưa có tem kỷ niệm nào")
                            .font(.headline)
                            .foregroundColor(MSColors.ink)
                        Text("Hãy chụp và lưu tem kỷ niệm từ camera trước khi chia sẻ trong tin nhắn!")
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 40)
                    .padding(.horizontal, 20)
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            ForEach(userStamps, id: \.id) { stamp in
                                VStack {
                                    DieCutStampView(
                                        title: stamp.title,
                                        imageUrl: stamp.stampImagePath,
                                        location: stamp.location,
                                        dateStr: formatDate(
                                            stamp.memoryDate > 0
                                                ? stamp.memoryDate
                                                : stamp.createdAt
                                        ),
                                        note: stamp.note,
                                        shape: stamp.shape,
                                        isInteractive: false
                                    )
                                    Button(action: {
                                        sendStampMessage(stamp: stamp)
                                        showStampPicker = false
                                    }) {
                                        Text("Gửi Tem Này")
                                            .font(.caption.bold())
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(MSColors.stamp)
                                            .foregroundColor(.white)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                }
            }
            .background(MSColors.paper.ignoresSafeArea())
        }
    }

    private func sendMessage() {
        let trimmed = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let textToSend = trimmed
        messageText = ""
        HapticFeedbackManager.shared.playImpact(style: .light)

        chatRepo.sendMessageCloud(recipientId: recipientUserId, text: textToSend) { result in
            switch result {
            case .success:
                print("Cloud DM sent successfully to \(recipientUserId)")
            case .failure(let err):
                print("Cloud DM send error: \(err.localizedDescription)")
            }
        }
    }

    private func sendStampMessage(stamp: StampItem) {
        let textToSend = "Đã gửi tem kỷ niệm: \(stamp.title)"
        HapticFeedbackManager.shared.playImpact(style: .medium)

        chatRepo.sendMessageCloud(recipientId: recipientUserId, text: textToSend, stamp: stamp) { result in
            switch result {
            case .success:
                print("Cloud Stamp DM sent successfully")
            case .failure(let err):
                print("Cloud Stamp DM send error: \(err.localizedDescription)")
            }
        }
    }
}

struct ChatMessageBubble: View {
    let message: ChatMessage

    var body: some View {
        HStack {
            if message.isFromCurrentUser { Spacer() }

            VStack(alignment: message.isFromCurrentUser ? .trailing : .leading, spacing: 6) {
                if let stampUrl = message.stampUrl, !stampUrl.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        MemoStampImageView(urlString: stampUrl) {
                            MSColors.lightGrey
                        }
                        .frame(width: 140, height: 140)
                        .cornerRadius(8)

                        if let title = message.stampTitle {
                            Text("✦ \(title)")
                                .font(.caption.bold())
                                .foregroundColor(message.isFromCurrentUser ? .white : MSColors.ink)
                        }
                    }
                    .padding(8)
                    .background(message.isFromCurrentUser ? MSColors.stamp : Color.white)
                    .cornerRadius(14)
                    .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 2)
                }

                if !message.text.isEmpty {
                    Text(message.text)
                        .font(.subheadline)
                        .foregroundColor(message.isFromCurrentUser ? .white : MSColors.ink)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(message.isFromCurrentUser ? MSColors.stamp : Color.white)
                        .cornerRadius(18)
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(message.isFromCurrentUser ? Color.clear : MSColors.lightGrey, lineWidth: 1)
                        )
                        .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                }

                HStack(spacing: 4) {
                    Text(DateFormatter.localizedString(from: message.timestamp, dateStyle: .none, timeStyle: .short))
                        .font(.caption2)
                        .foregroundColor(MSColors.grey)

                    if message.isFromCurrentUser {
                        Image(systemName: message.isRead ? "checkmark.circle.fill" : "checkmark")
                            .font(.system(size: 10))
                            .foregroundColor(message.isRead ? Color.blue : MSColors.grey)
                    }
                }
                .padding(.horizontal, 4)
            }

            if !message.isFromCurrentUser { Spacer() }
        }
    }
}
