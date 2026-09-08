package com.mipastudio.memostamp.feature.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.data.repository.FeedRepository
import com.mipastudio.memostamp.data.repository.UserAuthRepository
import com.mipastudio.memostamp.domain.model.CommentSubmissionError
import com.mipastudio.memostamp.domain.model.FeedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: FeedPost? = null,
    val isLoading: Boolean = true,
    val showMenu: Boolean = false,
    val errorMessage: String? = null,
    val postUnavailable: Boolean = false,
    val isSubmittingComment: Boolean = false,
    val commentSubmissionError: CommentSubmissionError? = null,
    val commentErrorMessage: String? = null,
    val lastSubmittedDraft: String? = null,
    val commentSuccessToken: String? = null,
    val isDeletingCommentId: String? = null,
    val deleteCommentError: String? = null
)

class PostDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    @Volatile
    private var lastSubmissionAuthUid: String? = null

    fun loadPost(context: Context, postId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, postUnavailable = false) }
            val repo = FeedRepository.getInstance(context)
            val post = repo.getPostById(postId)
            if (post != null) {
                _uiState.update { it.copy(post = post, isLoading = false, postUnavailable = false) }
            } else {
                _uiState.update {
                    it.copy(
                        post = null,
                        isLoading = false,
                        postUnavailable = true,
                        errorMessage = "Post unavailable"
                    )
                }
            }
        }
    }

    fun like(context: Context, postId: String) {
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            repo.like(postId)
            val updatedPost = repo.getPostById(postId)
            _uiState.update { it.copy(post = updatedPost) }
        }
    }

    fun toggleLike(context: Context, postId: String) {
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            repo.toggleLike(postId)
            val updatedPost = repo.getPostById(postId)
            _uiState.update { it.copy(post = updatedPost) }
        }
    }

    fun addComment(context: Context, postId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) {
            _uiState.update {
                it.copy(
                    commentSubmissionError = CommentSubmissionError.VALIDATION_FAILED,
                    commentErrorMessage = "Comment must be between 1 and 500 characters"
                )
            }
            return
        }

        // Deduplication / Submit lock: prevent rapid tap duplicate submissions
        if (_uiState.value.isSubmittingComment) {
            return
        }

        val authRepo = UserAuthRepository.getInstance(context)
        val currentUid = authRepo.authUserId.value?.trim().orEmpty()
        lastSubmissionAuthUid = currentUid

        val submittedSnapshot = trimmed
        _uiState.update {
            it.copy(
                isSubmittingComment = true,
                commentSubmissionError = null,
                commentErrorMessage = null,
                lastSubmittedDraft = submittedSnapshot
            )
        }

        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            try {
                repo.addComment(postId, submittedSnapshot)

                // Account-switch race guard: verify auth hasn't changed since request started
                val currentActiveUid = authRepo.authUserId.value?.trim().orEmpty()
                if (currentActiveUid != lastSubmissionAuthUid) {
                    _uiState.update { it.copy(isSubmittingComment = false) }
                    return@launch
                }

                // Refresh post data; mutation is confirmed even if refresh encounters transient error
                val updatedPost = try {
                    repo.getPostById(postId)
                } catch (_: Exception) {
                    null
                } ?: _uiState.value.post

                _uiState.update {
                    it.copy(
                        post = updatedPost,
                        isSubmittingComment = false,
                        commentSubmissionError = null,
                        commentErrorMessage = null,
                        commentSuccessToken = submittedSnapshot
                    )
                }
            } catch (e: Throwable) {
                // Account-switch race guard: discard stale response
                val currentActiveUid = authRepo.authUserId.value?.trim().orEmpty()
                if (currentActiveUid != lastSubmissionAuthUid) {
                    _uiState.update { it.copy(isSubmittingComment = false) }
                    return@launch
                }

                val errType = classifyError(e)
                val errMsg = e.message.orEmpty()
                _uiState.update {
                    it.copy(
                        isSubmittingComment = false,
                        commentSubmissionError = errType,
                        commentErrorMessage = errMsg
                    )
                }
            }
        }
    }

    fun consumeCommentSuccess() {
        _uiState.update { it.copy(commentSuccessToken = null) }
    }

    fun clearCommentError() {
        _uiState.update { it.copy(commentSubmissionError = null, commentErrorMessage = null) }
    }

    fun deleteComment(
        context: Context,
        postId: String,
        commentId: String,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingCommentId = commentId, deleteCommentError = null) }
            val repo = FeedRepository.getInstance(context)
            val res = repo.deleteComment(commentId)
            if (res.isSuccess) {
                val updatedPost = repo.getPostById(postId)
                _uiState.update { it.copy(post = updatedPost, isDeletingCommentId = null) }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to delete comment"
                _uiState.update { it.copy(isDeletingCommentId = null, deleteCommentError = err) }
                onError?.invoke(err)
            }
        }
    }

    fun removePostFromFeed(
        context: Context,
        postId: String,
        onDone: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            val res = repo.removePostFromFeed(postId)
            if (res.isSuccess) {
                onDone()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to remove post"
                _uiState.update { it.copy(errorMessage = err) }
                onError?.invoke(err)
            }
        }
    }

    fun deleteMemory(
        context: Context,
        stampId: String,
        onDone: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            val res = repo.deleteMemory(stampId)
            if (res.isSuccess) {
                onDone()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to delete memory"
                _uiState.update { it.copy(errorMessage = err) }
                onError?.invoke(err)
            }
        }
    }

    internal fun classifyError(e: Throwable): CommentSubmissionError {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("429") || msg.contains("RATE_LIMITED", ignoreCase = true) ->
                CommentSubmissionError.RATE_LIMITED
            msg.contains("401") || msg.contains("unauthenticated", ignoreCase = true) ||
            msg.contains("unauthorized", ignoreCase = true) || (e is IllegalStateException && msg.contains("sign in", ignoreCase = true)) ->
                CommentSubmissionError.UNAUTHENTICATED
            msg.contains("403") || msg.contains("forbidden", ignoreCase = true) || e is SecurityException ->
                CommentSubmissionError.FORBIDDEN
            e is java.io.IOException || msg.contains("network", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) || msg.contains("connection", ignoreCase = true) ->
                CommentSubmissionError.NETWORK_UNAVAILABLE
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ||
            msg.contains("server", ignoreCase = true) ->
                CommentSubmissionError.SERVER_FAILURE
            else -> CommentSubmissionError.UNKNOWN
        }
    }
}

