import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct HomeScreenView: View {
    @ObservedObject var viewModel: HomeObservableViewModel
    var onNavigateToCamera: (String?) -> Void

    @StateObject private var langManager = AppLanguageManager.shared
    @State private var activeTab: String = "Friends"
    @State private var activeCircle: String = "Tất cả bạn bè"
    @State private var showCommentSheet: Bool = false
    @State private var showTradeInboxSheet: Bool = false
    @State private var selectedPostForComments: FeedPost? = nil
    @State private var newCommentText: String = ""
    @State private var activeLightboxReply: FeedReply? = nil
    var filteredPosts: [FeedPost] {
        let friendItems = (viewModel.repository.friends.value as? [FriendItem]) ?? []
        let friendIds = friendItems.map { $0.id }
        switch activeCircle {
        case "Tất cả bạn bè", "All Friends":
            return viewModel.posts.filter { post in
                if post.audienceType == AudienceType.friends {
                    return post.authorId == viewModel.currentUser.uid || friendIds.contains(post.authorId)
                } else if post.audienceType == AudienceType.specificFriends {
                    return post.authorId == viewModel.currentUser.uid || post.targetFriendIds.contains(viewModel.currentUser.uid)
                } else if post.audienceType == AudienceType.onlyMe {
                    return post.authorId == viewModel.currentUser.uid
                } else {
                    return post.authorId == viewModel.currentUser.uid || friendIds.contains(post.authorId)
                }
            }
        case "Bạn bè chọn lọc", "Selected Friends":
            return viewModel.posts.filter { post in
                post.audienceType == AudienceType.specificFriends && (post.authorId == viewModel.currentUser.uid || post.targetFriendIds.contains(viewModel.currentUser.uid))
            }
        case "Chỉ mình tôi", "Only Me":
            return viewModel.posts.filter { post in
                post.audienceType == AudienceType.onlyMe && post.authorId == viewModel.currentUser.uid
            }
        case "Bài viết của tôi", "My Posts":
            return viewModel.posts.filter { post in
                post.authorId == viewModel.currentUser.uid
            }
        default:
            return viewModel.posts
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header: MemoStamp 💌 ◉ with Logo
            HStack(alignment: .center, spacing: 12) {
                Image("app_logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 38, height: 38)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(MSColors.stamp.opacity(0.3), lineWidth: 1)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("MemoStamp")
                            .font(.title2.bold())
                            .foregroundColor(MSColors.stamp)
                        Text("★")
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.gold)
                    }
                    Text("Intimate Memory Feed")
                        .font(.caption)
                        .foregroundColor(MSColors.grey)
                }

                Spacer()

                // Top Actions: Refresh Feed, Theme Palette, Inbox, Profile
                HStack(spacing: 10) {
                    Button(action: {
                        HapticFeedbackManager.shared.playImpact(style: .light)
                        viewModel.refreshFeed()
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(MSColors.ink)
                            .frame(width: 36, height: 36)
                            .background(MSColors.lightGrey.opacity(0.5))
                            .clipShape(Circle())
                    }

                    Button(action: {
                        HapticFeedbackManager.shared.playImpact(style: .medium)
                        showTradeInboxSheet = true
                    }) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "envelope.fill")
                                .font(.system(size: 16))
                                .foregroundColor(MSColors.ink)
                                .frame(width: 36, height: 36)
                                .background(MSColors.lightGrey.opacity(0.5))
                                .clipShape(Circle())

                            Circle()
                                .fill(MSColors.stamp)
                                .frame(width: 8, height: 8)
                                .offset(x: -2, y: 2)
                        }
                    }

                    NavigationLink(destination: PassportScreenView(repository: viewModel.repository)) {
                        AsyncImage(url: URL(string: viewModel.currentUser.avatarUrl ?? "")) { phase in
                            if let image = phase.image {
                                image.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(MSColors.gold.opacity(0.3))
                            }
                        }
                        .frame(width: 36, height: 36)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(MSColors.stamp, lineWidth: 1.5))
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            .padding(.bottom, 8)

            // Feed Filter Options (Designed Custom SF Symbol Vector Icons)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    let filterItems: [(id: String, key: String, title: String, icon: String)] = [
                        ("Tất cả bạn bè", "Tất cả bạn bè", langManager.string(vi: "Tất cả bạn bè", en: "All Friends"), "person.2.fill"),
                        ("Bạn bè chọn lọc", "Bạn bè chọn lọc", langManager.string(vi: "Bạn bè chọn lọc", en: "Selected Friends"), "target"),
                        ("Chỉ mình tôi", "Chỉ mình tôi", langManager.string(vi: "Chỉ mình tôi", en: "Only Me"), "lock.fill"),
                        ("Bài viết của tôi", "Bài viết của tôi", langManager.string(vi: "Bài viết của tôi", en: "My Posts"), "person.fill")
                    ]
                    ForEach(filterItems, id: \.id) { item in
                        let isActive = (activeCircle == item.key || activeCircle == item.title)
                        Button(action: { activeCircle = item.title }) {
                            HStack(spacing: 6) {
                                Image(systemName: item.icon)
                                    .font(.system(size: 12, weight: .bold))
                                Text(item.title)
                                    .font(.caption.bold())
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(isActive ? MSColors.stamp : MSColors.white)
                            .foregroundColor(isActive ? .white : MSColors.ink)
                            .cornerRadius(20)
                            .shadow(color: Color.black.opacity(0.05), radius: 3, x: 0, y: 2)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(isActive ? MSColors.stamp : MSColors.lightGrey, lineWidth: 1)
                            )
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 6)
            }

            Divider()
                .padding(.top, 4)

            // Memory Feed Scroll Stream
            ScrollView {
                LazyVStack(spacing: 16) {
                    // 1. Daily Memory Hero Banner
                    DailyMemoryChainBanner(onStampNow: { onNavigateToCamera(nil) })

                    // 2. Facebook-style Quick Post Box
                    QuickPostCreationBoxView(
                        userAvatarUrl: viewModel.currentUser.avatarUrl,
                        onOpenCamera: { onNavigateToCamera(nil) }
                    )

                    ForEach(filteredPosts, id: \.id) { post in
                        PostCardView(
                            post: post,
                            currentUserId: viewModel.currentUser.uid,
                            onLikeOnly: { viewModel.like(postId: post.id) },
                            onToggleLike: { viewModel.toggleLike(postId: post.id) },
                            onComment: {
                                selectedPostForComments = post
                                showCommentSheet = true
                            },
                            onReply: {
                                onNavigateToCamera(post.id)
                            },
                            onReplyClick: { reply in
                                activeLightboxReply = reply
                            }
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 140)
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .onAppear {
            viewModel.refreshFeed()
        }
        .sheet(isPresented: $showCommentSheet) {
            if let post = selectedPostForComments {
                CommentSheetView(
                    post: post,
                    currentUserId: viewModel.currentUser.uid,
                    commentText: $newCommentText,
                    onAddComment: {
                        viewModel.addComment(postId: post.id, text: newCommentText)
                        newCommentText = ""
                    },
                    onDeleteComment: { commentId in
                        viewModel.deleteComment(postId: post.id, commentId: commentId)
                    }
                )
            }
        }
        .sheet(isPresented: $showTradeInboxSheet) {
            TradeInboxSheetView(repository: viewModel.repository)
        }
        .sheet(item: $activeLightboxReply) { reply in
            ReplyLightboxView(reply: reply)
        }
    }
}

// Subview: Daily Memory Chain Prompt Banner (Matching Android Today's Memory Hero Banner)
struct DailyMemoryChainBanner: View {
    let onStampNow: () -> Void
    @StateObject private var langManager = AppLanguageManager.shared

    var body: some View {
        Button(action: onStampNow) {
            HStack(spacing: 16) {
                // Postal Stamp/Ticket Hero Box (62x62pt, rounded 18pt, terracotta #D85C4A)
                ZStack {
                    RoundedRectangle(cornerRadius: 18)
                        .fill(MSColors.stamp)
                        .frame(width: 62, height: 62)
                        .shadow(color: MSColors.stamp.opacity(0.3), radius: 6, x: 0, y: 3)

                    Image(systemName: "ticket.fill")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(langManager.string(vi: "Hôm nay ghi tem gì?", en: "Today’s memory"))
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(MSColors.ink)

                    Text(langManager.string(vi: "Lưu giữ ký ức qua từng con tem bưu chính.", en: "Capture something worth keeping."))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(MSColors.grey)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(MSColors.grey)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 28)
                    .fill(MSColors.cream)
                    .overlay(
                        RoundedRectangle(cornerRadius: 28)
                            .stroke(MSColors.lightGrey, lineWidth: 1)
                    )
            )
        }
    }
}

// Subview: Facebook-style Quick Post Creation Box (100% Matching Android)
struct QuickPostCreationBoxView: View {
    let userAvatarUrl: String?
    let onOpenCamera: () -> Void
    @StateObject private var langManager = AppLanguageManager.shared

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: userAvatarUrl ?? "")) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Circle().fill(MSColors.stamp.opacity(0.2))
                    }
                }
                .frame(width: 42, height: 42)
                .clipShape(Circle())

                Button(action: onOpenCamera) {
                    HStack {
                        Text(langManager.string(vi: "Chia sẻ khoảnh khắc tem kỷ niệm hôm nay... 📮", en: "Share today's stamp memory moment... 📮"))
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(MSColors.grey)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(MSColors.paper)
                    .cornerRadius(24)
                }
            }

            Divider()
                .background(MSColors.lightGrey)

            HStack {
                Button(action: onOpenCamera) {
                    HStack(spacing: 6) {
                        Image(systemName: "camera.fill")
                            .font(.caption.bold())
                            .foregroundColor(MSColors.stamp)
                        Text(langManager.string(vi: "Chụp tem mới", en: "New stamp"))
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }

                Button(action: onOpenCamera) {
                    HStack(spacing: 6) {
                        Image(systemName: "bookmark.fill")
                            .font(.caption.bold())
                            .foregroundColor(Color.blue)
                        Text(langManager.string(vi: "Kho tem", en: "Stamp vault"))
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }

                Button(action: onOpenCamera) {
                    HStack(spacing: 6) {
                        Image(systemName: "globe")
                            .font(.caption.bold())
                            .foregroundColor(Color.green)
                        Text(langManager.string(vi: "Đăng ngay", en: "Post now"))
                            .font(.caption.bold())
                            .foregroundColor(MSColors.ink)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(MSColors.white)
                .shadow(color: Color.black.opacity(0.06), radius: 6, x: 0, y: 3)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(MSColors.lightGrey, lineWidth: 1)
                )
        )
    }
}

struct PostCardView: View {
    let post: FeedPost
    let currentUserId: String
    let onLikeOnly: () -> Void
    let onToggleLike: () -> Void
    let onComment: () -> Void
    let onReply: () -> Void
    let onReplyClick: (FeedReply) -> Void

    @State private var showHeartAnimation: Bool = false
    @StateObject private var langManager = AppLanguageManager.shared

    private var formattedDateString: String {
        guard post.createdAt > 0 else {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy.MM.dd"
            return formatter.string(from: Date())
        }
        let date = Date(timeIntervalSince1970: TimeInterval(post.createdAt) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: date)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Author & Header Region
            HStack(spacing: 10) {
                AsyncImage(url: URL(string: post.authorAvatar)) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Circle().fill(MSColors.mint.opacity(0.4))
                    }
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(post.authorName)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MSColors.ink)
                        Text("• " + formattedDateString)
                            .font(.caption2)
                            .foregroundColor(MSColors.grey)
                    }

                    if let loc = post.location, !loc.isEmpty {
                        Text("📍 " + loc)
                            .font(.caption2.bold())
                            .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                    }
                }

                Spacer()

                // Signature Rotated Stamp Badge (-3 deg)
                Text((post.stampTitle.isEmpty ? "MEMORY" : String(post.stampTitle.prefix(8))).uppercased())
                    .font(.system(size: 9, weight: .black))
                    .foregroundColor(MSColors.stamp)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 5)
                            .stroke(MSColors.stamp, lineWidth: 1.5)
                    )
                    .rotationEffect(.degrees(-3))
            }

            // Hero Stamp Center Piece with Double Tap Gesture
            ZStack {
                DieCutStampView(
                    title: post.stampTitle,
                    imageUrl: post.stampUrl,
                    location: post.location,
                    dateStr: formattedDateString,
                    note: post.caption,
                    shape: post.shape,
                    isInteractive: true,
                    showMoldOverlay: false,
                    onDoubleTap: {
                        if !post.isLikedByMe {
                            onLikeOnly()
                        }
                        withAnimation(.spring()) {
                            showHeartAnimation = true
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            showHeartAnimation = false
                        }
                    }
                )

                if showHeartAnimation {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 80))
                        .foregroundColor(MSColors.stamp)
                        .shadow(color: Color.black.opacity(0.2), radius: 8)
                        .transition(.scale.combined(with: .opacity))
                        .allowsHitTesting(false)
                }
            }

            // Caption Text
            if let caption = post.caption, !caption.isEmpty {
                Text(caption)
                    .font(.body)
                    .foregroundColor(MSColors.ink)
                    .lineLimit(3)
                    .padding(.top, 2)
            }

            // Interactions Bar (♡ Like | 💬 Comment | 📮 Stamp Reply)
            HStack(spacing: 12) {
                Button(action: onToggleLike) {
                    HStack(spacing: 6) {
                        Image(systemName: post.isLikedByMe ? "heart.fill" : "heart")
                            .foregroundColor(post.isLikedByMe ? MSColors.stamp : MSColors.ink)
                        Text("\(post.reactionCount)")
                            .font(.subheadline.bold())
                            .foregroundColor(post.isLikedByMe ? MSColors.stamp : MSColors.ink)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                }

                Button(action: onComment) {
                    HStack(spacing: 6) {
                        Image(systemName: "bubble.right")
                            .foregroundColor(MSColors.ink)
                        Text("\(post.commentCount)")
                            .font(.subheadline.bold())
                            .foregroundColor(MSColors.ink)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                }

                Spacer()

                Button(action: onReply) {
                    HStack(spacing: 6) {
                        Image(systemName: "seal.fill")
                            .font(.system(size: 11))
                        Text(langManager.string(vi: "Phản hồi \(post.replyCount)", en: "Reply \(post.replyCount)"))
                            .font(.caption.bold())
                    }
                    .foregroundColor(MSColors.stamp)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(MSColors.stamp.opacity(0.1))
                    .cornerRadius(14)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(MSColors.lightGrey.opacity(0.3))
            .cornerRadius(12)

            // Inline Stamp Replies Row (Mini stamp + Author Name identity)
            if !post.replies.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(langManager.string(vi: "Tem phản hồi", en: "Stamp replies"))
                            .font(.caption.bold())
                            .foregroundColor(MSColors.grey)
                        Spacer()
                        Text("\(post.replyCount)")
                            .font(.caption2.bold())
                            .foregroundColor(MSColors.stamp)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(post.replies.prefix(4), id: \.id) { reply in
                                MiniStampReplyCardView(reply: reply, onClick: { onReplyClick(reply) })
                            }

                            Button(action: onReply) {
                                VStack(spacing: 4) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(MSColors.stamp.opacity(0.6), style: StrokeStyle(lineWidth: 1.5, dash: [4]))
                                            .frame(width: 68, height: 76)
                                            .background(MSColors.stamp.opacity(0.05))

                                        VStack(spacing: 2) {
                                            Image(systemName: "plus")
                                                .foregroundColor(MSColors.stamp)
                                            Text(langManager.string(vi: "Phản hồi", en: "Reply"))
                                                .font(.system(size: 10, weight: .bold))
                                                .foregroundColor(MSColors.stamp)
                                        }
                                    }
                                    Text(langManager.string(vi: "Tem của bạn", en: "Your stamp"))
                                        .font(.system(size: 10))
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
                .padding(.top, 4)
            }

            // Inline Comments Preview (Top 2 latest comments)
            if !post.comments.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Divider()
                        .padding(.vertical, 4)

                    ForEach(post.comments.prefix(2), id: \.id) { comment in
                        HStack(spacing: 6) {
                            Text(comment.authorName)
                                .font(.caption.bold())
                                .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                            Text(comment.content)
                                .font(.caption)
                                .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25).opacity(0.85))
                                .lineLimit(1)
                        }
                    }

                    if post.commentCount > 2 {
                        Button(action: onComment) {
                            Text("View all \(post.commentCount) comments")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                                .padding(.top, 2)
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.06), radius: 8, x: 0, y: 3)
    }
}

// Subview: Mini Stamp Reply Card View
struct MiniStampReplyCardView: View {
    let reply: FeedReply
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 4) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color(red: 0.98, green: 0.96, blue: 0.92))
                        .shadow(color: Color.black.opacity(0.08), radius: 2, x: 0, y: 1)

                    if let url = reply.replyStampUrl, !url.isEmpty {
                        AsyncImage(url: URL(string: url)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fit)
                            } else {
                                Text("📮").font(.system(size: 24))
                            }
                        }
                        .padding(4)
                    } else {
                        Text("📮").font(.system(size: 24))
                    }
                }
                .frame(width: 68, height: 76)

                Text(reply.authorName.components(separatedBy: " ").first ?? reply.authorName)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    .lineLimit(1)
            }
        }
    }
}

// Subview: Lightbox Modal for Mini Reply Stamp
struct ReplyLightboxView: View, Identifiable {
    var id: String { reply.id }
    let reply: FeedReply
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        VStack(spacing: 20) {
            // Header
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: reply.authorAvatar)) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Circle().fill(Color.gray.opacity(0.2))
                    }
                }
                .frame(width: 40, height: 40)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text(reply.authorName)
                        .font(.headline.bold())
                    Text("Stamp Reply")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.gray)
                }
            }
            .padding(.horizontal)
            .padding(.top, 20)

            Divider()

            Spacer()

            // Large Reply Stamp Artwork
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color(red: 0.98, green: 0.96, blue: 0.92))
                    .frame(width: 220, height: 275)
                    .shadow(color: Color.black.opacity(0.15), radius: 10, x: 0, y: 5)

                if let url = reply.replyStampUrl, !url.isEmpty {
                    AsyncImage(url: URL(string: url)) { phase in
                        if let img = phase.image {
                            img.resizable().aspectRatio(contentMode: .fit)
                        } else {
                            Text("📮").font(.system(size: 60))
                        }
                    }
                    .frame(width: 208, height: 263)
                    .cornerRadius(10)
                } else {
                    Text("📮").font(.system(size: 60))
                }
            }

            if let note = reply.note, !note.isEmpty {
                Text("“\(note)”")
                    .font(.body)
                    .italic()
                    .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                    .padding(.horizontal, 30)
                    .multilineTextAlignment(.center)
            }

            Spacer()

            Button(action: { presentationMode.wrappedValue.dismiss() }) {
                Text("Close")
                    .font(.subheadline.bold())
                    .foregroundColor(.secondary)
                    .padding(.bottom, 20)
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
    }
}

// Subview: Comment Bottom Sheet with 1-500 char validation and trash delete icon for own comments
struct CommentSheetView: View {
    let post: FeedPost
    let currentUserId: String
    @Binding var commentText: String
    let onAddComment: () -> Void
    let onDeleteComment: (String) -> Void

    var isInputValid: Bool {
        let trimmed = commentText.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 500
    }

    var body: some View {
        VStack {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            Text("Comments (\(post.comments.count))")
                .font(.headline.bold())
                .padding(.top, 4)

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if post.comments.isEmpty {
                        Text("Be the first to leave a comment on this memory! 💭")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    } else {
                        ForEach(post.comments, id: \.id) { comment in
                            HStack(alignment: .top, spacing: 10) {
                                Circle()
                                    .fill(Color.gray.opacity(0.2))
                                    .frame(width: 34, height: 34)
                                    .overlay(Text(String(comment.authorName.prefix(1))).font(.caption.bold()))

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(comment.authorName)
                                        .font(.caption.bold())
                                    Text(comment.content)
                                        .font(.subheadline)
                                        .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                                }

                                Spacer()

                                if comment.authorId == currentUserId {
                                    Button(action: { onDeleteComment(comment.id) }) {
                                        Image(systemName: "trash")
                                            .font(.caption)
                                            .foregroundColor(.gray)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding()
            }

            // Input Row (Clears input upon send, keeps sheet open)
            HStack(spacing: 10) {
                TextField("Add a comment...", text: $commentText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                Button(action: {
                    if isInputValid {
                        onAddComment()
                    }
                }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20) : .gray)
                        .padding(8)
                        .background(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.12) : Color.gray.opacity(0.1))
                        .clipShape(Circle())
                }
                .disabled(!isInputValid)
            }
            .padding()
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92))
    }
}

// ObservableViewModel Wrapper for Swift state binding
class HomeObservableViewModel: ObservableObject {
    let repository: SharedMemoStampRepository
    @Published var posts: [FeedPost] = []
    @Published var currentUser: UserProfile

    init(repository: SharedMemoStampRepository) {
        self.repository = repository
        self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
        self.currentUser = (repository.currentUser.value as? UserProfile) ?? UserProfile(
            uid: "user_me",
            username: "user_memostamp",
            displayName: "MemoStamp Collector",
            avatarUrl: nil,
            bio: "Sưu tầm ký ức qua từng con tem bưu chính 📮",
            stampsCreatedCount: Int32(0),
            stampsCollectedCount: Int32(0),
            placesVisitedCount: Int32(0)
        )
    }

    func refreshFeed() {
        self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
    }

    func like(postId: String) {
        if let post = posts.first(where: { $0.id == postId }), !post.isLikedByMe {
            repository.toggleLike(postId: postId)
            self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
        }
    }

    func toggleLike(postId: String) {
        repository.toggleLike(postId: postId)
        self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
    }

    func addComment(postId: String, text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.count > 500 { return }
        repository.addComment(postId: postId, content: trimmed)
        self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
    }

    func deleteComment(postId: String, commentId: String) {
        repository.deleteComment(postId: postId, commentId: commentId)
        self.posts = (repository.feedPosts.value as? [FeedPost]) ?? []
    }
}

// Subview: Trade Inbox Sheet Modal for responding to stamp trade requests
struct TradeInboxSheetView: View {
    let repository: SharedMemoStampRepository
    @Environment(\.presentationMode) var presentationMode
    @State private var trades: [TradeRequest] = []

    var body: some View {
        VStack {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 4)
                .padding(.top, 8)

            HStack(spacing: 6) {
                Image(systemName: "envelope.badge.fill")
                    .font(.headline)
                    .foregroundColor(MSColors.stamp)
                Text("Stamp Trade Inbox")
                    .font(.headline.bold())
                    .foregroundColor(MSColors.ink)
                Spacer()
                Button("Done") { presentationMode.wrappedValue.dismiss() }
                    .font(.subheadline.bold())
                    .foregroundColor(MSColors.stamp)
            }
            .padding(.horizontal)
            .padding(.top, 4)

            Divider()

            ScrollView {
                VStack(spacing: 12) {
                    if trades.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "tray.fill")
                                .font(.system(size: 38))
                                .foregroundColor(MSColors.stamp.opacity(0.6))
                            Text("No active trade offers")
                                .font(.headline)
                                .foregroundColor(.secondary)
                            Text("When friends send you stamp trade requests, they will appear here.")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 60)
                        .padding(.horizontal, 20)
                    } else {
                        ForEach(trades, id: \.id) { trade in
                            HStack(spacing: 12) {
                                AsyncImage(url: URL(string: trade.senderAvatar)) { phase in
                                    if let img = phase.image {
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } else {
                                        Circle().fill(Color.gray.opacity(0.2))
                                    }
                                }
                                .frame(width: 44, height: 44)
                                .clipShape(Circle())

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(trade.senderName)
                                        .font(.subheadline.bold())
                                        .foregroundColor(MSColors.ink)
                                    Text("Offered: \(trade.stampTitle)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                if trade.status == "PENDING" {
                                    HStack(spacing: 6) {
                                        Button(action: {
                                            repository.acceptTrade(tradeId: trade.id)
                                            trades = (repository.tradeRequests.value as? [TradeRequest]) ?? []
                                            HapticFeedbackManager.shared.playNotification(type: .success)
                                        }) {
                                            Text("Accept")
                                                .font(.caption.bold())
                                                .foregroundColor(.white)
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 6)
                                                .background(MSColors.stamp)
                                                .cornerRadius(12)
                                        }

                                        Button(action: {
                                            repository.rejectTrade(tradeId: trade.id)
                                            trades = (repository.tradeRequests.value as? [TradeRequest]) ?? []
                                            HapticFeedbackManager.shared.playImpact(style: .light)
                                        }) {
                                            Text("Decline")
                                                .font(.caption.bold())
                                                .foregroundColor(.gray)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 6)
                                                .background(Color.gray.opacity(0.12))
                                                .cornerRadius(12)
                                        }
                                    }
                                } else {
                                    Text(trade.status)
                                        .font(.caption.bold())
                                        .foregroundColor(trade.status == "ACCEPTED" ? .green : .red)
                                }
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(14)
                            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                        }
                    }
                }
                .padding()
            }
        }
        .background(MSColors.paper.ignoresSafeArea())
        .onAppear {
            trades = (repository.tradeRequests.value as? [TradeRequest]) ?? []
        }
    }
}

