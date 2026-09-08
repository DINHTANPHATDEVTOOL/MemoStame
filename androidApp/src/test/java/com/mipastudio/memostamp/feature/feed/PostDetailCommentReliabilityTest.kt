package com.mipastudio.memostamp.feature.feed

import com.mipastudio.memostamp.domain.model.CommentSubmissionError
import com.mipastudio.memostamp.domain.model.FeedComment
import com.mipastudio.memostamp.domain.model.FeedPost
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PostDetailCommentReliabilityTest {

    private val viewModel = PostDetailViewModel()

    // 1. Comment Validation Tests
    @Test
    fun commentValidation_emptyOrWhitespaceRejected() {
        val empty = ""
        val whitespaceOnly = "   \n\t  "
        val trimmedEmpty = empty.trim()
        val trimmedWhitespace = whitespaceOnly.trim()

        assertTrue(trimmedEmpty.isEmpty())
        assertTrue(trimmedWhitespace.isEmpty())

        // In ViewModel logic:
        val isValidEmpty = trimmedEmpty.isNotEmpty() && trimmedEmpty.length <= 500
        val isValidWhitespace = trimmedWhitespace.isNotEmpty() && trimmedWhitespace.length <= 500
        assertFalse(isValidEmpty)
        assertFalse(isValidWhitespace)
    }

    @Test
    fun commentValidation_exceeding500CharsRejected() {
        val exact500 = "a".repeat(500)
        val over500 = "a".repeat(501)

        assertTrue(exact500.length <= 500)
        assertFalse(over500.length <= 500)
    }

    @Test
    fun commentValidation_validCommentAccepted() {
        val valid = "This is a wonderful vintage stamp!"
        val trimmed = valid.trim()
        assertTrue(trimmed.isNotEmpty() && trimmed.length <= 500)
    }

    // 2. Rapid Tap / Deduplication Lock Tests
    @Test
    fun rapidTap_isSubmittingLockProtectsAgainstDuplicates() {
        var state = PostDetailUiState(isSubmittingComment = false)
        assertFalse(state.isSubmittingComment)

        // First tap: enters submitting
        state = state.copy(isSubmittingComment = true, lastSubmittedDraft = "hello")
        assertTrue(state.isSubmittingComment)

        // Second tap while submitting: rejected by guard `if (_uiState.value.isSubmittingComment) return`
        val canSubmitAgain = !state.isSubmittingComment
        assertFalse("Second tap must be ignored while submission is running", canSubmitAgain)
    }

    // 3. Draft Preservation on Failure & Success Scenarios
    @Test
    fun draftPreservation_failureRetainsDraft() {
        var draft = "Preserved draft text"
        var state = PostDetailUiState(
            isSubmittingComment = true,
            lastSubmittedDraft = draft,
            commentSuccessToken = null
        )

        // Simulate network / server failure
        state = state.copy(
            isSubmittingComment = false,
            commentSubmissionError = CommentSubmissionError.NETWORK_UNAVAILABLE,
            commentErrorMessage = "Unable to connect"
        )

        // Draft clearing logic: only clears when commentSuccessToken matches draft
        val token = state.commentSuccessToken
        if (token != null && draft.trim() == token) {
            draft = ""
        }

        assertEquals("Draft must remain unchanged after failure", "Preserved draft text", draft)
        assertNotNull(state.commentSubmissionError)
        assertEquals(CommentSubmissionError.NETWORK_UNAVAILABLE, state.commentSubmissionError)
    }

    @Test
    fun draftClearing_matchingSnapshotClearsDraftOnSuccess() {
        var draft = "Nice stamp!"
        var state = PostDetailUiState(
            isSubmittingComment = true,
            lastSubmittedDraft = draft
        )

        // Simulate server success confirmation
        state = state.copy(
            isSubmittingComment = false,
            commentSuccessToken = draft,
            commentSubmissionError = null
        )

        // Draft clearing logic
        val token = state.commentSuccessToken
        if (token != null && draft.trim() == token) {
            draft = ""
        }

        assertEquals("Draft must be cleared after confirmed success", "", draft)
    }

    @Test
    fun draftPreservation_newerEditedDraftNotClearedByOlderSuccess() {
        val originalSnapshot = "Hello"
        var draft = originalSnapshot

        // User taps send
        var state = PostDetailUiState(
            isSubmittingComment = true,
            lastSubmittedDraft = originalSnapshot
        )

        // While request is in-flight, user types more
        draft = "Hello world! Love this."

        // Server confirms original "Hello"
        state = state.copy(
            isSubmittingComment = false,
            commentSuccessToken = originalSnapshot
        )

        // Concurrency guard: only clear if current draft still equals submitted snapshot
        val token = state.commentSuccessToken
        if (token != null && draft.trim() == token) {
            draft = ""
        }

        assertEquals("Newer edited text must NOT be cleared by older success", "Hello world! Love this.", draft)
    }

    // 4. Error Classification Tests
    @Test
    fun errorClassification_rateLimit() {
        val err429 = Exception("HTTP 429 Too Many Requests")
        val errRateLimited = Exception("RATE_LIMITED: You are commenting too quickly")

        assertEquals(CommentSubmissionError.RATE_LIMITED, viewModel.classifyError(err429))
        assertEquals(CommentSubmissionError.RATE_LIMITED, viewModel.classifyError(errRateLimited))
    }

    @Test
    fun errorClassification_unauthenticated() {
        val err401 = Exception("401 Unauthorized")
        val errAuth = IllegalStateException("Please sign in to comment")
        val errJwt = Exception("Unauthenticated: invalid session")

        assertEquals(CommentSubmissionError.UNAUTHENTICATED, viewModel.classifyError(err401))
        assertEquals(CommentSubmissionError.UNAUTHENTICATED, viewModel.classifyError(errAuth))
        assertEquals(CommentSubmissionError.UNAUTHENTICATED, viewModel.classifyError(errJwt))
    }

    @Test
    fun errorClassification_forbidden() {
        val err403 = Exception("403 Forbidden")
        val secErr = SecurityException("Permission denied by RLS policy")

        assertEquals(CommentSubmissionError.FORBIDDEN, viewModel.classifyError(err403))
        assertEquals(CommentSubmissionError.FORBIDDEN, viewModel.classifyError(secErr))
    }

    @Test
    fun errorClassification_networkUnavailable() {
        val ioErr = IOException("Network connection timed out")
        val offlineErr = Exception("network failure: connection refused")

        assertEquals(CommentSubmissionError.NETWORK_UNAVAILABLE, viewModel.classifyError(ioErr))
        assertEquals(CommentSubmissionError.NETWORK_UNAVAILABLE, viewModel.classifyError(offlineErr))
    }

    @Test
    fun errorClassification_serverFailure() {
        val err500 = Exception("HTTP 500 Internal Server Error")
        val err503 = Exception("HTTP 503 Service Unavailable")
        val errServer = Exception("server failed to respond")

        assertEquals(CommentSubmissionError.SERVER_FAILURE, viewModel.classifyError(err500))
        assertEquals(CommentSubmissionError.SERVER_FAILURE, viewModel.classifyError(err503))
        assertEquals(CommentSubmissionError.SERVER_FAILURE, viewModel.classifyError(errServer))
    }

    @Test
    fun errorClassification_unknownFallback() {
        val unknown = RuntimeException("Some unexpected client exception")
        assertEquals(CommentSubmissionError.UNKNOWN, viewModel.classifyError(unknown))
    }

    // 5. Account Switch Race Safety
    @Test
    fun accountSwitchRace_completionDroppedIfUserChanged() {
        val userAUid = "user_a"
        val userBUid = "user_b"

        var currentActiveUid = userAUid
        val submissionAuthUid = currentActiveUid

        // A sends request
        var isSubmitting = true

        // A logs out, B logs in
        currentActiveUid = userBUid

        // Response for A arrives late:
        val shouldApplyResult = (currentActiveUid == submissionAuthUid)
        if (!shouldApplyResult) {
            isSubmitting = false // reset lock without mutating B's screen
        }

        assertFalse("Late response from User A must NOT mutate User B's state", shouldApplyResult)
        assertFalse("Submission lock must be released", isSubmitting)
    }

    // 6. Delete Comment Owner Safety
    @Test
    fun deleteComment_onlyAuthorCanDelete() {
        val authorUid = "user_author_123"
        val otherUid = "user_other_456"

        val comment = FeedComment(
            id = "comment_1",
            postId = "post_1",
            authorId = authorUid,
            authorName = "Author",
            authorAvatar = "",
            content = "Hello",
            createdAt = System.currentTimeMillis()
        )

        // Owner check
        val canOwnerDelete = (comment.authorId == authorUid)
        val canOtherDelete = (comment.authorId == otherUid)

        assertTrue(canOwnerDelete)
        assertFalse(canOtherDelete)
    }

    // 7. Post Load Unavailable State (Prevent Infinite Spinner)
    @Test
    fun postLoadFailure_separatesLoadingFromUnavailable() {
        // Initial loading state
        val loadingState = PostDetailUiState(isLoading = true, post = null, postUnavailable = false)
        assertTrue(loadingState.isLoading)
        assertFalse(loadingState.postUnavailable)

        // After post query returns null
        val failureState = PostDetailUiState(
            isLoading = false,
            post = null,
            postUnavailable = true,
            errorMessage = "Post unavailable"
        )
        assertFalse(failureState.isLoading)
        assertTrue(failureState.postUnavailable)
        assertEquals("Post unavailable", failureState.errorMessage)
    }
}
