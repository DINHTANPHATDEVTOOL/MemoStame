package com.mipastudio.memostamp.feature.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.ThemeSelectorModalSheet
import com.mipastudio.memostamp.data.local.StampEntity
import androidx.compose.material.icons.outlined.Palette
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampVaultScreen(
    onNavigateToCamera: () -> Unit,
    onStampClick: (StampEntity) -> Unit,
    onCollectionClick: (String) -> Unit = {},
    viewModel: StampVaultViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadStamps(context) }

    if (showThemeSelector) {
        ThemeSelectorModalSheet(onDismiss = { showThemeSelector = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vault", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text("Kho tem ký ức của bạn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showThemeSelector = true }) {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = "Chọn giao diện",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            if (isSearchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is VaultUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed, strokeWidth = 3.dp)
            }

            is VaultUiState.Empty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentRedSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = AccentRed)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your vault is quiet", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        "Press a moment into a stamp and it will live here.",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onNavigateToCamera,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open camera")
                    }
                }
            }

            is VaultUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message, color = AccentRed)
            }

            is VaultUiState.Success -> {
                val stamps = state.stamps
                val filteredStamps = remember(stamps, selectedFilter, searchQuery) {
                    stamps
                        .let { list ->
                            when (selectedFilter) {
                                1 -> list.filter { it.favorite }
                                2 -> list.filter { it.collectionId != null }
                                else -> list
                            }
                        }
                        .filter {
                            searchQuery.isBlank() ||
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.note.contains(searchQuery, ignoreCase = true) ||
                                (it.location?.contains(searchQuery, ignoreCase = true) == true)
                        }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search memories") },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceWhite,
                                    unfocusedContainerColor = SurfaceWhite,
                                    disabledContainerColor = SurfaceWhite,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${filteredStamps.size} memories",
                                color = SecondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Tất cả", "Yêu thích", "Albums").forEachIndexed { index, label ->
                                    val selected = selectedFilter == index
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else SecondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (selected) SurfaceDark else Color.Transparent)
                                            .clickable { selectedFilter = index }
                                            .padding(horizontal = 11.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (selectedFilter == 2) {
                            val repository = remember(context) { com.mipastudio.memostamp.data.repository.StampRepository.getInstance(context) }
                            val roomCollections by repository.observeCollections().collectAsState(initial = emptyList())
                            val scope = rememberCoroutineScope()

                            Spacer(modifier = Modifier.height(10.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(roomCollections.size) { idx ->
                                    val col = roomCollections[idx]
                                    val isPrivate = col.privacy == "ONLY_ME"
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = SurfaceWhite,
                                        shadowElevation = 1.dp,
                                        modifier = Modifier.width(140.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(col.iconEmoji ?: "📁", fontSize = 18.sp)
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isPrivate) AccentRedSoft else SurfaceSoft,
                                                    modifier = Modifier.clickable {
                                                        scope.launch {
                                                            repository.toggleCollectionPrivacy(col.id)
                                                        }
                                                    }
                                                ) {
                                                    Text(
                                                        text = if (isPrivate) "🔒 Mình tôi" else "👥 Bạn bè",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPrivate) AccentRed else SuccessGreen,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(col.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryText, maxLines = 1)
                                            Text("${col.targetCount} tem mộc", fontSize = 10.sp, color = SecondaryText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredStamps, key = { it.id }) { stamp ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onStampClick(stamp) },
                                contentAlignment = Alignment.Center
                            ) {
                                val imageModel = remember(stamp.stampImagePath) {
                                    val file = File(stamp.stampImagePath)
                                    if (file.exists() && file.length() > 0) file else stamp.stampImagePath
                                }
                                Image(
                                    painter = rememberAsyncImagePainter(model = imageModel),
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
    }
}
