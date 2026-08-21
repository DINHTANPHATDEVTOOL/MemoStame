package com.mipastudio.memostamp.core.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InAppBanner(
    val id: String,
    val title: String,
    val message: String,
    val avatarUrl: String? = null,
    val iconEmoji: String = "🔔",
    val targetRoute: String,
    val senderName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object InAppNotificationManager {

    private val _currentBanner = MutableStateFlow<InAppBanner?>(null)
    val currentBanner: StateFlow<InAppBanner?> = _currentBanner.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var autoDismissJob: Job? = null

    fun show(banner: InAppBanner, autoDismissMs: Long = 4500L) {
        autoDismissJob?.cancel()
        _currentBanner.value = banner

        autoDismissJob = coroutineScope.launch {
            delay(autoDismissMs)
            if (_currentBanner.value?.id == banner.id) {
                _currentBanner.value = null
            }
        }
    }

    fun dismiss() {
        autoDismissJob?.cancel()
        _currentBanner.value = null
    }
}
