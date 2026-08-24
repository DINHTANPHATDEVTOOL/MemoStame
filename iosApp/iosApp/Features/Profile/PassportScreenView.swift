import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct PassportScreenView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode

    @State private var showEditProfile: Bool = false

    var user: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "user_me",
            username: "minh_nguyen",
            displayName: "Minh Nguyen",
            avatarUrl: nil,
            bio: "Capturing life memory stamps",
            stampsCreatedCount: Int32(14),
            stampsCollectedCount: Int32(38),
            placesVisitedCount: Int32(9)
        )
    }

    var badges: [PassportBadge] {
        (repository.badges.value as? [PassportBadge]) ?? []
    }

    var friendsCount: Int {
        ((repository.friends.value as? [FriendItem]) ?? []).count
    }

    var collectionsCount: Int {
        ((repository.collections.value as? [CollectionItem]) ?? []).count
    }

    private func badgeIconName(_ key: String) -> String {
        switch key {
        case "plane", "✈️": return "paperplane.fill"
        case "coffee", "☕": return "cup.and.saucer.fill"
        case "palette", "🎨": return "paintpalette.fill"
        case "crown", "👑": return "crown.fill"
        case "tree", "🌲": return "leaf.fill"
        case "heart", "💖": return "heart.fill"
        default: return "star.fill"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.title3.bold())
                        .foregroundColor(MSColors.ink)
                }

                Spacer()

                Text("PASSPORT PROFILE")
                    .font(.headline.bold())
                    .foregroundColor(MSColors.ink)

                Spacer()

                Button(action: { showEditProfile = true }) {
                    Image(systemName: "pencil")
                        .font(.title3)
                        .foregroundColor(MSColors.stamp)
                }
            }
            .padding()

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Profile Header Card (Golden Ring Viền mạ vàng)
                    VStack(spacing: 12) {
                        AsyncImage(url: URL(string: user.avatarUrl ?? "")) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(MSColors.lightGrey)
                            }
                        }
                        .frame(width: 88, height: 88)
                        .clipShape(Circle())
                        .overlay(
                            ZStack {
                                Circle().stroke(MSColors.gold, lineWidth: 3)
                                Circle().stroke(MSColors.gold.opacity(0.5), lineWidth: 1).padding(-4)
                            }
                        )
                        .shadow(color: MSColors.gold.opacity(0.2), radius: 6, x: 0, y: 3)

                        Text(user.displayName)
                            .font(.title3.bold())
                            .foregroundColor(MSColors.ink)

                        Text("@" + user.username)
                            .font(.subheadline)
                            .foregroundColor(MSColors.grey)

                        Text("“" + user.bio + "”")
                            .font(.caption)
                            .italic()
                            .foregroundColor(MSColors.grey)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(.top, 12)

                    // Stats Counters Row (Tem dán, Bạn bè, Bộ sưu tập)
                    HStack(spacing: 14) {
                        StatBox(title: "TEM DÁN", value: "\(user.stampsCreatedCount)")
                        StatBox(title: "BẠN BÈ", value: "\(friendsCount)")
                        StatBox(title: "BỘ SƯU TẬP", value: "\(collectionsCount)")
                    }
                    .padding(.horizontal)

                    // Passport Badges & Stamps
                    VStack(alignment: .leading, spacing: 14) {
                        Text("PASSPORT STAMPS & BADGES")
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.grey)
                            .padding(.horizontal)

                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            ForEach(badges, id: \.title) { badge in
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack(spacing: 6) {
                                        Image(systemName: badgeIconName(badge.iconEmoji))
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(MSColors.stamp)
                                        Text(badge.title)
                                            .font(.subheadline.bold())
                                            .foregroundColor(MSColors.ink)
                                        Spacer()
                                        if badge.isUnlocked {
                                            Image(systemName: "checkmark.seal.fill")
                                                .foregroundColor(MSColors.stamp)
                                        }
                                    }
                                    Text(badge.subtitle)
                                        .font(.caption2)
                                        .foregroundColor(MSColors.grey)
                                }
                                .padding(12)
                                .background(Color.white)
                                .cornerRadius(14)
                                .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.bottom, 40)
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .sheet(isPresented: $showEditProfile) {
            EditProfileSheetView(repository: repository)
        }
    }
}

struct EditProfileSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode

    @State private var displayName: String = ""
    @State private var bio: String = ""

    var body: some View {
        VStack(spacing: 16) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("Edit Passport Profile")
                .font(.headline.bold())
                .foregroundColor(MSColors.ink)

            VStack(alignment: .leading, spacing: 14) {
                Text("DISPLAY NAME")
                    .font(.caption2.bold())
                    .foregroundColor(MSColors.grey)

                TextField("Enter Display Name", text: $displayName)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                Text("BIO NOTE")
                    .font(.caption2.bold())
                    .foregroundColor(MSColors.grey)

                TextEditor(text: $bio)
                    .frame(height: 80)
                    .padding(4)
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                    )
            }
            .padding(.horizontal, 20)

            Spacer()

            Button(action: {
                let current = (repository.currentUser.value as? UserProfile)
                let name = displayName.isEmpty ? (current?.displayName ?? "") : displayName
                let note = bio.isEmpty ? (current?.bio ?? "") : bio
                repository.updateProfile(displayName: name, bio: note, avatarUrl: nil)
                presentationMode.wrappedValue.dismiss()
            }) {
                Text("Save Profile Changes")
                    .font(.body.bold())
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(MSColors.stamp)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
        .onAppear {
            if let user = repository.currentUser.value as? UserProfile {
                displayName = user.displayName
                bio = user.bio
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
    }
}

struct StatBox: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundColor(MSColors.stamp)
            Text(title)
                .font(.caption2.bold())
                .foregroundColor(MSColors.grey)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.white)
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
    }
}
