import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct FriendsAndTradeScreenView: View {
    let repository: SharedMemoStampRepository

    @State private var friendCode: String = ""
    @State private var selectedTab: Int = 0 // 0: Friends, 1: Trade Requests, 2: Chat, 3: Inbox
    @State private var selectedFriendForTrade: FriendItem? = nil
    @State private var selectedFriendForChat: FriendItem? = nil
    @State private var showTradeModal: Bool = false
    @State private var showChatModal: Bool = false
    @State private var showQrCodeModal: Bool = false
    @State private var toastMessage: String? = nil
    @State private var showToast: Bool = false
    @State private var refreshTrigger: Bool = false
    @State private var friendToBlock: FriendItem? = nil
    @State private var showBlockAlert: Bool = false
    @State private var friendToReport: FriendItem? = nil
    @State private var showReportSheet: Bool = false
    @State private var reportCategory: String = "spam"
    @State private var reportNote: String = ""
    @State private var isSubmittingSafety: Bool = false
    @StateObject private var langManager = AppLanguageManager.shared
    @ObservedObject private var friendRepo = IOSFriendRepository.shared
    @ObservedObject private var chatRepo = IOSChatRepository.shared

    private var authenticatedUid: String? {
        let uid = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(uid) else {
            return nil
        }
        return uid
    }

    private var currentUid: String {
        authenticatedUid ?? ""
    }

    private func validatedMutationUid() -> String? {
        guard let uid = authenticatedUid else {
            triggerToast("Bạn cần đăng nhập để thực hiện thao tác này.")
            return nil
        }

        guard let repoUser = repository.currentUser.value as? UserProfile,
              repoUser.uid == uid else {
            triggerToast("Không thể xác minh tài khoản. Vui lòng thử lại.")
            return nil
        }

        return uid
    }

    private func getProcessedInboxIds(for userId: String) -> Set<String> {
        guard let uid = authenticatedUid, !userId.isEmpty, userId == uid else { return [] }
        let key = "processed_inbox_\(uid)"
        let array = UserDefaults.standard.stringArray(forKey: key) ?? []
        return Set(array)
    }

    private func markInboxItemProcessed(for userId: String, messageId: String) {
        guard let uid = authenticatedUid, !userId.isEmpty, userId == uid else {
            triggerToast("Bạn cần đăng nhập để thực hiện thao tác này.")
            return
        }
        let key = "processed_inbox_\(uid)"
        var current = getProcessedInboxIds(for: uid)
        current.insert(messageId)
        UserDefaults.standard.set(Array(current), forKey: key)
        refreshTrigger.toggle()
    }

    private func formattedTimestamp(_ timestampMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(timestampMillis) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "dd/MM/yyyy HH:mm"
        return formatter.string(from: date)
    }

    private func formattedTime(_ timestampMillis: Int64) -> String {
        guard timestampMillis > 0 else { return "" }
        let date = Date(timeIntervalSince1970: Double(timestampMillis) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    var visibleReceivedStamps: [ChatMessage] {
        _ = refreshTrigger
        guard let uid = authenticatedUid else { return [] }
        let processedIds = getProcessedInboxIds(for: uid)
        var result: [ChatMessage] = []
        for (_, msgs) in chatRepo.conversationMessages {
            for msg in msgs {
                if !msg.isMe && msg.recipientId == uid && msg.stamp != nil && !processedIds.contains(msg.id) {
                    result.append(msg)
                }
            }
        }
        return result.sorted(by: { $0.createdAt > $1.createdAt })
    }

    var allFriendRequests: [FriendRequestItem] {
        friendRepo.incomingRequests + friendRepo.outgoingRequests
    }

    var incomingFriendRequests: [FriendRequestItem] {
        friendRepo.incomingRequests
    }

    var outgoingFriendRequests: [FriendRequestItem] {
        friendRepo.outgoingRequests
    }

    var friends: [FriendItem] {
        friendRepo.friends
    }

    var incomingTradeRequests: [SupabaseTradeRequestRecord] {
        _ = refreshTrigger
        return friendRepo.incomingTrades
    }

    var outgoingTradeRequests: [SupabaseTradeRequestRecord] {
        _ = refreshTrigger
        return friendRepo.outgoingTrades
    }

    var receivedTradeStamps: [SupabaseReceivedStampRecord] {
        _ = refreshTrigger
        return friendRepo.receivedStamps
    }

    var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // Header
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(langManager.localized("friends_trade_title"))
                                .font(.title2.bold())
                                .foregroundColor(MSColors.ink)
                            Text(langManager.localized("friends_trade_subtitle"))
                                .font(.caption)
                                .foregroundColor(MSColors.grey)
                        }
                        Spacer()

                        // QR Code Profile Button
                        Button(action: {
                            guard authenticatedUid != nil else {
                                triggerToast("Bạn cần đăng nhập để xem mã QR.")
                                return
                            }
                            showQrCodeModal = true
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode")
                                    .font(.system(size: 16, weight: .bold))
                                Text(langManager.localized("friends_my_qr"))
                                    .font(.caption.bold())
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(MSColors.stamp.opacity(0.12))
                            .foregroundColor(MSColors.stamp)
                            .cornerRadius(12)
                        }
                    }

                    // Search/Add Friend Code Input Box with Validation & Friend Request Workflow
                    HStack(spacing: 10) {
                        Image(systemName: "person.badge.plus")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(MSColors.stamp)
                        TextField(langManager.localized("friends_search_hint"), text: $friendCode)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                        Button(action: {
                            guard authenticatedUid != nil else {
                                triggerToast("Bạn cần đăng nhập để gửi lời mời kết bạn.")
                                return
                            }
                            friendRepo.sendFriendRequestByUsernameOrId(input: friendCode) { result in
                                switch result {
                                case .success(let msg):
                                    triggerToast(msg)
                                    friendCode = ""
                                case .failure(let err):
                                    let errStr = err.localizedDescription
                                    if errStr.localizedCaseInsensitiveContains("RATE_LIMITED") || errStr.contains("429") {
                                        triggerToast("You're doing that too quickly. Please try again shortly.")
                                    } else {
                                        triggerToast(errStr)
                                    }
                                }
                            }
                        }) {
                            Text(langManager.localized("friends_send_invite"))
                                .font(.caption.bold())
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(friendCode.isEmpty ? MSColors.stamp.opacity(0.3) : MSColors.stamp)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                                .shadow(color: friendCode.isEmpty ? Color.clear : MSColors.stamp.opacity(0.3), radius: 4, x: 0, y: 2)
                        }
                        .disabled(friendCode.isEmpty)
                    }
                    .padding(12)
                    .background(MSColors.paper)
                    .cornerRadius(16)
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1.5))
                    .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)

                    // Custom High-Contrast Vintage Tab Bar
                    HStack(spacing: 8) {
                        Button(action: { selectedTab = 0 }) {
                            HStack(spacing: 4) {
                                Image(systemName: "person.2.fill")
                                    .font(.caption.bold())
                                Text(langManager.localized("friends_tab_friends", friends.count))
                                    .font(.subheadline.bold())
                            }
                            .padding(.vertical, 9)
                            .frame(maxWidth: .infinity)
                            .background(selectedTab == 0 ? MSColors.stamp : Color.white)
                            .foregroundColor(selectedTab == 0 ? .white : MSColors.grey)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selectedTab == 0 ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1))
                            .shadow(color: selectedTab == 0 ? MSColors.stamp.opacity(0.25) : Color.clear, radius: 4, x: 0, y: 2)
                        }

                        Button(action: { selectedTab = 1 }) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.caption.bold())
                                Text(langManager.localized("friends_tab_trades", incomingTradeRequests.count))
                                    .font(.subheadline.bold())
                            }
                            .padding(.vertical, 9)
                            .frame(maxWidth: .infinity)
                            .background(selectedTab == 1 ? MSColors.stamp : Color.white)
                            .foregroundColor(selectedTab == 1 ? .white : MSColors.grey)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selectedTab == 1 ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1))
                            .shadow(color: selectedTab == 1 ? MSColors.stamp.opacity(0.25) : Color.clear, radius: 4, x: 0, y: 2)
                        }

                        Button(action: { selectedTab = 2 }) {
                            HStack(spacing: 4) {
                                Image(systemName: "bubble.left.and.bubble.right.fill")
                                    .font(.caption.bold())
                                let unreadChat = chatRepo.totalUnreadCount
                                Text(unreadChat > 0 ? "\(langManager.localized("friends_tab_chat")) (\(unreadChat))" : langManager.localized("friends_tab_chat"))
                                    .font(.subheadline.bold())
                            }
                            .padding(.vertical, 9)
                            .frame(maxWidth: .infinity)
                            .background(selectedTab == 2 ? MSColors.stamp : Color.white)
                            .foregroundColor(selectedTab == 2 ? .white : MSColors.grey)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selectedTab == 2 ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1))
                            .shadow(color: selectedTab == 2 ? MSColors.stamp.opacity(0.25) : Color.clear, radius: 4, x: 0, y: 2)
                        }

                        Button(action: { selectedTab = 3 }) {
                            HStack(spacing: 4) {
                                Image(systemName: "envelope.fill")
                                    .font(.caption.bold())
                                Text(langManager.localized("friends_tab_inbox", visibleReceivedStamps.count + receivedTradeStamps.count))
                                    .font(.subheadline.bold())
                            }
                            .padding(.vertical, 9)
                            .frame(maxWidth: .infinity)
                            .background(selectedTab == 3 ? MSColors.stamp : Color.white)
                            .foregroundColor(selectedTab == 3 ? .white : MSColors.grey)
                            .cornerRadius(18)
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(selectedTab == 3 ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1))
                            .shadow(color: selectedTab == 3 ? MSColors.stamp.opacity(0.25) : Color.clear, radius: 4, x: 0, y: 2)
                        }
                    }
                    .padding(.top, 4)
                }
                .padding()

                Divider()

                ScrollView {
                    VStack(spacing: 12) {
                        if selectedTab == 0 {
                            friendsTabContent
                        } else if selectedTab == 1 {
                            tradeRequestsTabContent
                        } else if selectedTab == 2 {
                            directChatTabContent
                        } else if selectedTab == 3 {
                            receivedStampsInboxTabContent
                        }
                    }
                }
                .padding()
                .padding(.bottom, 140)
            }

            if showToast, let msg = toastMessage {
                VStack {
                    Text(msg)
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.8))
                        .cornerRadius(20)
                        .shadow(radius: 4)
                        .padding(.top, 40)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(isPresented: $showTradeModal) {
            if let friend = selectedFriendForTrade {
                TradeStampModalView(
                    friend: friend,
                    stamps: stamps,
                    onSendTrade: { stampId in
                        guard let uid = validatedMutationUid() else { return }
                        guard let selectedStamp = stamps.first(where: { $0.id == stampId }) else {
                            triggerToast("Không tìm thấy con tem đã chọn.")
                            return
                        }
                        SupabaseMediaUploader.shared.ensureRemoteRenderedStamp(
                            ownerUid: uid,
                            localOrRemotePath: selectedStamp.stampImagePath
                        ) { uploadResult in
                            DispatchQueue.main.async {
                                switch uploadResult {
                                case .failure(let err):
                                    triggerToast("Lỗi tải ảnh tem lên Cloud: \(err.localizedDescription)")
                                case .success(let remoteUrl):
                                    let storageMediaPath: String = {
                                        if remoteUrl.contains("/stamp-media/") {
                                            return String(remoteUrl.components(separatedBy: "/stamp-media/").last ?? remoteUrl)
                                        }
                                        return remoteUrl
                                    }()
                                    friendRepo.createTradeRequest(
                                        recipientId: friend.id,
                                        stampId: selectedStamp.id,
                                        stampName: selectedStamp.title,
                                        stampMediaPath: storageMediaPath,
                                        stampCategory: selectedStamp.collectionId,
                                        stampSvg: nil,
                                        note: nil
                                    ) { tradeRes in
                                        DispatchQueue.main.async {
                                            switch tradeRes {
                                            case .success:
                                                showTradeModal = false
                                                triggerToast("Sent trade offer to \(friend.displayName)!")
                                            case .failure(let err):
                                                let errStr = err.localizedDescription
                                                if errStr.localizedCaseInsensitiveContains("RATE_LIMITED") || errStr.contains("429") {
                                                    triggerToast("You're doing that too quickly. Please try again shortly.")
                                                } else {
                                                    triggerToast("Lỗi tạo yêu cầu trao đổi: \(errStr)")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
        .sheet(item: $selectedFriendForChat) { friend in
            ChatScreenView(
                recipientUserId: friend.id,
                recipientName: friend.displayName,
                recipientIsOnline: friend.isOnline,
                currentUserId: currentUid,
                repository: repository,
                onDismiss: { selectedFriendForChat = nil }
            )
        }
        .sheet(isPresented: $showQrCodeModal) {
            FriendQrCodeSheetView(repository: repository)
        }
        .alert(isPresented: $showBlockAlert) {
            Alert(
                title: Text(langManager.localized("friends_block_confirm_title")),
                message: Text(langManager.localized("friends_block_confirm_message", friendToBlock?.displayName ?? "User")),
                primaryButton: .destructive(Text(langManager.localized("friends_block_btn"))) {
                    if let target = friendToBlock {
                        isSubmittingSafety = true
                        friendRepo.blockUser(blockedId: target.id) { result in
                            isSubmittingSafety = false
                            switch result {
                            case .success:
                                triggerToast("Đã chặn \(target.displayName).")
                            case .failure(let err):
                                triggerToast("Lỗi: \(err.localizedDescription)")
                            }
                        }
                    }
                },
                secondaryButton: .cancel(Text(langManager.localized("common_cancel")))
            )
        }
        .sheet(isPresented: $showReportSheet) {
            if let target = friendToReport {
                NavigationView {
                    Form {
                        Section(header: Text(langManager.localized("friends_report_reason_section"))) {
                            Picker(langManager.localized("friends_report_category"), selection: $reportCategory) {
                                Text(langManager.localized("report_category_spam")).tag("spam")
                                Text(langManager.localized("report_category_harassment")).tag("harassment")
                                Text(langManager.localized("report_category_fraud")).tag("impersonation")
                                Text(langManager.localized("report_category_inappropriate")).tag("inappropriate_content")
                                Text(langManager.localized("report_category_other")).tag("other")
                            }
                        }

                        Section(header: Text(langManager.localized("friends_report_notes_section"))) {
                            TextEditor(text: $reportNote)
                                .frame(height: 80)
                        }

                        Section {
                            Button(action: {
                                isSubmittingSafety = true
                                friendRepo.reportUser(reportedUserId: target.id, category: reportCategory, note: reportNote) { result in
                                    isSubmittingSafety = false
                                    showReportSheet = false
                                    switch result {
                                    case .success:
                                        triggerToast("Báo cáo đã được ghi nhận. Cảm ơn đóng góp của bạn.")
                                    case .failure(let err):
                                        triggerToast("Lỗi báo cáo: \(err.localizedDescription)")
                                    }
                                }
                            }) {
                                HStack {
                                    Spacer()
                                    Text(langManager.localized("friends_report_submit"))
                                        .font(.headline.bold())
                                        .foregroundColor(Color.red)
                                    Spacer()
                                }
                            }
                            .disabled(isSubmittingSafety)
                        }
                    }
                    .navigationTitle(langManager.localized("friends_report_title"))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button(langManager.localized("common_close")) {
                                showReportSheet = false
                            }
                        }
                    }
                }
            }
        }
        .onAppear {
            friendRepo.loadCloudData()
            if let uid = authenticatedUid {
                chatRepo.onUserChanged(newUserId: uid)
                for friend in friendRepo.friends {
                    chatRepo.loadConversation(otherUserId: friend.id)
                }
            } else {
                chatRepo.onLogout()
            }
        }
    }

    // MARK: - Tab Content Subviews
    @ViewBuilder
    private var friendsTabContent: some View {
        // Incoming Friend Requests Notification Section
        if !incomingFriendRequests.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "envelope.badge.fill")
                        .foregroundColor(MSColors.stamp)
                    Text("LỜI MỜI KẾT BẠN MỚI (\(incomingFriendRequests.count))")
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                }
                .padding(.horizontal, 4)

                ForEach(incomingFriendRequests, id: \.id) { req in
                    HStack(spacing: 10) {
                        AsyncImage(url: URL(string: req.senderAvatar)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(MSColors.stamp.opacity(0.15))
                            }
                        }
                        .frame(width: 40, height: 40)
                        .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 2) {
                            Text(req.senderName)
                                .font(.subheadline.bold())
                                .foregroundColor(MSColors.ink)
                            Text("@" + req.senderUsername)
                                .font(.caption)
                                .foregroundColor(MSColors.grey)
                        }

                        Button(action: {
                            friendRepo.acceptRequest(requestId: req.id) { result in
                                switch result {
                                case .success:
                                    triggerToast("Đã đồng ý kết bạn với \(req.senderName)!")
                                case .failure(let err):
                                    triggerToast("Lỗi: \(err.localizedDescription)")
                                }
                            }
                        }) {
                            Text("Chấp nhận")
                                .font(.caption.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                        }

                        Button(action: {
                            friendRepo.declineRequest(requestId: req.id) { result in
                                switch result {
                                case .success:
                                    triggerToast("Đã từ chối lời mời kết bạn.")
                                case .failure(let err):
                                    triggerToast("Lỗi: \(err.localizedDescription)")
                                }
                            }
                        }) {
                            Text("Từ chối")
                                .font(.caption.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.gray.opacity(0.15))
                                .foregroundColor(MSColors.ink)
                                .cornerRadius(10)
                        }
                    }
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(14)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1))
                }
            }
            .padding(.bottom, 8)
        }

        // Outgoing Friend Requests Section
        if !outgoingFriendRequests.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(MSColors.grey)
                    Text("LỜI MỜI ĐÃ GỬI (\(outgoingFriendRequests.count))")
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                }
                .padding(.horizontal, 4)

                ForEach(outgoingFriendRequests, id: \.id) { req in
                    HStack(spacing: 10) {
                        Circle().fill(MSColors.stamp.opacity(0.15))
                            .frame(width: 40, height: 40)
                            .overlay(
                                Text("@")
                                    .font(.caption.bold())
                                    .foregroundColor(MSColors.stamp)
                            )

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Đã gửi lời mời tới @\(req.recipientUsername.isEmpty ? req.senderUsername : req.recipientUsername)")
                                .font(.subheadline.bold())
                                .foregroundColor(MSColors.ink)
                            Text("Đang chờ phản hồi...")
                                .font(.caption)
                                .foregroundColor(MSColors.grey)
                        }

                        Spacer()

                        Button(action: {
                            friendRepo.cancelRequest(requestId: req.id) { result in
                                switch result {
                                case .success:
                                    triggerToast("Đã hủy lời mời kết bạn.")
                                case .failure(let err):
                                    triggerToast("Lỗi: \(err.localizedDescription)")
                                }
                            }
                        }) {
                            Text("Hủy lời mời")
                                .font(.caption.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.red.opacity(0.12))
                                .foregroundColor(.red)
                                .cornerRadius(10)
                        }
                    }
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(14)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(MSColors.lightGrey, lineWidth: 1))
                }
            }
            .padding(.bottom, 8)
        }

        // Friends List
        if friends.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "person.2.slash")
                    .font(.system(size: 38))
                    .foregroundColor(MSColors.stamp.opacity(0.6))
                Text("Chưa có bạn bè nào")
                    .font(.headline)
                    .foregroundColor(MSColors.ink)
                Text("Nhập mã kết bạn ở trên để giao lưu tem.")
                    .font(.caption)
                    .foregroundColor(MSColors.grey)
            }
            .padding(.top, 40)
        } else {
            ForEach(friends, id: \.id) { friend in
                HStack(spacing: 12) {
                    ZStack(alignment: .bottomTrailing) {
                        if !friend.avatarUrl.isEmpty && friend.avatarUrl.contains("http") {
                            AsyncImage(url: URL(string: friend.avatarUrl)) { phase in
                                if let img = phase.image {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    ZStack {
                                        Circle().fill(MSColors.stamp.opacity(0.15))
                                        Text(String(friend.displayName.prefix(1)).uppercased())
                                            .font(.headline.bold())
                                            .foregroundColor(MSColors.stamp)
                                    }
                                }
                            }
                            .frame(width: 48, height: 48)
                            .clipShape(Circle())
                        } else {
                            ZStack {
                                Circle().fill(MSColors.stamp.opacity(0.15))
                                Text(String(friend.displayName.prefix(1)).uppercased())
                                    .font(.headline.bold())
                                    .foregroundColor(MSColors.stamp)
                            }
                            .frame(width: 48, height: 48)
                        }

                        if friend.isOnline {
                            Circle()
                                .fill(Color.green)
                                .frame(width: 12, height: 12)
                                .overlay(Circle().stroke(Color.white, lineWidth: 2))
                        }
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        Text(friend.displayName)
                            .font(.subheadline.bold())
                            .foregroundColor(MSColors.ink)
                        Text("@" + friend.username + " • \(friend.tradeCount) trao đổi")
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                    }

                    Spacer()

                    HStack(spacing: 8) {
                        // Direct Chat Button
                        Button(action: {
                            guard authenticatedUid != nil else {
                                triggerToast("Bạn cần đăng nhập để trò chuyện.")
                                return
                            }
                            selectedFriendForChat = friend
                            showChatModal = true
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "bubble.left.and.bubble.right.fill")
                                    .font(.system(size: 11))
                                Text("Chat")
                                    .font(.caption.bold())
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(MSColors.stamp.opacity(0.12))
                            .foregroundColor(MSColors.stamp)
                            .cornerRadius(14)
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(MSColors.stamp.opacity(0.3), lineWidth: 1))
                        }

                        // Trade Button
                        Button(action: {
                            guard authenticatedUid != nil else {
                                triggerToast("Bạn cần đăng nhập để thực hiện giao dịch.")
                                return
                            }
                            selectedFriendForTrade = friend
                            showTradeModal = true
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.system(size: 11))
                                Text("Trade")
                                    .font(.caption.bold())
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(MSColors.gold.opacity(0.18))
                            .foregroundColor(Color(red: 0.70, green: 0.50, blue: 0.10))
                            .cornerRadius(14)
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(MSColors.gold.opacity(0.4), lineWidth: 1))
                        }

                        Menu {
                            Button(role: .destructive, action: {
                                friendToBlock = friend
                                showBlockAlert = true
                            }) {
                                Label(langManager.localized("friends_block_user_menu"), systemImage: "hand.raised.slash")
                            }

                            Button(action: {
                                friendToReport = friend
                                reportCategory = "spam"
                                reportNote = ""
                                showReportSheet = true
                            }) {
                                Label(langManager.localized("friends_report_abuse_menu"), systemImage: "exclamationmark.bubble")
                            }

                            Divider()

                            Button(role: .destructive, action: {
                                friendRepo.unfriendUser(friendId: friend.id) { result in
                                    switch result {
                                    case .success:
                                        triggerToast("Đã xóa \(friend.displayName) khỏi danh sách.")
                                    case .failure(let err):
                                        triggerToast("Lỗi: \(err.localizedDescription)")
                                    }
                                }
                            }) {
                                Label(langManager.localized("friends_unfriend_btn"), systemImage: "person.badge.minus")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.system(size: 16))
                                .foregroundColor(MSColors.stamp)
                                .padding(6)
                        }
                    }
                    .fixedSize(horizontal: true, vertical: false)
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(16)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(MSColors.lightGrey, lineWidth: 1))
                .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
            }
        }
    }

    @ViewBuilder
    private var tradeRequestsTabContent: some View {
        // Trade Requests
        if incomingTradeRequests.isEmpty && outgoingTradeRequests.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 38))
                    .foregroundColor(MSColors.gold.opacity(0.6))
                Text("No active trade requests")
                    .font(.headline)
                    .foregroundColor(.secondary)
            }
            .padding(.top, 40)
        } else {
            if !incomingTradeRequests.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("YÊU CẦU TRAO ĐỔI NHẬN ĐƯỢC (\(incomingTradeRequests.count))")
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                        .padding(.horizontal, 4)

                    ForEach(incomingTradeRequests, id: \.id) { trade in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(trade.senderDisplayName.isEmpty ? trade.senderUsername : trade.senderDisplayName)
                                    .font(.subheadline.bold())
                                    .foregroundColor(MSColors.ink)
                                Text("gửi lời đề nghị trao đổi tem!")
                                    .font(.subheadline)
                                    .foregroundColor(MSColors.grey)
                                Spacer()
                                Text(trade.status)
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(trade.status == "ACCEPTED" ? Color.green.opacity(0.2) : (trade.status == "DECLINED" || trade.status == "CANCELLED" ? Color.red.opacity(0.15) : MSColors.gold.opacity(0.2)))
                                    .foregroundColor(trade.status == "ACCEPTED" ? .green : (trade.status == "DECLINED" || trade.status == "CANCELLED" ? .red : MSColors.gold))
                                    .cornerRadius(8)
                            }

                            HStack(spacing: 12) {
                                let mediaUrl = trade.stampMediaPath.hasPrefix("http") ? trade.stampMediaPath : "\(SupabaseSocialClient.shared.supabaseUrl)/storage/v1/object/public/stamp-media/\(trade.stampMediaPath)"
                                MemoStampImageView(urlString: mediaUrl) {
                                    MSColors.lightGrey
                                }
                                .frame(width: 60, height: 60)
                                .cornerRadius(8)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(trade.stampName)
                                        .font(.subheadline.bold())
                                        .foregroundColor(MSColors.ink)
                                    Text("Bộ sưu tập độc bản #2026")
                                        .font(.caption)
                                        .foregroundColor(MSColors.grey)

                                    if trade.status == "PENDING" {
                                        HStack(spacing: 8) {
                                            Button(action: {
                                                guard validatedMutationUid() != nil else { return }
                                                friendRepo.acceptTrade(tradeId: trade.id) { result in
                                                    switch result {
                                                    case .success:
                                                        triggerToast("Đã chấp nhận đề nghị trao đổi")
                                                    case .failure(let err):
                                                        triggerToast("Lỗi: \(err.localizedDescription)")
                                                    }
                                                }
                                            }) {
                                                Text("Chấp nhận")
                                                    .font(.caption.bold())
                                                    .frame(maxWidth: .infinity)
                                                    .padding(.vertical, 8)
                                                    .background(MSColors.stamp)
                                                    .foregroundColor(.white)
                                                    .cornerRadius(10)
                                            }

                                            Button(action: {
                                                guard validatedMutationUid() != nil else { return }
                                                friendRepo.declineTrade(tradeId: trade.id) { result in
                                                    switch result {
                                                    case .success:
                                                        triggerToast("Đã từ chối đề nghị trao đổi.")
                                                    case .failure(let err):
                                                        triggerToast("Lỗi: \(err.localizedDescription)")
                                                    }
                                                }
                                            }) {
                                                Text("Từ chối")
                                                    .font(.caption.bold())
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 8)
                                                    .background(Color.gray.opacity(0.15))
                                                    .foregroundColor(.gray)
                                                    .cornerRadius(10)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(16)
                    }
                }
            }

            if !receivedTradeStamps.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("TEM KỶ NIỆM ĐÃ NHẬN QUA TRAO ĐỔI (\(receivedTradeStamps.count))")
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                        .padding(.horizontal, 4)

                    ForEach(receivedTradeStamps, id: \.id) { rStamp in
                        HStack(spacing: 12) {
                            let mediaUrl = rStamp.mediaPath.hasPrefix("http") ? rStamp.mediaPath : "\(SupabaseSocialClient.shared.supabaseUrl)/storage/v1/object/public/stamp-media/\(rStamp.mediaPath)"
                            MemoStampImageView(urlString: mediaUrl) {
                                MSColors.lightGrey
                            }
                            .frame(width: 56, height: 56)
                            .cornerRadius(8)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(rStamp.stampName)
                                    .font(.subheadline.bold())
                                    .foregroundColor(MSColors.ink)
                                Text("Đã lưu vĩnh viễn trong Kho tem")
                                    .font(.caption2)
                                    .foregroundColor(.green)
                                Text("Độc bản giao lưu qua Supabase Cloud")
                                    .font(.caption2)
                                    .foregroundColor(MSColors.grey)
                            }

                            Spacer()

                            HStack(spacing: 4) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 10, weight: .bold))
                                Text("Đã sở hữu")
                            }
                            .font(.caption2.bold())
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(MSColors.stamp.opacity(0.12))
                            .foregroundColor(MSColors.stamp)
                            .cornerRadius(8)
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(16)
                    }
                }
            }

            if !outgoingTradeRequests.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("YÊU CẦU TRAO ĐỔI ĐÃ GỬI (\(outgoingTradeRequests.count))")
                        .font(.caption2.bold())
                        .foregroundColor(MSColors.grey)
                        .padding(.horizontal, 4)

                    ForEach(outgoingTradeRequests, id: \.id) { trade in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("Đã gửi tới \(trade.recipientDisplayName.isEmpty ? trade.recipientUsername : trade.recipientDisplayName)")
                                    .font(.subheadline.bold())
                                    .foregroundColor(MSColors.ink)
                                Spacer()
                                Text(trade.status)
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(trade.status == "ACCEPTED" ? Color.green.opacity(0.2) : (trade.status == "DECLINED" || trade.status == "CANCELLED" ? Color.red.opacity(0.15) : MSColors.gold.opacity(0.2)))
                                    .foregroundColor(trade.status == "ACCEPTED" ? .green : (trade.status == "DECLINED" || trade.status == "CANCELLED" ? .red : MSColors.gold))
                                    .cornerRadius(8)
                            }

                            HStack(spacing: 12) {
                                let mediaUrl = trade.stampMediaPath.hasPrefix("http") ? trade.stampMediaPath : "\(SupabaseSocialClient.shared.supabaseUrl)/storage/v1/object/public/stamp-media/\(trade.stampMediaPath)"
                                MemoStampImageView(urlString: mediaUrl) {
                                    MSColors.lightGrey
                                }
                                .frame(width: 60, height: 60)
                                .cornerRadius(8)

                                VStack(alignment: .leading, spacing: 4) {
                                    Text(trade.stampName)
                                        .font(.subheadline.bold())
                                        .foregroundColor(MSColors.ink)
                                    Text(trade.status == "PENDING" ? "Đang chờ bạn bè xác nhận..." : (trade.status == "ACCEPTED" ? "Đã được chấp nhận" : "Đã kết thúc"))
                                        .font(.caption)
                                        .foregroundColor(MSColors.grey)
                                }

                                Spacer()

                                if trade.status == "PENDING" {
                                    Button(action: {
                                        guard validatedMutationUid() != nil else { return }
                                        friendRepo.cancelTrade(tradeId: trade.id) { result in
                                            switch result {
                                            case .success:
                                                triggerToast("Đã hủy yêu cầu trao đổi.")
                                            case .failure(let err):
                                                triggerToast("Lỗi: \(err.localizedDescription)")
                                            }
                                        }
                                    }) {
                                        Text("Hủy yêu cầu")
                                            .font(.caption.bold())
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(Color.red.opacity(0.12))
                                            .foregroundColor(.red)
                                            .cornerRadius(10)
                                    }
                                }
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(16)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var directChatTabContent: some View {
        let conversationList = chatRepo.getConversationList(friends: friends)
        if conversationList.isEmpty {
            VStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(MSColors.stamp.opacity(0.12))
                        .frame(width: 64, height: 64)
                    Image(systemName: "bubble.left.and.bubble.right.fill")
                        .font(.system(size: 28))
                        .foregroundColor(MSColors.stamp)
                }
                Text(langManager.localized("chat_conversations_empty_title"))
                    .font(.headline.bold())
                    .foregroundColor(MSColors.ink)
                Text(langManager.localized("chat_conversations_empty_desc"))
                    .font(.caption)
                    .foregroundColor(MSColors.grey)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                Button(action: { selectedTab = 0 }) {
                    HStack(spacing: 6) {
                        Image(systemName: "person.2.fill")
                            .font(.caption.bold())
                        Text(langManager.localized("chat_view_friends_btn"))
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(MSColors.stamp)
                    .foregroundColor(.white)
                    .cornerRadius(16)
                }
                .padding(.top, 4)
            }
            .padding(.top, 40)
            .padding(.horizontal, 24)
        } else {
            ForEach(conversationList) { conv in
                let friend = conv.otherUser
                let lastMsg = conv.lastMessage
                let isMe = lastMsg?.isMe ?? false

                HStack(spacing: 12) {
                    ZStack(alignment: .bottomTrailing) {
                        AsyncImage(url: URL(string: friend.avatarUrl)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(MSColors.lightGrey)
                            }
                        }
                        .frame(width: 48, height: 48)
                        .clipShape(Circle())

                        if friend.isOnline {
                            Circle()
                                .fill(Color.green)
                                .frame(width: 10, height: 10)
                                .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                        }
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(friend.displayName)
                                .font(.subheadline.bold())
                                .foregroundColor(MSColors.ink)
                            Spacer()
                            if let last = lastMsg {
                                Text(formattedTime(last.createdAt))
                                    .font(.caption2)
                                    .foregroundColor(MSColors.grey)
                            }
                        }

                        HStack {
                            let basePreview: String = {
                                guard let msg = lastMsg else {
                                    return langManager.localized("chat_tap_to_message")
                                }
                                if msg.stamp != nil || isValidRemoteStampUrl(msg.stampUrl) {
                                    return "[Tem: \(msg.stampTitle ?? "Kỷ niệm")] \(msg.text)"
                                }
                                return msg.text
                            }()
                            let displayText = isMe ? "Bạn: \(basePreview)" : basePreview

                            Text(displayText)
                                .font(.caption)
                                .foregroundColor(conv.unreadCount > 0 ? MSColors.ink : MSColors.grey)
                                .fontWeight(conv.unreadCount > 0 ? .bold : .regular)
                                .lineLimit(1)

                            Spacer()

                            if isMe, let last = lastMsg {
                                if last.isRead {
                                    Text("Đã xem ✓✓")
                                        .font(.caption2.bold())
                                        .foregroundColor(MSColors.stamp)
                                } else {
                                    Text("Đã gửi ✓")
                                        .font(.caption2)
                                        .foregroundColor(MSColors.grey)
                                }
                            }

                            if conv.unreadCount > 0 {
                                Text("\(conv.unreadCount) mới")
                                    .font(.caption2.bold())
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 2)
                                    .background(MSColors.stamp)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                        }
                    }

                    Image(systemName: "chevron.right")
                        .font(.caption.bold())
                        .foregroundColor(MSColors.grey)
                }
                .padding(14)
                .background(Color.white)
                .cornerRadius(16)
                .onTapGesture {
                    guard authenticatedUid != nil else {
                        triggerToast("Bạn cần đăng nhập để trò chuyện.")
                        return
                    }
                    selectedFriendForChat = friend
                    showChatModal = true
                }
            }
        }
    }

    @ViewBuilder
    private var receivedStampsInboxTabContent: some View {
        if visibleReceivedStamps.isEmpty && receivedTradeStamps.isEmpty {
            receivedStampsEmptyView
        } else {
            VStack(spacing: 12) {
                if !receivedTradeStamps.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("TEM KỶ NIỆM ĐÃ NHẬN TỪ TRAO ĐỔI (\(receivedTradeStamps.count))")
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.grey)
                            .padding(.horizontal, 4)

                        ForEach(receivedTradeStamps, id: \.id) { rStamp in
                            HStack(spacing: 12) {
                                let mediaUrl = rStamp.mediaPath.hasPrefix("http") ? rStamp.mediaPath : "\(SupabaseSocialClient.shared.supabaseUrl)/storage/v1/object/public/stamp-media/\(rStamp.mediaPath)"
                                MemoStampImageView(urlString: mediaUrl) {
                                    MSColors.lightGrey
                                }
                                .frame(width: 56, height: 56)
                                .cornerRadius(8)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(rStamp.stampName)
                                        .font(.subheadline.bold())
                                        .foregroundColor(MSColors.ink)
                                    Text("Đã lưu vĩnh viễn trong Kho tem")
                                        .font(.caption2)
                                        .foregroundColor(.green)
                                    Text("Độc bản giao lưu qua Supabase Cloud")
                                        .font(.caption2)
                                        .foregroundColor(MSColors.grey)
                                }

                                Spacer()

                                HStack(spacing: 4) {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 10, weight: .bold))
                                    Text("Đã sở hữu")
                                }
                                .font(.caption2.bold())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(MSColors.stamp.opacity(0.12))
                                .foregroundColor(MSColors.stamp)
                                .cornerRadius(8)
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(16)
                        }
                    }
                }

                if !visibleReceivedStamps.isEmpty {
                    receivedStampsListView
                }
            }
        }
    }

    @ViewBuilder
    private var receivedStampsEmptyView: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(MSColors.stamp.opacity(0.12))
                    .frame(width: 64, height: 64)
                Image(systemName: "envelope.open.fill")
                    .font(.system(size: 28))
                    .foregroundColor(MSColors.stamp)
            }
            Text(langManager.localized("inbox_empty_title"))
                .font(.headline.bold())
                .foregroundColor(MSColors.ink)
            Text(langManager.localized("inbox_empty_subtitle"))
                .font(.caption)
                .foregroundColor(MSColors.grey)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        }
        .padding(.top, 40)
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private var receivedStampsListView: some View {
        ForEach(visibleReceivedStamps, id: \.id) { msg in
            inboxStampRow(msg)
        }
    }

    @ViewBuilder
    private func inboxStampRow(_ msg: ChatMessage) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Sender info
            HStack(spacing: 10) {
                if isValidRemoteStampUrl(msg.senderAvatar) {
                    AsyncImage(url: URL(string: msg.senderAvatar)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            Circle().fill(MSColors.stamp.opacity(0.15))
                        }
                    }
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())
                } else {
                    Circle().fill(MSColors.stamp.opacity(0.15))
                        .frame(width: 36, height: 36)
                        .overlay(
                            Text(String(msg.senderName.prefix(1)).uppercased())
                                .font(.caption.bold())
                                .foregroundColor(MSColors.stamp)
                        )
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(msg.senderName)
                        .font(.subheadline.bold())
                        .foregroundColor(MSColors.ink)
                    Text(formattedTimestamp(msg.createdAt))
                        .font(.caption2)
                        .foregroundColor(MSColors.grey)
                }

                Spacer()

                HStack(spacing: 4) {
                    MemoStampIcon(key: .stamp, size: 12, color: MSColors.stamp)
                    Text("Tem kỷ niệm")
                }
                .font(.caption2.bold())
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(MSColors.stamp.opacity(0.12))
                .foregroundColor(MSColors.stamp)
                .cornerRadius(8)
            }

            // Card Body: Stamp Preview & Metadata
            HStack(spacing: 12) {
                if let stamp = msg.stamp, isValidRemoteStampUrl(stamp.stampImagePath) {
                    AsyncImage(url: URL(string: stamp.stampImagePath)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fill)
                        } else {
                            ZStack {
                                Color.gray.opacity(0.1)
                                MemoStampIcon(key: .stamp, size: 28, color: MSColors.stamp)
                            }
                        }
                    }
                    .frame(width: 64, height: 64)
                    .cornerRadius(8)
                } else {
                    ZStack {
                        MSColors.stamp.opacity(0.1)
                        MemoStampIcon(key: .stamp, size: 28, color: MSColors.stamp)
                    }
                    .frame(width: 64, height: 64)
                    .cornerRadius(8)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(msg.stamp?.title.isEmpty == false ? (msg.stamp?.title ?? "") : "Tem thư kỷ niệm")
                        .font(.subheadline.bold())
                        .foregroundColor(MSColors.ink)
                    HStack(spacing: 3) {
                        MemoStampIcon(key: .location, size: 12, color: Color.blue)
                        Text(msg.stamp?.location ?? "Việt Nam")
                    }
                    .font(.caption.bold())
                    .foregroundColor(Color.blue)
                    if !msg.text.isEmpty && !msg.text.hasPrefix("📮 Đã gửi con tem") && !msg.text.hasPrefix("Đã gửi con tem") {
                        Text("“\(msg.text)”")
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                            .lineLimit(2)
                    }
                }
            }
            .padding(10)
            .background(MSColors.paper)
            .cornerRadius(12)

            // Actions: Từ chối, Nhắn tin, Lưu vào Kho
            HStack(spacing: 8) {
                Spacer()

                Button(action: {
                    guard authenticatedUid != nil else {
                        triggerToast("Bạn cần đăng nhập để thực hiện thao tác này.")
                        return
                    }
                    markInboxItemProcessed(for: currentUid, messageId: msg.id)
                    triggerToast("Đã từ chối con tem này")
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark")
                            .font(.system(size: 11, weight: .bold))
                        Text("Từ chối")
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.gray.opacity(0.12))
                    .foregroundColor(MSColors.grey)
                    .cornerRadius(10)
                }

                Button(action: {
                    guard authenticatedUid != nil else {
                        triggerToast("Bạn cần đăng nhập để trò chuyện.")
                        return
                    }
                    let friend = friends.first(where: { $0.id == msg.senderId }) ?? FriendItem(id: msg.senderId, displayName: msg.senderName, username: msg.senderName, avatarUrl: msg.senderAvatar, isOnline: false, tradeCount: 0)
                    selectedFriendForChat = friend
                    showChatModal = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .font(.system(size: 11))
                        Text("Nhắn tin")
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(MSColors.stamp.opacity(0.12))
                    .foregroundColor(MSColors.stamp)
                    .cornerRadius(10)
                }

                Button(action: {
                    guard let uid = validatedMutationUid() else { return }
                    if let stamp = msg.stamp {
                        let validRemoteUrl = isValidRemoteStampUrl(stamp.stampImagePath) ? stamp.stampImagePath : ""
                        let previousStamps = (repository.stamps.value as? [StampItem]) ?? []
                        let previousProfile = repository.currentUser.value as? UserProfile

                        _ = repository.addStamp(
                            title: stamp.title.isEmpty ? "Tem từ \(msg.senderName)" : stamp.title,
                            note: msg.text,
                            location: stamp.location?.isEmpty == false ? stamp.location : "Việt Nam",
                            imageUrl: validRemoteUrl,
                            originalImageUrl: validRemoteUrl,
                            shape: "classic",
                            collectionId: nil,
                            audience: AudienceType.friends,
                            mood: "happy",
                            memoryDate: msg.createdAt
                        )
                        let persisted = IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: uid)
                        if persisted {
                            markInboxItemProcessed(for: uid, messageId: msg.id)
                            triggerToast("Đã lưu con tem vào Kho của bạn thành công!")
                        } else {
                            repository.restoreStamps(stamps: previousStamps)
                            if let prev = previousProfile {
                                repository.setCurrentUser(profile: prev)
                            }
                            triggerToast("Lỗi lưu dữ liệu. Vui lòng thử lại.")
                        }
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "square.and.arrow.down.fill")
                            .font(.system(size: 11))
                        Text("Lưu vào Kho")
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(MSColors.lightGrey, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
    }

    private func triggerToast(_ msg: String) {
        toastMessage = msg
        withAnimation(.easeInOut(duration: 0.2)) {
            showToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
            withAnimation(.easeInOut(duration: 0.2)) {
                showToast = false
            }
        }
    }
}

struct TradeStampModalView: View {
    let friend: FriendItem
    let stamps: [StampItem]
    let onSendTrade: (String) -> Void
    @Environment(\.presentationMode) var presentationMode
    @State private var selectedStampId: String = ""

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("Trade Stamp with \(friend.displayName)")
                .font(.headline.bold())

            Text("Select one of your stamps to offer for trade:")
                .font(.caption)
                .foregroundColor(.secondary)

            ScrollView {
                VStack(spacing: 10) {
                    ForEach(stamps, id: \.id) { stamp in
                        HStack {
                            AsyncImage(url: URL(string: stamp.stampImagePath)) { phase in
                                if let img = phase.image { img.resizable().aspectRatio(contentMode: .fill) }
                                else { Color.gray.opacity(0.2) }
                            }
                            .frame(width: 50, height: 50)
                            .cornerRadius(8)

                            Text(stamp.title)
                                .font(.subheadline.bold())

                            Spacer()

                            if selectedStampId == stamp.id {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                            }
                        }
                        .padding(10)
                        .background(selectedStampId == stamp.id ? Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.1) : Color.white)
                        .cornerRadius(12)
                        .onTapGesture {
                            selectedStampId = stamp.id
                        }
                    }
                }
                .padding(.horizontal)
            }

            Button(action: {
                if !selectedStampId.isEmpty {
                    onSendTrade(selectedStampId)
                }
            }) {
                Text("Send Trade Request")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(selectedStampId.isEmpty ? Color.gray : Color(red: 0.85, green: 0.25, blue: 0.20))
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .padding(.horizontal)
            .padding(.bottom, 20)
        }
    }
}

// Subview: Personal Friend QR Code Sheet
struct FriendQrCodeSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode
    @ObservedObject private var friendRepo = IOSFriendRepository.shared
    @State private var scannedCode: String = ""
    @State private var toastMsg: String? = nil

    private var authenticatedUid: String? {
        let uid = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(uid) else {
            return nil
        }
        return uid
    }

    var user: UserProfile {
        if let authUid = authenticatedUid,
           let repoUser = repository.currentUser.value as? UserProfile,
           repoUser.uid == authUid {
            return repoUser
        }
        return UserProfile(
            uid: "",
            username: "user",
            displayName: "MemoStamp User",
            avatarUrl: nil,
            bio: "",
            stampsCreatedCount: 0,
            stampsCollectedCount: 0,
            placesVisitedCount: 0
        )
    }

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("MÃ QR TÀI KHOẢN")
                .font(.headline.bold())
                .foregroundColor(MSColors.ink)

            VStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white)
                        .frame(width: 200, height: 200)
                        .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 4)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(MSColors.stamp.opacity(0.3), lineWidth: 2)
                        )

                    VStack(spacing: 8) {
                        Image(systemName: "qrcode")
                            .font(.system(size: 130))
                            .foregroundColor(MSColors.ink)

                        Text("#STAMP_\(user.username.uppercased())")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.stamp)
                    }
                }

                Text(user.displayName)
                    .font(.title3.bold())
                    .foregroundColor(MSColors.ink)

                Text("@\(user.username)")
                    .font(.subheadline)
                    .foregroundColor(MSColors.grey)
            }

            Divider().padding(.horizontal)

            // Functional QR Scanner / Friend Invitation Input Field
            VStack(alignment: .leading, spacing: 8) {
                Text("QUÉT / NHẬP MÃ QR NGƯỜI KHÁC")
                    .font(.caption2.bold())
                    .foregroundColor(MSColors.grey)

                HStack(spacing: 8) {
                    Image(systemName: "qrcode.viewfinder")
                        .foregroundColor(MSColors.stamp)
                    TextField("Dán mã QR hoặc Username", text: $scannedCode)
                        .font(.subheadline)
                        .foregroundColor(MSColors.ink)
                    Button(action: {
                        guard authenticatedUid != nil else {
                            toastMsg = "Bạn cần đăng nhập để gửi lời mời kết bạn."
                            return
                        }
                        friendRepo.sendFriendRequestByUsernameOrId(input: scannedCode) { result in
                            switch result {
                            case .success(let msg):
                                toastMsg = msg
                                scannedCode = ""
                            case .failure(let err):
                                toastMsg = err.localizedDescription
                            }
                        }
                    }) {
                        Text("Kết Bạn")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(scannedCode.isEmpty ? Color.gray.opacity(0.3) : MSColors.stamp)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                    .disabled(scannedCode.isEmpty)
                }
                .padding(10)
                .background(Color.white)
                .cornerRadius(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(MSColors.lightGrey, lineWidth: 1))

                if let toast = toastMsg {
                    Text(toast)
                        .font(.caption.bold())
                        .foregroundColor(toast.contains("Đã gửi") ? Color.green : Color.red)
                }
            }
            .padding(.horizontal, 24)

            Spacer()

            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                Text("Đóng")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(MSColors.stamp)
                    .foregroundColor(.white)
                    .cornerRadius(14)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .background(MSColors.paper.ignoresSafeArea())
    }
}
