package com.mipastudio.memostamp.feature.collection

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.data.local.CollectionEntity
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.StampRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    initialCollectionId: String? = null,
    onStampClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { StampRepository.getInstance(context) }
    var collections by remember { mutableStateOf<List<CollectionEntity>>(emptyList()) }
    var stamps by remember { mutableStateOf<List<StampEntity>>(emptyList()) }
    var selectedCollectionId by remember { mutableStateOf<String?>(initialCollectionId) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.ensureDefaultCollections()
        repo.observeCollections().collect { list ->
            collections = list
            if (selectedCollectionId == null && list.isNotEmpty()) selectedCollectionId = list.first().id
        }
    }
    LaunchedEffect(Unit) { repo.observeStamps().collect { stamps = it } }

    val selected = collections.find { it.id == selectedCollectionId } ?: collections.firstOrNull()
    val selectedStamps = selected?.let { col -> stamps.filter { it.collectionId == col.id } }.orEmpty()

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Collections", style = MaterialTheme.typography.headlineLarge)
                        Text("Group memories that belong together", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "New collection", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmPaperBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(collections, key = { it.id }) { col ->
                    val isSelected = selected?.id == col.id
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) SurfaceDark else SurfaceWhite)
                            .clickable { selectedCollectionId = col.id }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(col.iconEmoji ?: "•", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            col.name,
                            color = if (isSelected) Color.White else PrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            selected?.let { col ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.name, style = MaterialTheme.typography.displaySmall)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            col.description ?: "A place for related memories.",
                            color = SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "${selectedStamps.size}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentRed
                    )
                }

                if (col.targetCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (selectedStamps.size.toFloat() / col.targetCount).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = AccentRed,
                        trackColor = SurfaceSoft
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (selectedStamps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("No memories in this collection yet.", color = SecondaryText, fontSize = 13.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(selectedStamps, key = { it.id }) { stamp ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .clickable { onStampClick(stamp.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                val imageFile = File(stamp.stampImagePath)
                                Image(
                                    painter = rememberAsyncImagePainter(if (imageFile.exists()) imageFile else stamp.stampImagePath),
                                    contentDescription = stamp.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            var emoji by remember { mutableStateOf("✈") }
            val emojis = listOf("✈", "☕", "🎓", "☀", "♡", "⛰", "🏖", "✦")

            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New collection", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(emojis) { item ->
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (emoji == item) AccentRedSoft else SurfaceSoft)
                                        .clickable { emoji = item },
                                    contentAlignment = Alignment.Center
                                ) { Text(item, fontSize = 18.sp) }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                scope.launch {
                                    selectedCollectionId = repo.createCollection(name, description, emoji)
                                    showCreateDialog = false
                                    Toast.makeText(context, "Collection created", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
                containerColor = SurfaceWhite
            )
        }
    }
}
