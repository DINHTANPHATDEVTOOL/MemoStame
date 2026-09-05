import Foundation
import Combine
import shared

class IOSFeedRepository: ObservableObject {
    static let shared = IOSFeedRepository()

    @Published var posts: [FeedPost] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil

    private var activeUserId: String? = nil
    private let client = SupabaseSocialClient.shared

    private init() {
        syncUserSession()
    }

    func syncUserSession() {
        let currentAuthUid = SupabaseAuthService.shared.currentUserId
        if activeUserId != currentAuthUid {
            activeUserId = currentAuthUid
            posts = []
            if let uid = currentAuthUid, IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(uid) {
                loadFeed()
            }
        }
    }

    func clear() {
        activeUserId = nil
        posts = []
        errorMessage = nil
    }

    private func parseIsoToMillis(_ dateStr: String?) -> Int64 {
        guard let str = dateStr?.trimmingCharacters(in: .whitespacesAndNewlines), !str.isEmpty else {
            return Int64(Date().timeIntervalSince1970 * 1000)
        }
        if let millis = Int64(str) {
            return millis < 10_000_000_000 ? millis * 1000 : millis
        }
        if let d = Double(str) {
            return Int64(d < 10_000_000_000 ? d * 1000 : d)
        }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = iso.date(from: str) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        iso.formatOptions = [.withInternetDateTime]
        if let date = iso.date(from: str) {
            return Int64(date.timeIntervalSince1970 * 1000)
        }
        return Int64(Date().timeIntervalSince1970 * 1000)
    }

    private func parseAudienceType(_ raw: String?) -> AudienceType {
        switch (raw ?? "").uppercased() {
        case "ONLY_ME": return .onlyMe
        case "SPECIFIC_FRIENDS": return .specificFriends
        default: return .friends
        }
    }

    private func parseFeedPostType(_ raw: String?) -> FeedPostType {
        switch (raw ?? "").uppercased() {
        case "MEMORY": return .memory
        case "STAMP_REPLY": return .stampReply
        case "COLLECTION_MILESTONE": return .collectionMilestone
        default: return .memory
        }
    }

    func loadFeed(completion: ((Result<[FeedPost], Error>) -> Void)? = nil) {
        syncUserSession()
        guard let currentUid = activeUserId, IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        isLoading = true
        errorMessage = nil

        let group = DispatchGroup()
        var loadedPosts: [SupabaseFeedPostRecord] = []
        var loadedReactions: [SupabaseFeedReactionRecord] = []
        var loadedComments: [SupabaseFeedCommentRecord] = []
        var loadedReplies: [SupabaseFeedReplyRecord] = []
        var loadError: Error? = nil

        group.enter()
        client.getFeedPosts { result in
            switch result {
            case .success(let items):
                loadedPosts = items
            case .failure(let err):
                loadError = err
            }
            group.leave()
        }

        group.enter()
        client.getFeedReactions { result in
            if case .success(let items) = result {
                loadedReactions = items
            }
            group.leave()
        }

        group.enter()
        client.getFeedComments { result in
            if case .success(let items) = result {
                loadedComments = items
            }
            group.leave()
        }

        group.enter()
        client.getFeedReplies { result in
            if case .success(let items) = result {
                loadedReplies = items
            }
            group.leave()
        }

        group.notify(queue: .main) { [weak self] in
            guard let self = self else { return }
            self.isLoading = false

            if let err = loadError, loadedPosts.isEmpty {
                self.errorMessage = err.localizedDescription
                completion?(.failure(err))
                return
            }

            // Hydrate posts
            let reactionsByPost = Dictionary(grouping: loadedReactions, by: { $0.postId })
            let commentsByPost = Dictionary(grouping: loadedComments, by: { $0.postId })
            let repliesByPost = Dictionary(grouping: loadedReplies, by: { $0.postId })

            let hydrated: [FeedPost] = loadedPosts.compactMap { p in
                guard !p.id.isEmpty else { return nil }
                let rawUrl = p.stampUrl ?? ""
                let safeUrl = isValidRemoteStampUrl(rawUrl) ? rawUrl : "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600"

                let postReactions = (reactionsByPost[p.id] ?? []).map { r in
                    FeedReaction(
                        id: r.id,
                        postId: r.postId,
                        userId: r.userId,
                        userName: r.userName ?? "Bạn bè",
                        emoji: r.emoji ?? "❤️",
                        createdAt: self.parseIsoToMillis(r.createdAt)
                    )
                }

                let postComments = (commentsByPost[p.id] ?? []).map { c in
                    FeedComment(
                        id: c.id,
                        postId: c.postId,
                        authorId: c.authorId,
                        authorName: c.authorName ?? "Bạn bè",
                        authorAvatar: c.authorAvatar ?? "https://i.pravatar.cc/150?u=\(c.authorId)",
                        content: c.content,
                        createdAt: self.parseIsoToMillis(c.createdAt)
                    )
                }

                let postReplies = (repliesByPost[p.id] ?? []).compactMap { rep -> FeedReply? in
                    guard isValidRemoteStampUrl(rep.replyStampUrl) else { return nil }
                    return FeedReply(
                        id: rep.id,
                        postId: rep.postId,
                        authorId: rep.authorId,
                        authorName: rep.authorName ?? "Bạn bè",
                        authorAvatar: rep.authorAvatar ?? "https://i.pravatar.cc/150?u=\(rep.authorId)",
                        replyStampId: rep.replyStampId ?? "",
                        replyStampUrl: rep.replyStampUrl,
                        shape: rep.shape ?? "classic",
                        note: rep.note,
                        createdAt: self.parseIsoToMillis(rep.createdAt)
                    )
                }

                let isLiked = postReactions.contains(where: { $0.userId == currentUid })

                return FeedPost(
                    id: p.id,
                    stampId: p.stampId ?? "",
                    stampUrl: safeUrl,
                    stampTitle: p.stampTitle ?? "Tem kỷ niệm",
                    shape: p.shape ?? "classic",
                    authorId: p.authorId,
                    authorName: p.authorName ?? "Người dùng",
                    authorAvatar: p.authorAvatar ?? "https://i.pravatar.cc/150?u=\(p.authorId)",
                    caption: p.caption,
                    audienceType: self.parseAudienceType(p.audienceType),
                    targetFriendIds: [],
                    circleId: p.circleId,
                    circleName: p.circleName,
                    createdAt: self.parseIsoToMillis(p.createdAt),
                    type: self.parseFeedPostType(p.type),
                    location: p.location,
                    reactionCount: Int32(postReactions.count),
                    commentCount: Int32(postComments.count),
                    replyCount: Int32(postReplies.count),
                    reactions: postReactions,
                    comments: postComments,
                    replies: postReplies,
                    isLikedByMe: isLiked,
                    isSeen: false
                )
            }

            self.posts = hydrated
            completion?(.success(hydrated))
        }
    }

    func toggleLike(postId: String, completion: ((Result<Bool, Error>) -> Void)? = nil) {
        guard let currentUid = activeUserId, IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        let isCurrentlyLiked = posts.first(where: { $0.id == postId })?.isLikedByMe ?? false

        if isCurrentlyLiked {
            client.deleteFeedReaction(postId: postId, userId: currentUid) { [weak self] result in
                DispatchQueue.main.async {
                    switch result {
                    case .success:
                        self?.loadFeed()
                        completion?(.success(true))
                    case .failure(let err):
                        completion?(.failure(err))
                    }
                }
            }
        } else {
            let record = SupabaseFeedReactionRecord(
                id: "\(postId):\(currentUid)",
                postId: postId,
                userId: currentUid,
                userName: SupabaseAuthService.shared.activeSession?.email ?? "Tôi",
                emoji: "❤️",
                createdAt: nil
            )
            client.addFeedReaction(reaction: record) { [weak self] result in
                DispatchQueue.main.async {
                    switch result {
                    case .success:
                        self?.loadFeed()
                        completion?(.success(true))
                    case .failure(let err):
                        completion?(.failure(err))
                    }
                }
            }
        }
    }

    func addComment(postId: String, text: String, completion: ((Result<FeedComment, Error>) -> Void)? = nil) {
        guard let currentUid = activeUserId, IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) else {
            completion?(.failure(SupabaseSocialError.unauthorized))
            return
        }

        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            completion?(.failure(SupabaseSocialError.invalidData("Nội dung bình luận không được để trống")))
            return
        }

        let record = SupabaseFeedCommentRecord(
            id: UUID().uuidString,
            postId: postId,
            authorId: currentUid,
            authorName: SupabaseAuthService.shared.activeSession?.email ?? "Bạn bè",
            authorAvatar: "https://i.pravatar.cc/150?u=\(currentUid)",
            content: trimmed,
            createdAt: nil
        )

        client.addFeedComment(comment: record) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let serverComment):
                    let domainComment = FeedComment(
                        id: serverComment.id,
                        postId: serverComment.postId,
                        authorId: serverComment.authorId,
                        authorName: serverComment.authorName ?? "Bạn bè",
                        authorAvatar: serverComment.authorAvatar ?? "https://i.pravatar.cc/150?u=\(serverComment.authorId)",
                        content: serverComment.content,
                        createdAt: self?.parseIsoToMillis(serverComment.createdAt) ?? Int64(Date().timeIntervalSince1970 * 1000)
                    )
                    self?.loadFeed()
                    completion?(.success(domainComment))
                case .failure(let err):
                    completion?(.failure(err))
                }
            }
        }
    }

    func deleteComment(postId: String, commentId: String, completion: ((Result<Bool, Error>) -> Void)? = nil) {
        client.deleteFeedComment(commentId: commentId) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    self?.loadFeed()
                    completion?(.success(true))
                case .failure(let err):
                    completion?(.failure(err))
                }
            }
        }
    }

    func createStampReply(
        postId: String,
        stampId: String,
        stampUrl: String,
        shape: String,
        note: String?,
        completion: @escaping (Result<FeedReply, Error>) -> Void
    ) {
        guard let currentUid = activeUserId, IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(currentUid) else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }
        guard isValidRemoteStampUrl(stampUrl) else {
            completion(.failure(SupabaseSocialError.invalidData("Ảnh tem phản hồi phải là remote URL hợp lệ")))
            return
        }

        let record = SupabaseFeedReplyRecord(
            id: UUID().uuidString,
            postId: postId,
            authorId: currentUid,
            authorName: SupabaseAuthService.shared.activeSession?.email ?? "Bạn bè",
            authorAvatar: "https://i.pravatar.cc/150?u=\(currentUid)",
            replyStampId: stampId,
            replyStampUrl: stampUrl,
            shape: shape,
            note: note,
            createdAt: nil
        )

        client.addFeedReply(reply: record) { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success(let serverReply):
                    let domainReply = FeedReply(
                        id: serverReply.id,
                        postId: serverReply.postId,
                        authorId: serverReply.authorId,
                        authorName: serverReply.authorName ?? "Bạn bè",
                        authorAvatar: serverReply.authorAvatar ?? "https://i.pravatar.cc/150?u=\(serverReply.authorId)",
                        replyStampId: serverReply.replyStampId ?? stampId,
                        replyStampUrl: serverReply.replyStampUrl,
                        shape: serverReply.shape ?? shape,
                        note: serverReply.note,
                        createdAt: self?.parseIsoToMillis(serverReply.createdAt) ?? Int64(Date().timeIntervalSince1970 * 1000)
                    )
                    self?.loadFeed()
                    completion(.success(domainReply))
                case .failure(let err):
                    completion(.failure(err))
                }
            }
        }
    }
}
