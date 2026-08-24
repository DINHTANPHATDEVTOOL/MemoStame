package com.mipastudio.memostamp.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.data.repository.FeedRepository
import com.mipastudio.memostamp.domain.model.Circle
import com.mipastudio.memostamp.domain.model.FeedPost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FeedTab {
    FRIENDS,
    CIRCLES
}

data class HomeUiState(
    val activeTab: FeedTab = FeedTab.FRIENDS,
    val selectedCircleId: String? = null,
    val circles: List<Circle> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingCircle: Boolean = false,
    val activeCommentsPostId: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val feedRepo = FeedRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            feedRepo.ensureDefaultFeedData()
        }

        observeCircles()
        setupFeedStream()
    }

    private fun observeCircles() {
        viewModelScope.launch {
            feedRepo.observeCircles().collect { circleList ->
                _uiState.update { state ->
                    val defaultCircle = state.selectedCircleId ?: circleList.firstOrNull()?.id
                    state.copy(
                        circles = circleList,
                        selectedCircleId = defaultCircle
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupFeedStream() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.activeTab }.distinctUntilChanged(),
                _uiState.map { it.selectedCircleId }.distinctUntilChanged()
            ) { tab, circleId ->
                Pair(tab, circleId)
            }.flatMapLatest { (tab, circleId) ->
                _uiState.update { it.copy(isLoading = true) }
                if (tab == FeedTab.FRIENDS) {
                    feedRepo.observeFriendsFeed()
                } else {
                    feedRepo.observeCircleFeed(circleId ?: "")
                }
            }.collect { postList ->
                _uiState.update { it.copy(posts = postList, isLoading = false) }
            }
        }
    }

    fun selectTab(tab: FeedTab) {
        if (_uiState.value.activeTab != tab) {
            _uiState.update { it.copy(activeTab = tab) }
        }
    }

    fun selectCircle(circleId: String) {
        if (_uiState.value.selectedCircleId != circleId) {
            _uiState.update { it.copy(selectedCircleId = circleId) }
        }
    }

    fun like(postId: String) {
        viewModelScope.launch {
            feedRepo.like(postId)
        }
    }

    fun toggleLike(postId: String) {
        viewModelScope.launch {
            feedRepo.toggleLike(postId)
        }
    }

    fun addComment(postId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) return
        viewModelScope.launch {
            feedRepo.addComment(postId, trimmed)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            feedRepo.deleteComment(commentId)
        }
    }

    fun openCommentsSheet(postId: String) {
        _uiState.update { it.copy(activeCommentsPostId = postId) }
    }

    fun closeCommentsSheet() {
        _uiState.update { it.copy(activeCommentsPostId = null) }
    }

    fun markPostSeen(postId: String) {
        viewModelScope.launch {
            val userId = com.mipastudio.memostamp.data.repository.UserAuthRepository.getInstance(getApplication()).currentUser.value.userId
            feedRepo.markPostSeen(postId, userId)
        }
    }

    fun createCircle(name: String, icon: String, memberIds: List<String>) {
        viewModelScope.launch {
            val newCircle = feedRepo.createCircle(name, icon, memberIds)
            _uiState.update {
                it.copy(
                    selectedCircleId = newCircle.id,
                    activeTab = FeedTab.CIRCLES,
                    isCreatingCircle = false
                )
            }
        }
    }

    fun setCreatingCircle(isCreating: Boolean) {
        _uiState.update { it.copy(isCreatingCircle = isCreating) }
    }
}
