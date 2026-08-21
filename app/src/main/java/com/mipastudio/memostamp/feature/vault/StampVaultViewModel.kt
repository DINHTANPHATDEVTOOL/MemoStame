package com.mipastudio.memostamp.feature.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VaultUiState {
    object Loading : VaultUiState
    object Empty : VaultUiState
    data class Success(val stamps: List<StampEntity>) : VaultUiState
    data class Error(val message: String) : VaultUiState
}

class StampVaultViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _selectedStamp = MutableStateFlow<StampEntity?>(null)
    val selectedStamp: StateFlow<StampEntity?> = _selectedStamp.asStateFlow()

    private var observeJob: Job? = null

    fun loadStamps(context: Context) {
        if (observeJob != null && observeJob?.isActive == true) return

        observeJob = viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            try {
                val repo = StampRepository.getInstance(context)
                repo.observeStamps().collect { list ->
                    if (list.isEmpty()) {
                        _uiState.value = VaultUiState.Empty
                    } else {
                        _uiState.value = VaultUiState.Success(list)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = VaultUiState.Error(e.message ?: "Failed to load stamps")
            }
        }
    }

    fun selectStamp(stamp: StampEntity) {
        _selectedStamp.value = stamp
    }

    fun deleteStamp(context: Context, stampId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val repo = StampRepository.getInstance(context)
                val result = repo.deleteStamp(stampId)
                result.onSuccess {
                    if (_selectedStamp.value?.id == stampId) {
                        _selectedStamp.value = null
                    }
                    onDeleted()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
