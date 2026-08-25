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
    @State private var showTradeModal: Bool = false
    @State private var showQrCodeModal: Bool = false
    @State private var toastMessage: String? = nil
    @State private var showToast: Bool = false
    @State private var refreshTrigger: Bool = false

    var friends: [FriendItem] {
        _ = refreshTrigger
        return (repository.friends.value as? [FriendItem]) ?? []
    }

    var tradeRequests: [TradeRequest] {
        _ = refreshTrigger
        return (repository.tradeRequests.value as? [TradeRequest]) ?? []
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
                            Text("FRIENDS & STAMP TRADE")
                                .font(.title2.bold())
                                .foregroundColor(MSColors.ink)
                            Text("Share & exchange vintage stamps")
                                .font(.caption)
                                .foregroundColor(MSColors.grey)
                        }
                        Spacer()

                        // QR Code Profile Button
                        Button(action: { showQrCodeModal = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode")
                                    .font(.system(size: 16, weight: .bold))
                                Text("My QR")
                                    .font(.caption.bold())
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(MSColors.stamp.opacity(0.12))
                            .foregroundColor(MSColors.stamp)
                            .cornerRadius(12)
                        }
                    }

                    // Search/Add Friend Code Input
                    HStack {
                        Image(systemName: "person.badge.plus")
                            .foregroundColor(MSColors.stamp)
                        TextField("Enter Friend Code (e.g. #STAMP99 or Username)", text: $friendCode)
                            .font(.subheadline)
                            .foregroundColor(MSColors.ink)
                        Button(action: {
                            let code = friendCode.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !code.isEmpty {
                                let name = code.replacingOccurrences(of: "#", with: "").capitalized
                                let username = code.lowercased().replacingOccurrences(of: " ", with: "_")
                                _ = repository.addFriend(displayName: name, username: username)
                                friendCode = ""
                                refreshTrigger.toggle()
                                triggerToast("Added \(name) to friends list! 🎉")
                            }
                        }) {
                            Text("Add")
                                .font(.caption.bold())
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(friendCode.isEmpty ? MSColors.lightGrey : MSColors.stamp)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                        .disabled(friendCode.isEmpty)
                    }
                    .padding(10)
                    .background(Color.white)
                    .cornerRadius(12)

                    // Segment Picker
                    Picker("", selection: $selectedTab) {
                        Text("My Friends (\(friends.count))").tag(0)
                        Text("Trade Requests (\(tradeRequests.count))").tag(1)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                }
                .padding()

                Divider()

                ScrollView {
                    if selectedTab == 0 {
                        // Friends List
                        VStack(spacing: 12) {
                            if friends.isEmpty {
                                VStack(spacing: 8) {
                                    Text("👥 No friends added yet")
                                        .font(.headline)
                                        .foregroundColor(.secondary)
                                    Text("Add friends using their friend code or username above.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
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
                                            .frame(width: 44, height: 44)
                                            .clipShape(Circle())

                                            if friend.isOnline {
                                                Circle()
                                                    .fill(Color.green)
                                                    .frame(width: 10, height: 10)
                                                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                                            }
                                        }

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(friend.displayName)
                                                .font(.subheadline.bold())
                                                .foregroundColor(MSColors.ink)
                                            Text("@" + friend.username + " • \(friend.tradeCount) trades")
                                                .font(.caption)
                                                .foregroundColor(MSColors.grey)
                                        }

                                        Spacer()

                                        HStack(spacing: 8) {
                                            Button(action: {
                                                selectedFriendForTrade = friend
                                                showTradeModal = true
                                            }) {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "arrow.triangle.2.circlepath")
                                                    Text("Trade")
                                                        .font(.caption.bold())
                                                }
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 6)
                                                .background(MSColors.gold.opacity(0.2))
                                                .foregroundColor(MSColors.gold)
                                                .cornerRadius(12)
                                            }

                                            Button(action: {
                                                repository.removeFriend(friendId: friend.id)
                                                refreshTrigger.toggle()
                                                triggerToast("Removed \(friend.displayName) from friends.")
                                            }) {
                                                Image(systemName: "person.badge.minus")
                                                    .font(.system(size: 14))
                                                    .foregroundColor(.gray.opacity(0.7))
                                                    .padding(6)
                                            }
                                        }
                                    }
                                    .padding(12)
                                    .background(Color.white)
                                    .cornerRadius(14)
                                }
                            }
                        }
                        .padding()
                        .padding(.bottom, 140)
                    } else {
                        // Trade Requests
                        VStack(spacing: 12) {
                            if tradeRequests.isEmpty {
                                Text("📬 No active trade requests.")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 40)
                            } else {
                                ForEach(tradeRequests, id: \.id) { trade in
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Text(trade.senderName)
                                                .font(.subheadline.bold())
                                                .foregroundColor(MSColors.ink)
                                            Text("sent a trade offer!")
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
                                            AsyncImage(url: URL(string: trade.stampUrl)) { phase in
                                                if let img = phase.image {
                                                    img.resizable().aspectRatio(contentMode: .fill)
                                                } else {
                                                    MSColors.lightGrey
                                                }
                                            }
                                            .frame(width: 60, height: 60)
                                            .cornerRadius(8)

                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(trade.stampTitle)
                                                    .font(.subheadline.bold())
                                                    .foregroundColor(MSColors.ink)
                                                Text("Rare Vintage Series #2026")
                                                    .font(.caption)
                                                    .foregroundColor(MSColors.grey)
                                            }
                                        }

                                        if trade.status == "PENDING" {
                                            HStack(spacing: 10) {
                                                Button(action: {
                                                    repository.acceptTrade(tradeId: trade.id)
                                                    refreshTrigger.toggle()
                                                    triggerToast("Accepted trade offer!")
                                                }) {
                                                    Text("Accept Trade")
                                                        .font(.caption.bold())
                                                        .frame(maxWidth: .infinity)
                                                        .padding(.vertical, 8)
                                                        .background(MSColors.stamp)
                                                        .foregroundColor(.white)
                                                        .cornerRadius(10)
                                                }

                                                Button(action: {
                                                    repository.rejectTrade(tradeId: trade.id)
                                                    refreshTrigger.toggle()
                                                }) {
                                                    Text("Decline")
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
                        .padding()
                        .padding(.bottom, 140)
                    }
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
                        repository.sendTradeRequest(friendId: friend.id, stampId: stampId)
                        refreshTrigger.toggle()
                        showTradeModal = false
                        triggerToast("Sent trade offer to \(friend.displayName)!")
                    }
                )
            }
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

    var user: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "user_me",
            username: "minh_nguyen",
            displayName: "Minh Nguyen",
            avatarUrl: nil,
            bio: "",
            stampsCreatedCount: 14,
            stampsCollectedCount: 38,
            placesVisitedCount: 9
        )
    }

    var body: some View {
        VStack(spacing: 20) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("My Friend QR Code")
                .font(.headline.bold())
                .foregroundColor(MSColors.ink)

            VStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.white)
                        .frame(width: 220, height: 220)
                        .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 4)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(MSColors.stamp.opacity(0.3), lineWidth: 2)
                        )

                    VStack(spacing: 10) {
                        Image(systemName: "qrcode")
                            .font(.system(size: 140))
                            .foregroundColor(MSColors.ink)

                        Text("#STAMP_\(user.username.uppercased())")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.stamp)
                    }
                }

                Text(user.displayName)
                    .font(.title3.bold())
                    .foregroundColor(MSColors.ink)

                Text("@\(user.username)")
                    .font(.subheadline)
                    .foregroundColor(MSColors.grey)

                Text("Show this QR code to friends to add you instantly on MemoStamp.")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)
            }

            Spacer()

            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                Text("Close")
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
