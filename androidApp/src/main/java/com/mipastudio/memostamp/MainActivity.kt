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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import com.mipastudio.memostamp.core.i18n.AppLanguageManager
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

    override fun attachBaseContext(newBase: android.content.Context) {
        val languageManager = AppLanguageManager.getInstance(newBase)
        val config = languageManager.createLocalizedConfiguration(newBase.resources.configuration)
        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val languageManager = AppLanguageManager.getInstance(this)
        ThemeManager.init(this)
        enableEdgeToEdge()

        checkNotificationPermission()
        extractIntentExtras(intent)

        setContent {
            val currentMode by languageManager.currentMode.collectAsState()
            val baseConfiguration = LocalConfiguration.current
            val localizedConfiguration = remember(currentMode, baseConfiguration) {
                languageManager.createLocalizedConfiguration(baseConfiguration, currentMode)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration
            ) {
                MemoStampTheme {
                    MemoStampNavGraph(
                        targetScreen = targetScreenState.value,
                        onTargetScreenHandled = { targetScreenState.value = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            com.mipastudio.memostamp.data.repository.ChatRepository.getInstance(applicationContext).onAppForeground()
        } catch (_: Throwable) {}
        try {
            com.mipastudio.memostamp.data.repository.FeedRepository.getInstance(applicationContext).onAppForeground()
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

        val dataUri = intent?.dataString
        if (!dataUri.isNullOrBlank() && dataUri.startsWith("memostamp://", ignoreCase = true)) {
            com.mipastudio.memostamp.data.local.PasswordRecoveryCoordinator.getInstance().handleDeepLink(dataUri)
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
