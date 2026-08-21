import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct HomeScreenView: View {
    @ObservedObject var viewModel: HomeObservableViewModel
    var onNavigateToCamera: (String?) -> Void

    @State private var activeTab: String = "Friends"
    @State private var activeCircle: String = "All Friends"
    @State private var showCommentSheet: Bool = false
    @State private var selectedPostForComments: FeedPost? = nil
    @State private var newCommentText: String = ""
    @State private var activeLightboxReply: FeedReply? = nil

    let circleOptions = ["All Friends", "Best Friends 💖", "Da Lat Trip 🌲", "Class 22DTHB3 🎓"]

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
                            .stroke(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.3), lineWidth: 1)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("MemoStamp")
                            .font(.title2.bold())
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                        Text("★")
                            .font(.caption2.bold())
                            .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                    }
                    Text("Intimate Memory Feed")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.50))
                }

                Spacer()

                // Inbox Envelope & Profile
                HStack(spacing: 12) {
                    Button(action: {}) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "envelope.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                                .frame(width: 38, height: 38)
                                .background(Color.gray.opacity(0.12))
                                .clipShape(Circle())

                            Circle()
                                .fill(Color(red: 0.85, green: 0.25, blue: 0.20))
                                .frame(width: 8, height: 8)
                                .offset(x: -2, y: 2)
                        }
                    }

                    NavigationLink(destination: PassportScreenView(repository: viewModel.repository)) {
                        AsyncImage(url: URL(string: viewModel.currentUser.avatarUrl ?? "")) { phase in
                            if let image = phase.image {
                                image.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(Color.orange.opacity(0.3))
                            }
                        }
                        .frame(width: 38, height: 38)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color(red: 0.85, green: 0.25, blue: 0.20), lineWidth: 1.5))
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            .padding(.bottom, 8)

            // Feed Tabs: Friends / Circles
            HStack(spacing: 20) {
                Button(action: { activeTab = "Friends" }) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Friends")
                            .font(.title3.weight(activeTab == "Friends" ? .bold : .medium))
                            .foregroundColor(activeTab == "Friends" ? Color(red: 0.15, green: 0.15, blue: 0.18) : .gray)
                        if activeTab == "Friends" {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(red: 0.85, green: 0.25, blue: 0.20))
                                .frame(width: 28, height: 3)
                        }
                    }
                }

                Button(action: { activeTab = "Circles" }) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Circles")
                            .font(.title3.weight(activeTab == "Circles" ? .bold : .medium))
                            .foregroundColor(activeTab == "Circles" ? Color(red: 0.15, green: 0.15, blue: 0.18) : .gray)
                        if activeTab == "Circles" {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(red: 0.85, green: 0.25, blue: 0.20))
                                .frame(width: 28, height: 3)
                        }
                    }
                }

                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 4)

            // Circle Filter Chips if Circles tab selected
            if activeTab == "Circles" {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(circleOptions, id: \.self) { circle in
                            Button(action: { activeCircle = circle }) {
                                Text(circle)
                                    .font(.caption.bold())
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(activeCircle == circle ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.gray.opacity(0.12))
                                    .foregroundColor(activeCircle == circle ? .white : Color(red: 0.20, green: 0.20, blue: 0.25))
                                    .cornerRadius(16)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 6)
                }
            }

            Divider()
                .padding(.top, 4)

            // Memory Feed Scroll Stream
            ScrollView {
                LazyVStack(spacing: 20) {
                    // Daily Memory Chain Prompter
                    DailyMemoryChainBanner(onStampNow: { onNavigateToCamera(nil) })

                    ForEach(viewModel.posts, id: \.id) { post in
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
                .padding(.bottom, 100)
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
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
        .sheet(item: $activeLightboxReply) { reply in
            ReplyLightboxView(reply: reply)
        }
    }
}

// Subview: Daily Memory Chain Prompt Banner
struct DailyMemoryChainBanner: View {
    let onStampNow: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Today's Memory Chain 📮")
                    .font(.subheadline.bold())
                    .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                Text("What are you doing right now? Stamp a memory to join your friends.")
                    .font(.caption)
                    .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25).opacity(0.8))
            }

            Spacer()

            Button(action: onStampNow) {
                Text("Stamp now")
                    .font(.caption.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color(red: 0.20, green: 0.45, blue: 0.75))
                    .cornerRadius(20)
            }
        }
        .padding(14)
        .background(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.08))
        .cornerRadius(16)
    }
}

// Subview: Post Card View with strict double-tap like, comments preview, and inline stamp reply identity
struct PostCardView: View {
    let post: FeedPost
    let currentUserId: String
    let onLikeOnly: () -> Void
    let onToggleLike: () -> Void
    let onComment: () -> Void
    let onReply: () -> Void
    let onReplyClick: (FeedReply) -> Void

    @State private var showHeartAnimation: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Author & Header Region
            HStack(spacing: 10) {
                AsyncImage(url: URL(string: post.authorAvatar)) { phase in
                    if let img = phase.image {
                        img.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        Circle().fill(Color.gray.opacity(0.2))
                    }
                }
                .frame(width: 38, height: 38)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(post.authorName)
                            .font(.subheadline.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Text("• Just now")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }

                    if let loc = post.location, !loc.isEmpty {
                        Text("📍 " + loc)
                            .font(.caption2.bold())
                            .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                    }
                }

                Spacer()

                NavigationLink(destination: PostDetailScreenView(post: post, onLike: onToggleLike, onReply: onReply)) {
                    Image(systemName: "ellipsis")
                        .foregroundColor(.gray)
                        .frame(width: 32, height: 32)
                }
            }

            // Hero Stamp Center Piece with Double Tap Gesture (Strictly LIKE only)
            ZStack {
                DieCutStampView(
                    title: post.stampTitle,
                    imageUrl: post.stampUrl,
                    location: post.location,
                    dateStr: "2026.08.18",
                    shape: post.shape,
                    isInteractive: true
                )
                .onTapGesture(count: 2) {
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

                if showHeartAnimation {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 80))
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                        .shadow(color: Color.black.opacity(0.2), radius: 8)
                        .transition(.scale.combined(with: .opacity))
                }
            }

            // Caption Text
            if let caption = post.caption, !caption.isEmpty {
                Text(caption)
                    .font(.body)
                    .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                    .lineLimit(3)
                    .padding(.top, 2)
            }

            // Interactions Bar (♡ Like | 💬 Comment | 📮 Stamp Reply)
            HStack(spacing: 12) {
                Button(action: onToggleLike) {
                    HStack(spacing: 6) {
                        Image(systemName: post.isLikedByMe ? "heart.fill" : "heart")
                            .foregroundColor(post.isLikedByMe ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color(red: 0.15, green: 0.15, blue: 0.18))
                        Text("\(post.reactionCount)")
                            .font(.subheadline.bold())
                            .foregroundColor(post.isLikedByMe ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color(red: 0.15, green: 0.15, blue: 0.18))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                }

                Button(action: onComment) {
                    HStack(spacing: 6) {
                        Image(systemName: "bubble.right")
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Text("\(post.commentCount)")
                            .font(.subheadline.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                }

                Spacer()

                Button(action: onReply) {
                    HStack(spacing: 6) {
                        Text("📮")
                        Text("Reply \(post.replyCount)")
                            .font(.caption.bold())
                            .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.1))
                    .cornerRadius(14)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.gray.opacity(0.08))
            .cornerRadius(12)

            // Inline Stamp Replies Row (Mini stamp + Author Name identity)
            if !post.replies.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Stamp replies")
                            .font(.caption.bold())
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(post.replyCount)")
                            .font(.caption2.bold())
                            .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
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
                                            .stroke(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.6), style: StrokeStyle(lineWidth: 1.5, dash: [4]))
                                            .frame(width: 68, height: 76)
                                            .background(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.05))

                                        VStack(spacing: 2) {
                                            Image(systemName: "plus")
                                                .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                                            Text("Reply")
                                                .font(.system(size: 10, weight: .bold))
                                                .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                                        }
                                    }
                                    Text("Your stamp")
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
            username: "minh_nguyen",
            displayName: "Minh Nguyen",
            avatarUrl: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
            bio: "Capturing life memory stamps ☕✨",
            stampsCreatedCount: 14,
            stampsCollectedCount: 38,
            placesVisitedCount: 9
        )
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
