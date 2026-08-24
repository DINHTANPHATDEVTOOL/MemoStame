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
            stampsCreatedCount: 14,
            stampsCollectedCount: 38,
            placesVisitedCount: 9
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
                        .foregroundColor(.primary)
                }

                Spacer()

                Text("PASSPORT PROFILE")
                    .font(.headline.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))

                Spacer()

                Button(action: { showEditProfile = true }) {
                    Image(systemName: "pencil")
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                }
            }
            .padding()

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Profile Header Card
                    VStack(spacing: 12) {
                        AsyncImage(url: URL(string: user.avatarUrl ?? "")) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(Color.orange.opacity(0.3))
                            }
                        }
                        .frame(width: 84, height: 84)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color(red: 0.82, green: 0.65, blue: 0.35), lineWidth: 3))
                        .shadow(radius: 4)

                        Text(user.displayName)
                            .font(.title3.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))

                        Text("@" + user.username)
                            .font(.subheadline)
                            .foregroundColor(.secondary)

                        Text("“" + user.bio + "”")
                            .font(.caption)
                            .italic()
                            .foregroundColor(Color(red: 0.30, green: 0.30, blue: 0.35))
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
                            .foregroundColor(.secondary)
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
                                                .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                        }
                                    }
                                    Text(badge.subtitle)
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
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
    }
}

struct StatBox: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
            Text(title)
                .font(.caption2.bold())
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.white)
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)
    }
}
