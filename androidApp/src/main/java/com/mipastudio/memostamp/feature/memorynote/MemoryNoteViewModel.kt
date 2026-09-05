package com.mipastudio.memostamp.feature.memorynote

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.FeedRepository
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.AudienceType
import com.mipastudio.memostamp.domain.model.StampDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryNoteUiState(
    val draft: StampDraft? = null,
    val draftId: String? = null,
    val title: String = "",
    val note: String = "",
    val mood: String = "✨",
    val location: String = "",
    val collectionId: String? = null,
    val audienceType: AudienceType = AudienceType.FRIENDS,
    val selectedCircleId: String? = null,
    val selectedCircleName: String? = null,
    val replyToPostId: String? = null,
    val isSaving: Boolean = false,
    val nearbyPlaces: List<String> = emptyList(),
    val locationSearchQuery: String = "",
    val categoryFilter: String = "ALL"
)

class MemoryNoteViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryNoteUiState())
    val uiState: StateFlow<MemoryNoteUiState> = _uiState.asStateFlow()

    fun setReplyToPostId(postId: String?) {
        _uiState.update { it.copy(replyToPostId = postId) }
    }

    fun loadDraftById(context: Context, draftId: String) {
        viewModelScope.launch {
            val repo = StampRepository.getInstance(context)
            val draft = repo.getDraft(draftId)
            if (draft != null) {
                val loc = draft.location ?: ""
                _uiState.value = _uiState.value.copy(
                    draft = draft,
                    draftId = draftId,
                    title = draft.title.ifBlank { "" },
                    note = draft.note,
                    mood = draft.mood ?: "✨",
                    location = loc,
                    collectionId = draft.collectionId
                )
                if (loc.isBlank()) {
                    fetchLiveLocation(context)
                }
                loadNearbyPlaces(context)
            }
        }
    }

    fun initialize(context: Context, draft: StampDraft, draftId: String? = null) {
        val loc = draft.location ?: ""
        _uiState.value = _uiState.value.copy(
            draft = draft,
            draftId = draftId,
            title = draft.title.ifBlank { "" },
            note = draft.note,
            mood = draft.mood ?: "✨",
            location = loc,
            collectionId = draft.collectionId
        )
        if (loc.isBlank()) {
            fetchLiveLocation(context)
        }
        loadNearbyPlaces(context)
    }

    fun fetchLiveLocation(context: Context) {
        com.mipastudio.memostamp.core.location.LocationHelper.fetchCurrentLocation(context) { liveLoc ->
            _uiState.update { it.copy(location = liveLoc) }
        }
        loadNearbyPlaces(context)
    }

    fun loadNearbyPlaces(context: Context) {
        val query = _uiState.value.locationSearchQuery
        val cat = _uiState.value.categoryFilter
        com.mipastudio.memostamp.core.location.LocationHelper.searchPlaces(context, query, cat) { places ->
            _uiState.update { it.copy(nearbyPlaces = places) }
        }
    }

    fun searchLocation(context: Context, query: String) {
        _uiState.update { it.copy(locationSearchQuery = query) }
        loadNearbyPlaces(context)
    }

    fun setCategoryFilter(context: Context, filter: String) {
        _uiState.update { it.copy(categoryFilter = filter) }
        loadNearbyPlaces(context)
    }

    fun updateTitle(newTitle: String) { _uiState.update { it.copy(title = newTitle) } }
    fun updateNote(newNote: String) { _uiState.update { it.copy(note = newNote) } }
    fun updateMood(newMood: String) { _uiState.update { it.copy(mood = newMood) } }
    fun updateLocation(newLoc: String) { _uiState.update { it.copy(location = newLoc) } }
    fun updateCollectionId(newCol: String?) { _uiState.update { it.copy(collectionId = newCol) } }

    fun updateAudience(audience: AudienceType) {
        _uiState.update { it.copy(audienceType = audience) }
    }

    fun updateCircle(circleId: String?, circleName: String?) {
        _uiState.update { it.copy(selectedCircleId = circleId, selectedCircleName = circleName) }
    }

    fun saveMemory(
        context: Context,
        onSuccess: (StampEntity) -> Unit
    ) {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val repository = StampRepository.getInstance(context)
                val feedRepository = FeedRepository.getInstance(context)

                val updatedDraft = draft.copy(
                    title = state.title.ifBlank { "Untitled Memory" },
                    note = state.note,
                    mood = state.mood,
                    location = state.location.ifBlank { null },
                    collectionId = state.collectionId
                )
                val result = repository.saveStamp(updatedDraft, state.draftId)
                result.fold(
                    onSuccess = { entity ->
                        // Only create a feed post if explicit reply to post is requested
                        if (!state.replyToPostId.isNullOrBlank()) {
                            try {
                                feedRepository.createPostFromStamp(
                                    stampEntity = entity,
                                    audienceType = state.audienceType,
                                    circleId = state.selectedCircleId,
                                    circleName = state.selectedCircleName,
                                    replyToPostId = state.replyToPostId
                                )
                                feedRepository.reconcileFeedFromCloud()
                                onSuccess(entity)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Lưu tem thành công nhưng gửi phản hồi thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            onSuccess(entity)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "Failed to save stamp: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error saving stamp", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun discardDraft(
        context: Context,
        targetDraftId: String?,
        onDone: () -> Unit
    ) {
        val idToDiscard = targetDraftId ?: uiState.value.draftId
        if (idToDiscard.isNullOrBlank()) {
            onDone()
            return
        }
        viewModelScope.launch {
            try {
                StampRepository.getInstance(context).removeDraft(idToDiscard)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onDone()
            }
        }
    }
}
