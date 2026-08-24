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

    var friends: [FriendItem] {
        (repository.friends.value as? [FriendItem]) ?? []
    }

    var tradeRequests: [TradeRequest] {
        (repository.tradeRequests.value as? [TradeRequest]) ?? []
    }

    var stamps: [StampItem] {
        (repository.stamps.value as? [StampItem]) ?? []
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("FRIENDS & STAMP TRADE")
                            .font(.title2.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Text("Share & exchange vintage stamps")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                }

                // Search/Add Friend Code Input
                HStack {
                    Image(systemName: "person.badge.plus")
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    TextField("Enter Friend Code (e.g. #STAMP99)", text: $friendCode)
                        .font(.subheadline)
                    Button(action: {
                        if !friendCode.isEmpty {
                            // Friend code added feedback
                            friendCode = ""
                        }
                    }) {
                        Text("Add")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(friendCode.isEmpty ? Color.gray.opacity(0.4) : Color(red: 0.85, green: 0.25, blue: 0.20))
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
                        ForEach(friends, id: \.id) { friend in
                            HStack(spacing: 12) {
                                ZStack(alignment: .bottomTrailing) {
                                    AsyncImage(url: URL(string: friend.avatarUrl)) { phase in
                                        if let img = phase.image {
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } else {
                                            Circle().fill(Color.gray.opacity(0.3))
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
                                    Text("@" + friend.username + " • \(friend.tradeCount) trades")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                Button(action: {
                                    selectedFriendForTrade = friend
                                    showTradeModal = true
                                }) {
                                    HStack(spacing: 4) {
                                        Image(systemName: "arrow.triangle.2.circlepath")
                                        Text("Trade")
                                            .font(.caption.bold())
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color(red: 0.82, green: 0.65, blue: 0.35).opacity(0.2))
                                    .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                                    .cornerRadius(12)
                                }
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(14)
                        }
                    }
                    .padding()
                } else {
                    // Trade Requests
                    VStack(spacing: 12) {
                        ForEach(tradeRequests, id: \.id) { trade in
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text(trade.senderName)
                                        .font(.subheadline.bold())
                                    Text("sent a trade offer!")
                                        .font(.subheadline)
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text(trade.status)
                                        .font(.caption2.bold())
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(trade.status == "ACCEPTED" ? Color.green.opacity(0.2) : Color.orange.opacity(0.2))
                                        .foregroundColor(trade.status == "ACCEPTED" ? .green : .orange)
                                        .cornerRadius(8)
                                }

                                HStack(spacing: 12) {
                                    AsyncImage(url: URL(string: trade.stampUrl)) { phase in
                                        if let img = phase.image {
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } else {
                                            Color.gray.opacity(0.3)
                                        }
                                    }
                                    .frame(width: 60, height: 60)
                                    .cornerRadius(8)

                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(trade.stampTitle)
                                            .font(.subheadline.bold())
                                        Text("Rare Vintage Series #2026")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }

                                if trade.status == "PENDING" {
                                    HStack {
                                        Button(action: {
                                            repository.acceptTrade(tradeId: trade.id)
                                        }) {
                                            Text("Accept Trade")
                                                .font(.caption.bold())
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, 8)
                                                .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                                                .foregroundColor(.white)
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
                    .padding()
                }
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(isPresented: $showTradeModal) {
            if let friend = selectedFriendForTrade {
                TradeStampModalView(
                    friend: friend,
                    stamps: stamps,
                    onSendTrade: { stampId in
                        repository.sendTradeRequest(friendId: friend.id, stampId: stampId)
                        showTradeModal = false
                    }
                )
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
