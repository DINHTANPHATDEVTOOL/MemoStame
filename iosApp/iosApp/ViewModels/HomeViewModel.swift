import SwiftUI
import Combine
import shared

/// Native Swift ViewModel for HomeScreen managing reactive UI state, feed filtering, and memory chain updates.
public class HomeViewModel: ObservableObject {
    @Published public var selectedTab: Int = 0 // 0: Friends, 1: Circles
    @Published public var selectedCircleId: String = "all"
    @Published public var activePostDetail: FeedPost? = nil
    @Published public var showCameraModal: Bool = false
    @Published public var activeReplyPostId: String? = nil
    @Published public var isRefreshing: Bool = false
    @Published public var filterSearchQuery: String = ""

    private let repository: SharedMemoStampRepository
    private var cancellables = Set<AnyCancellable>()

    public init(repository: SharedMemoStampRepository) {
        self.repository = repository
    }

    public var currentUser: UserProfile {
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

    public var feedPosts: [FeedPost] {
        let allPosts = (repository.feedPosts.value as? [FeedPost]) ?? []
        if filterSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return allPosts
        }
        let query = filterSearchQuery.lowercased()
        return allPosts.filter { post in
            post.stampTitle.lowercased().contains(query) ||
            post.authorName.lowercased().contains(query) ||
            (post.location?.lowercased().contains(query) ?? false)
        }
    }

    public var circles: [Circle] {
        (repository.circles.value as? [Circle]) ?? []
    }

    public func toggleLike(postId: String) {
        repository.toggleLike(postId: postId)
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    public func addComment(postId: String, content: String) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        repository.addComment(postId: postId, content: trimmed)
        HapticFeedbackManager.shared.playNotification(type: .success)
    }

    public func deleteComment(postId: String, commentId: String) {
        repository.deleteComment(postId: postId, commentId: commentId)
        HapticFeedbackManager.shared.playImpact(style: .light)
    }

    public func refreshFeed() {
        isRefreshing = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.isRefreshing = false
            HapticFeedbackManager.shared.playNotification(type: .success)
        }
    }
}
