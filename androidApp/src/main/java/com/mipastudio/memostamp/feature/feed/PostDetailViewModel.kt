package com.mipastudio.memostamp.feature.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.data.repository.FeedRepository
import com.mipastudio.memostamp.domain.model.FeedPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: FeedPost? = null,
    val isLoading: Boolean = true,
    val showMenu: Boolean = false
)

class PostDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    fun loadPost(context: Context, postId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val repo = FeedRepository.getInstance(context)
            val post = repo.getPostById(postId)
            _uiState.update { it.copy(post = post, isLoading = false) }
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
        if (trimmed.isEmpty() || trimmed.length > 500) return
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            repo.addComment(postId, trimmed)
            val updatedPost = repo.getPostById(postId)
            _uiState.update { it.copy(post = updatedPost) }
        }
    }

    fun deleteComment(context: Context, postId: String, commentId: String) {
        viewModelScope.launch {
            val repo = FeedRepository.getInstance(context)
            repo.deleteComment(commentId)
            val updatedPost = repo.getPostById(postId)
            _uiState.update { it.copy(post = updatedPost) }
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
}
