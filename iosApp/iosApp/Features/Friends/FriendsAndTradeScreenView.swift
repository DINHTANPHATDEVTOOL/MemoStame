import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct FriendsAndTradeScreenView: View {
    let repository: SharedMemoStampRepository

    @State private var friendCode: String = ""
    @State private var selectedTab: Int = 0 // 0: Friends, 1: Trade Requests
    @State private var selectedFriendForTrade: FriendItem? = nil
    @State private var selectedFriendForChat: FriendItem? = nil
    @State private var showTradeModal: Bool = false
    @State private var showChatModal: Bool = false
    @State private var showQrCodeModal: Bool = false
    @State private var toastMessage: String? = nil
    @State private var showToast: Bool = false
    @State private var refreshTrigger: Bool = false
    @StateObject private var langManager = AppLanguageManager.shared

    private var currentUid: String {
        (repository.currentUser.value as? UserProfile)?.uid ?? "user_me"
    }

    var allFriendRequests: [FriendRequestItem] {
        _ = refreshTrigger
        return (repository.friendRequests.value as? [FriendRequestItem]) ?? []
    }

    var incomingFriendRequests: [FriendRequestItem] {
        allFriendRequests.filter { req in
            req.recipientId.isEmpty || req.recipientId == currentUid
        }
    }

    var outgoingFriendRequests: [FriendRequestItem] {
        allFriendRequests.filter { req in
            req.senderId == currentUid && req.recipientId != currentUid
        }
    }

    var friends: [FriendItem] {
        _ = refreshTrigger
        return (repository.friends.value as? [FriendItem]) ?? []
    }

    var allTradeRequests: [TradeRequest] {
        _ = refreshTrigger
        return (repository.tradeRequests.value as? [TradeRequest]) ?? []
    }

    var incomingTradeRequests: [TradeRequest] {
        allTradeRequests.filter { trade in
            trade.recipientId.isEmpty || trade.recipientId == currentUid
        }
    }

    var outgoingTradeRequests: [TradeRequest] {
        allTradeRequests.filter { trade in
            trade.senderId == currentUid && trade.recipientId != currentUid
        }
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
                            Text(langManager.string(vi: "BẠN BÈ & TRAO ĐỔI TEM", en: "FRIENDS & STAMP TRADE"))
                                .font(.title2.bold())
                                .foregroundColor(MSColors.ink)
                            Text(langManager.string(vi: "Chia sẻ & giao lưu tem bưu chính độc bản", en: "Share & exchange vintage stamps"))
                                .font(.caption)
                                .foregroundColor(MSColors.grey)
                        }
                        Spacer()

                        // QR Code Profile Button
                        Button(action: { showQrCodeModal = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode")
                                    .font(.system(size: 16, weight: .bold))
                                Text(langManager.string(vi: "Mã QR", en: "My QR"))
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
                        TextField(langManager.string(vi: "Nhập mã kết bạn (ví dụ #STAMP99 hoặc Tên)", en: "Enter Friend Code (e.g. #STAMP99 or Username)"), text: $friendCode)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                        Button(action: {
                            let result = repository.sendFriendRequest(usernameOrCode: friendCode)
                            triggerToast(result.message)
                            if result.success {
                                friendCode = ""
                                refreshTrigger.toggle()
                                IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                            }
                        }) {
                            Text(langManager.string(vi: "Gửi Mời", en: "Invite"))
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
                                Text("\(langManager.string(vi: "Bạn bè", en: "Friends")) (\(friends.count))")
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
                                Text("\(langManager.string(vi: "Trao đổi", en: "Trades")) (\(incomingTradeRequests.count))")
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
                                Text(langManager.string(vi: "Trò chuyện", en: "Chat"))
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
                    }
                    .padding(.top, 4)
                }
                .padding()

                Divider()

                ScrollView {
                    VStack(spacing: 12) {
                        if selectedTab == 0 {
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
                                                let success = repository.acceptFriendRequest(requestId: req.id)
                                                refreshTrigger.toggle()
                                                IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                if success {
                                                    triggerToast("Đã đồng ý kết bạn với \(req.senderName)! 🎉")
                                                } else {
                                                    triggerToast("Không có quyền chấp nhận lời mời này.")
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
                                                let success = repository.rejectFriendRequest(requestId: req.id)
                                                refreshTrigger.toggle()
                                                IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                if success {
                                                    triggerToast("Đã từ chối lời mời kết bạn.")
                                                } else {
                                                    triggerToast("Không thể thực hiện thao tác.")
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
                                                let success = repository.cancelOutgoingFriendRequest(requestId: req.id)
                                                refreshTrigger.toggle()
                                                IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                if success {
                                                    triggerToast("Đã hủy lời mời kết bạn.")
                                                } else {
                                                    triggerToast("Không thể hủy lời mời.")
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

                                            Button(action: {
                                                repository.removeFriend(friendId: friend.id)
                                                refreshTrigger.toggle()
                                                IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                triggerToast("Đã xóa \(friend.displayName) khỏi danh sách.")
                                            }) {
                                                Image(systemName: "person.badge.minus")
                                                    .font(.system(size: 14))
                                                    .foregroundColor(.gray.opacity(0.7))
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
                        } else if selectedTab == 1 {
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
                                                    Text(trade.senderName)
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
                                                        .background(trade.status == "ACCEPTED" ? Color.green.opacity(0.2) : (trade.status == "REJECTED" ? Color.red.opacity(0.15) : MSColors.gold.opacity(0.2)))
                                                        .foregroundColor(trade.status == "ACCEPTED" ? .green : (trade.status == "REJECTED" ? .red : MSColors.gold))
                                                        .cornerRadius(8)
                                                }

                                                HStack(spacing: 12) {
                                                    MemoStampImageView(urlString: trade.stampUrl) {
                                                        MSColors.lightGrey
                                                    }
                                                    .frame(width: 60, height: 60)
                                                    .cornerRadius(8)

                                                    VStack(alignment: .leading, spacing: 4) {
                                                        Text(trade.stampTitle)
                                                            .font(.subheadline.bold())
                                                            .foregroundColor(MSColors.ink)
                                                        Text("Bộ sưu tập độc bản #2026")
                                                            .font(.caption)
                                                            .foregroundColor(MSColors.grey)
                                                        Button(action: {
                                                            let success = repository.acceptTrade(tradeId: trade.id)
                                                            refreshTrigger.toggle()
                                                            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                            if success {
                                                                triggerToast("Đã chấp nhận đề nghị trao đổi")
                                                            } else {
                                                                triggerToast("Không có quyền chấp nhận yêu cầu này.")
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
                                                            let success = repository.rejectTrade(tradeId: trade.id)
                                                            refreshTrigger.toggle()
                                                            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                            if success {
                                                                triggerToast("Đã từ chối đề nghị trao đổi.")
                                                            } else {
                                                                triggerToast("Không thể thực hiện thao tác.")
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
                                                    Text("Đã gửi tới \(trade.recipientName.isEmpty ? "bạn bè" : trade.recipientName)")
                                                        .font(.subheadline.bold())
                                                        .foregroundColor(MSColors.ink)
                                                    Spacer()
                                                    Text(trade.status)
                                                        .font(.caption2.bold())
                                                        .padding(.horizontal, 8)
                                                        .padding(.vertical, 4)
                                                        .background(trade.status == "ACCEPTED" ? Color.green.opacity(0.2) : (trade.status == "REJECTED" ? Color.red.opacity(0.15) : MSColors.gold.opacity(0.2)))
                                                        .foregroundColor(trade.status == "ACCEPTED" ? .green : (trade.status == "REJECTED" ? .red : MSColors.gold))
                                                        .cornerRadius(8)
                                                }

                                                HStack(spacing: 12) {
                                                    MemoStampImageView(urlString: trade.stampUrl) {
                                                        MSColors.lightGrey
                                                    }
                                                    .frame(width: 60, height: 60)
                                                    .cornerRadius(8)

                                                    VStack(alignment: .leading, spacing: 4) {
                                                        Text(trade.stampTitle)
                                                            .font(.subheadline.bold())
                                                            .foregroundColor(MSColors.ink)
                                                        Text("Đang chờ bạn bè xác nhận...")
                                                            .font(.caption)
                                                            .foregroundColor(MSColors.grey)
                                                    }

                                                    Spacer()

                                                    if trade.status == "PENDING" {
                                                        Button(action: {
                                                            let success = repository.cancelOutgoingTrade(tradeId: trade.id)
                                                            refreshTrigger.toggle()
                                                            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                                                            if success {
                                                                triggerToast("Đã hủy yêu cầu trao đổi.")
                                                            } else {
                                                                triggerToast("Không thể hủy yêu cầu.")
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
                        } else {
                            // Tab 2: Direct Chat Conversations
                            if friends.isEmpty {
                                VStack(spacing: 10) {
                                    Image(systemName: "bubble.left.and.bubble.right")
                                        .font(.system(size: 38))
                                        .foregroundColor(MSColors.stamp.opacity(0.6))
                                    Text("Chưa có cuộc trò chuyện nào.")
                                        .font(.headline)
                                        .foregroundColor(.secondary)
                                }
                                .padding(.top, 40)
                            } else {
                                ForEach(friends, id: \.id) { friend in
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
                                                Text("Mới đây")
                                                    .font(.caption2)
                                                    .foregroundColor(MSColors.grey)
                                            }
                                            Text("Sẵn sàng nhắn tin và trao đổi tem kỷ niệm!")
                                                .font(.caption)
                                                .foregroundColor(MSColors.grey)
                                                .lineLimit(1)
                                        }

                                        Image(systemName: "chevron.right")
                                            .font(.caption.bold())
                                            .foregroundColor(MSColors.grey)
                                    }
                                    .padding(14)
                                    .background(Color.white)
                                    .cornerRadius(16)
                                    .onTapGesture {
                                        selectedFriendForChat = friend
                                        showChatModal = true
                                    }
                                }
                            }
                        }
                    }
                    .padding()
                    .padding(.bottom, 140)
                }
            }
            .background(MSColors.paper.ignoresSafeArea())

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
        .sheet(isPresented: $showTradeModal) {
            if let friend = selectedFriendForTrade {
                TradeStampModalView(
                    friend: friend,
                    stamps: stamps,
                    onSendTrade: { stampId in
                        let success = repository.sendTradeRequest(friendId: friend.id, stampId: stampId)
                        refreshTrigger.toggle()
                        IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: currentUid)
                        showTradeModal = false
                        if success {
                            triggerToast("Sent trade offer to \(friend.displayName)!")
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
    @State private var scannedCode: String = ""
    @State private var toastMsg: String? = nil

    var user: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "user_me",
            username: "user_memostamp",
            displayName: "MemoStamp Collector",
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
                        let result = repository.sendFriendRequest(usernameOrCode: scannedCode)
                        toastMsg = result.message
                        if result.success {
                            scannedCode = ""
                            IOSLocalPersistenceStore.shared.saveData(repository: repository, userId: user.uid)
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
