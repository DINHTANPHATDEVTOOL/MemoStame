package com.mipastudio.memostamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.mipastudio.memostamp.ui.theme.MemoStampTheme
import com.mipastudio.memostamp.ui.theme.ThemeManager
import com.mipastudio.memostamp.navigation.MemoStampNavGraph

class MainActivity : ComponentActivity() {

    private val targetScreenState = mutableStateOf<Pair<String?, String?>?>(null)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        enableEdgeToEdge()

        checkNotificationPermission()
        extractIntentExtras(intent)

        setContent {
            MemoStampTheme {
                MemoStampNavGraph(
                    targetScreen = targetScreenState.value,
                    onTargetScreenHandled = { targetScreenState.value = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            com.mipastudio.memostamp.data.repository.ChatRepository.getInstance(applicationContext).onAppForeground()
        } catch (_: Throwable) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentExtras(intent)
    }

    private fun extractIntentExtras(intent: Intent?) {
        val openScreen = intent?.getStringExtra("OPEN_SCREEN")
        val targetUserId = intent?.getStringExtra("TARGET_USER_ID")
        if (!openScreen.isNullOrBlank()) {
            targetScreenState.value = Pair(openScreen, targetUserId)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
