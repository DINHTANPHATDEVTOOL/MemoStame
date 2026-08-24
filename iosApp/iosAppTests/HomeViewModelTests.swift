import XCTest
@testable import shared

final class HomeViewModelTests: XCTestCase {

    func testRepositoryMockUser() {
        let repo = SharedMemoStampRepository()
        XCTAssertNotNil(repo.currentUser.value)
        XCTAssertFalse(repo.stamps.value.isEmpty)
        XCTAssertFalse(repo.feedPosts.value.isEmpty)
    }

    func testLikeToggleUpdatesReactionCount() {
        let repo = SharedMemoStampRepository()
        guard let firstPost = repo.feedPosts.value.first else {
            XCTFail("No posts in feed")
            return
        }
        let initialLiked = firstPost.isLikedByMe
        let initialCount = firstPost.reactionCount

        repo.toggleLike(postId: firstPost.id)

        guard let updatedPost = repo.feedPosts.value.first(where: { $0.id == firstPost.id }) else {
            XCTFail("Post missing after toggle")
            return
        }

        XCTAssertEqual(updatedPost.isLikedByMe, !initialLiked)
        if !initialLiked {
            XCTAssertEqual(updatedPost.reactionCount, initialCount + 1)
        } else {
            XCTAssertEqual(updatedPost.reactionCount, initialCount - 1)
        }
    }

    func testAddCommentIncrementsCommentCount() {
        let repo = SharedMemoStampRepository()
        guard let firstPost = repo.feedPosts.value.first else {
            XCTFail("No posts in feed")
            return
        }
        let initialCount = firstPost.commentCount

        repo.addComment(postId: firstPost.id, content: "Native Swift Unit Test Comment")

        guard let updatedPost = repo.feedPosts.value.first(where: { $0.id == firstPost.id }) else {
            XCTFail("Post missing after comment")
            return
        }

        XCTAssertEqual(updatedPost.commentCount, initialCount + 1)
        XCTAssertTrue(updatedPost.comments.contains(where: { $0.content == "Native Swift Unit Test Comment" }))
    }
}
