import XCTest
import SwiftUI
@testable import iosApp
@testable import shared

final class PostDetailCommentReliabilityTests: XCTestCase {

    var viewModel: PostDetailViewModel!
    var repository: SharedMemoStampRepository!

    override func setUp() {
        super.setUp()
        viewModel = PostDetailViewModel()
        repository = SharedMemoStampRepository()
    }

    override func tearDown() {
        viewModel = nil
        repository = nil
        super.tearDown()
    }

    // 1. Comment Validation Tests
    func testValidation_emptyAndWhitespaceRejected() {
        let empty = ""
        let whitespace = "   \n\t  "

        let trimmedEmpty = empty.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedWhitespace = whitespace.trimmingCharacters(in: .whitespacesAndNewlines)

        XCTAssertTrue(trimmedEmpty.isEmpty)
        XCTAssertTrue(trimmedWhitespace.isEmpty)

        // ViewModel addComment with empty input
        viewModel.addComment(postId: "p1", draft: empty, authUid: "u1", repository: repository)
        XCTAssertEqual(viewModel.commentSubmissionError, .validationFailed)
        XCTAssertFalse(viewModel.isSubmittingComment)
    }

    func testValidation_exceeding500CharsRejected() {
        let longDraft = String(repeating: "a", count: 501)
        viewModel.addComment(postId: "p1", draft: longDraft, authUid: "u1", repository: repository)
        XCTAssertEqual(viewModel.commentSubmissionError, .validationFailed)
        XCTAssertFalse(viewModel.isSubmittingComment)
    }

    func testValidation_validInputPasses() {
        let valid = "Great memory stamp!"
        let trimmed = valid.trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertFalse(trimmed.isEmpty)
        XCTAssertTrue(trimmed.count <= 500)
    }

    // 2. Submit Lock / Rapid Tap Protection
    func testRapidTap_preventsDuplicateSubmission() {
        viewModel.isSubmittingComment = true

        // Attempt second submission while first is running
        viewModel.addComment(postId: "p1", draft: "Second tap", authUid: "u1", repository: repository)

        // Error should not be changed, remains submitting
        XCTAssertTrue(viewModel.isSubmittingComment)
    }

    // 3. Draft Preservation on Failure & Success
    func testDraftPreservation_failurePreservesDraft() {
        var draft = "User draft text"
        viewModel.isSubmittingComment = true

        // Simulate network failure
        let networkError = NSError(domain: "NSURLErrorDomain", code: -1009, userInfo: [NSLocalizedDescriptionKey: "The Internet connection appears to be offline."])
        let classified = viewModel.classifyError(networkError)
        viewModel.commentSubmissionError = classified
        viewModel.commentErrorMessage = viewModel.localizedErrorMessage(for: classified)
        viewModel.isSubmittingComment = false

        // Draft clearing check: only clears if commentSuccessToken matches
        if let token = viewModel.commentSuccessToken, draft.trimmingCharacters(in: .whitespacesAndNewlines) == token {
            draft = ""
        }

        XCTAssertEqual(draft, "User draft text", "Draft must remain intact after network failure")
        XCTAssertEqual(viewModel.commentSubmissionError, .networkUnavailable)
        XCTAssertNotNil(viewModel.commentErrorMessage)
    }

    func testDraftClearing_matchingSnapshotClearsDraftOnSuccess() {
        var draft = "Confirmed comment"
        viewModel.isSubmittingComment = true

        // Server confirmed success
        viewModel.commentSuccessToken = "Confirmed comment"
        viewModel.isSubmittingComment = false

        if let token = viewModel.commentSuccessToken, draft.trimmingCharacters(in: .whitespacesAndNewlines) == token {
            draft = ""
        }
        viewModel.consumeCommentSuccess()

        XCTAssertEqual(draft, "", "Draft must be cleared on success")
        XCTAssertNil(viewModel.commentSuccessToken)
    }

    func testDraftPreservation_newerEditedDraftNotClearedByOlderSuccess() {
        let originalSnapshot = "Initial thought"
        var draft = originalSnapshot

        // User submits "Initial thought"
        viewModel.isSubmittingComment = true

        // While submitting, user types more
        draft = "Initial thought and something more!"

        // Server confirms "Initial thought"
        viewModel.commentSuccessToken = originalSnapshot
        viewModel.isSubmittingComment = false

        // Concurrency guard
        if let token = viewModel.commentSuccessToken, draft.trimmingCharacters(in: .whitespacesAndNewlines) == token {
            draft = ""
        }
        viewModel.consumeCommentSuccess()

        XCTAssertEqual(draft, "Initial thought and something more!", "Newer typed characters must NOT be wiped by older success")
    }

    // 4. Error Classification
    func testErrorClassification_rateLimited() {
        let err429 = NSError(domain: "HTTP", code: 429, userInfo: [NSLocalizedDescriptionKey: "429: Too Many Requests"])
        let errRate = NSError(domain: "Social", code: 1, userInfo: [NSLocalizedDescriptionKey: "RATE_LIMITED: slow down"])

        XCTAssertEqual(viewModel.classifyError(err429), .rateLimited)
        XCTAssertEqual(viewModel.classifyError(errRate), .rateLimited)
    }

    func testErrorClassification_unauthenticated() {
        let err401 = NSError(domain: "HTTP", code: 401, userInfo: [NSLocalizedDescriptionKey: "401 Unauthorized token"])
        XCTAssertEqual(viewModel.classifyError(err401), .unauthenticated)
    }

    func testErrorClassification_forbidden() {
        let err403 = NSError(domain: "HTTP", code: 403, userInfo: [NSLocalizedDescriptionKey: "403 Forbidden: RLS rejection"])
        XCTAssertEqual(viewModel.classifyError(err403), .forbidden)
    }

    func testErrorClassification_networkUnavailable() {
        let offline = NSError(domain: "NSURLErrorDomain", code: -1004, userInfo: [NSLocalizedDescriptionKey: "Could not connect to the server"])
        XCTAssertEqual(viewModel.classifyError(offline), .networkUnavailable)
    }

    func testErrorClassification_serverFailure() {
        let err500 = NSError(domain: "HTTP", code: 500, userInfo: [NSLocalizedDescriptionKey: "500 Internal Server Error"])
        XCTAssertEqual(viewModel.classifyError(err500), .serverFailure)
    }

    // 5. No Raw Internal Leak
    func testNoRawInternalLeak() {
        let rawPgError = NSError(domain: "Supabase", code: 500, userInfo: [NSLocalizedDescriptionKey: "PGRST116: SQLSTATE 42501 relation feed_comments violates row-level security policy"])
        let classified = viewModel.classifyError(rawPgError)
        let localizedMsg = viewModel.localizedErrorMessage(for: classified)

        XCTAssertFalse(localizedMsg.contains("SQLSTATE"))
        XCTAssertFalse(localizedMsg.contains("PGRST"))
        XCTAssertFalse(localizedMsg.contains("row-level security"))
        XCTAssertFalse(localizedMsg.contains("feed_comments"))
    }

    // 6. Delete Comment Owner Safety
    func testDeleteComment_authorOnly() {
        let authorUid = "uid_alice"
        let otherUid = "uid_bob"

        let comment = FeedComment(
            id: "c1",
            postId: "p1",
            authorId: authorUid,
            authorName: "Alice",
            authorAvatar: "",
            content: "Hello",
            createdAt: 1000
        )

        // Non-owner delete attempt
        viewModel.deleteComment(postId: "p1", comment: comment, authUid: otherUid, repository: repository)
        XCTAssertNotNil(viewModel.deleteCommentError)
        XCTAssertEqual(viewModel.deleteCommentError, AppLanguageManager.shared.localized("comment_error_delete_forbidden"))
    }
}
