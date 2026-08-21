package com.mipastudio.memostamp.feature.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mipastudio.memostamp.core.repository.SampleDataRepository
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.data.remote.CloudSyncEngine
import com.mipastudio.memostamp.data.remote.UserAuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember(context) { com.mipastudio.memostamp.data.repository.StampRepository.getInstance(context) }
    val authRepo = remember(context) { UserAuthRepository.getInstance(context) }
    val syncEngine = remember(context) { CloudSyncEngine.getInstance(context) }

    val currentUser by authRepo.currentUser.collectAsState()
    val syncStatus by syncEngine.syncStatus.collectAsState()
    val roomStamps by repo.observeStamps().collectAsState(initial = emptyList())
    val passport = SampleDataRepository.samplePassport

    var showQrModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Profile", fontSize = 20.sp, fontFamily = AppDisplayFontFamily, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("Your memory passport", fontSize = 11.sp, color = SecondaryText)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = { showQrModal = true }) {
                        Icon(Icons.Outlined.QrCode, contentDescription = "QR Code", tint = PrimaryText)
                    }
                    IconButton(onClick = { showSettingsModal = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        },
        containerColor = WarmPaperBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            AsyncImage(
                model = currentUser.avatarUrl,
                contentDescription = currentUser.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(currentUser.displayName, fontSize = 24.sp, fontFamily = AppDisplayFontFamily, fontWeight = FontWeight.Bold, color = PrimaryText)
            Text("@${currentUser.username}", fontSize = 13.sp, fontFamily = AppSansFontFamily, color = SecondaryText)

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Click Google Sign-In Button
            Button(
                onClick = {
                    com.mipastudio.memostamp.core.auth.FirebaseAuthManager.performGoogleSignIn(
                        context = context,
                        onSuccess = { profile ->
                            Toast.makeText(context, "Welcome ${profile.displayName}! 🌐", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, "Sign in failed: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(2.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("Sign in with Google", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cloud Sync Indicator Chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        val res = syncEngine.performFullCloudSync()
                        res.fold(
                            onSuccess = { Toast.makeText(context, "Cloud sync complete! ☁️", Toast.LENGTH_SHORT).show() },
                            onFailure = { err -> Toast.makeText(context, "Sync failed: ${err.message}", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (syncStatus.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentRed, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.CloudSync, contentDescription = "Sync", tint = SageGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (syncStatus.isSyncing) "Syncing…" else "Cloud sync",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Minimal Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${roomStamps.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text("Stamps", fontSize = 12.sp, color = SecondaryText)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${passport.stats.collections}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text("Collections", fontSize = 12.sp, color = SecondaryText)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${passport.stats.friends}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Text("Friends", fontSize = 12.sp, color = SecondaryText)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Passport Visas
            Text("Memory passport", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    passport.visas.forEach { visa ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(visa.countryOrCity, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                Text(visa.date, fontSize = 12.sp, color = SecondaryText)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarmPaperBg
                            ) {
                                Text(visa.category, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentRed, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        HorizontalDivider(color = UIBorder, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // QR Modal
        if (showQrModal) {
            AlertDialog(
                onDismissRequest = { showQrModal = false },
                title = { Text("Your Passport QR", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan to connect on MemoStamp", fontSize = 13.sp, color = SecondaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(WarmPaperBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📱 QR CODE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SecondaryText)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQrModal = false }) { Text("Close") }
                },
                containerColor = SurfaceWhite
            )
        }

        // Settings Modal
        if (showSettingsModal) {
            var editName by remember { mutableStateOf(currentUser.displayName) }
            AlertDialog(
                onDismissRequest = { showSettingsModal = false },
                title = { Text("Account & Cloud Settings", fontWeight = FontWeight.Bold, color = PrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Database: Room v4 (Active)", fontSize = 13.sp, color = SecondaryText)
                        Text("User ID: ${currentUser.userId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SecondaryText)
                        TextButton(
                            onClick = {
                                if (editName.isNotBlank()) {
                                    authRepo.updateDisplayName(editName)
                                }
                                coroutineScope.launch {
                                    repo.cleanupExpiredDrafts()
                                    showSettingsModal = false
                                    Toast.makeText(context, "Settings updated & draft cache cleared!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Save Changes & Clear Cache", color = AccentRed)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsModal = false }) { Text("Close") }
                },
                containerColor = SurfaceWhite
            )
        }
    }
}

