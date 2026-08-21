package com.mipastudio.memostamp.feature.vault

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.core.ui.StampGeometry
import com.mipastudio.memostamp.core.util.StampExportHelper
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.feature.vault.components.EnvelopeShareModal
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampDetailScreen(
    stampId: String,
    onNavigateBack: () -> Unit,
    onEditStamp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stamp by remember { mutableStateOf<StampEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }

    LaunchedEffect(stampId) {
        loading = true
        stamp = StampRepository.getInstance(context).getStampById(stampId).getOrNull()
        loading = false
    }

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (stamp != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                containerColor = SurfaceWhite
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete memory", color = AccentRed) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        }
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AccentRed, strokeWidth = 3.dp) }

            stamp == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Memory not found", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onNavigateBack) { Text("Go back", color = AccentRed) }
                }
            }

            else -> {
                val s = stamp!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val imageModel = remember(s.stampImagePath) {
                        File(s.stampImagePath).takeIf { it.exists() && it.length() > 0 } ?: s.stampImagePath
                    }
                    Image(
                        painter = rememberAsyncImagePainter(imageModel),
                        contentDescription = s.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.70f)
                            .aspectRatio(StampGeometry.ASPECT_RATIO)
                            .shadow(12.dp, RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(30.dp))
                    Text(
                        s.title.ifBlank { "Untitled memory" },
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(7.dp))
                    val formattedDate = remember(s.memoryDate) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(s.memoryDate))
                    }
                    Text(
                        listOfNotNull(formattedDate, s.location?.takeIf { it.isNotBlank() }).joinToString("  •  "),
                        color = SecondaryText,
                        fontSize = 12.sp
                    )

                    if (s.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Text(
                            s.note,
                            color = PrimaryText,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DetailAction(Icons.Outlined.Edit, "Edit", PrimaryText) { onEditStamp(s.id) }
                            DetailAction(Icons.Outlined.Send, "Send", AccentRed) { showShareModal = true }
                            DetailAction(Icons.Outlined.Share, "Export", AccentBlue) {
                                val bmp = BitmapFactory.decodeFile(s.stampImagePath)
                                if (bmp != null) {
                                    StampExportHelper.exportToGallery(
                                        context,
                                        bmp,
                                        "STAMP_${s.id}",
                                        StampExportHelper.ExportMode.STAMP_IMAGE
                                    )
                                    Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                    Text("Created by you", color = TertiaryText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    val currentStamp = stamp
    if (showDeleteConfirm && currentStamp != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this memory?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes the stamp and its local files from your vault.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = StampRepository.getInstance(context).deleteStamp(currentStamp.id)
                        showDeleteConfirm = false
                        result.fold(
                            onSuccess = { onNavigateBack() },
                            onFailure = { Toast.makeText(context, it.message ?: "Delete failed", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }) { Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            containerColor = SurfaceWhite
        )
    }

    if (showShareModal && currentStamp != null) {
        EnvelopeShareModal(stamp = currentStamp, onDismiss = { showShareModal = false })
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}
