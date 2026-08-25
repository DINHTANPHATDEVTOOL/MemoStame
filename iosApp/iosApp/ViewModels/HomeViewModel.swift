import SwiftUI
import Combine
import shared

/// Native Swift ViewModel for HomeScreen managing reactive UI state, feed filtering, and memory chain updates.
class HomeViewModel: ObservableObject {
    @Published var selectedTab: Int = 0 // 0: Friends, 1: Circles
    @Published var selectedCircleId: String = "all"
    @Published var activePostDetail: FeedPost? = nil
    @Published var showCameraModal: Bool = false
    @Published var activeReplyPostId: String? = nil
    @Published var isRefreshing: Bool = false
    @Published var filterSearchQuery: String = ""

    private let repository: SharedMemoStampRepository
    private var cancellables = Set<AnyCancellable>()

    init(repository: SharedMemoStampRepository) {
        self.repository = repository

        Timer.publish(every: 0.5, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    var currentUser: UserProfile {
        (repository.currentUser.value as? UserProfile) ?? UserProfile(
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

    var feedPosts: [FeedPost] {
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

    var circles: [SharedCircle] {
        (repository.circles.value as? [SharedCircle]) ?? []
    }

    func toggleLike(postId: String) {
        repository.toggleLike(postId: postId)
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func addComment(postId: String, content: String) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        repository.addComment(postId: postId, content: trimmed)
        HapticFeedbackManager.shared.playNotification(type: .success)
    }

    func deleteComment(postId: String, commentId: String) {
        repository.deleteComment(postId: postId, commentId: commentId)
        HapticFeedbackManager.shared.playImpact(style: .light)
    }

    func refreshFeed() {
        isRefreshing = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.isRefreshing = false
            HapticFeedbackManager.shared.playNotification(type: .success)
        }
    }
}
