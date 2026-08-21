package com.mipastudio.memostamp.feature.memorynote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.core.location.LocationHelper
import com.mipastudio.memostamp.core.location.LocationPickerModalSheet
import com.mipastudio.memostamp.core.theme.*
import com.mipastudio.memostamp.core.ui.StampGeometry
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.domain.model.AudienceType
import com.mipastudio.memostamp.domain.model.StampDraft
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryNoteScreen(
    draft: StampDraft,
    draftId: String? = null,
    onNavigateBack: () -> Unit,
    onSavedSuccess: () -> Unit,
    viewModel: MemoryNoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val repository = remember(context) { StampRepository.getInstance(context) }
    val roomCollections by repository.observeCollections().collectAsState(initial = emptyList())

    var showMoodSheet by remember { mutableStateOf(false) }
    var showCollectionSheet by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showAudienceSheet by remember { mutableStateOf(false) }

    val moods = listOf(
        "😊" to "Happy",
        "❤️" to "Love",
        "☀️" to "Bright",
        "✨" to "Magic",
        "☕" to "Cozy",
        "🌿" to "Calm",
        "🥹" to "Emotional"
    )

    LaunchedEffect(draft.renderedImagePath, draftId) {
        repository.ensureDefaultCollections()
        if (!draftId.isNullOrBlank()) viewModel.loadDraftById(context, draftId)
        else viewModel.initialize(context, draft, draftId)
    }

    BackHandler {
        viewModel.discardDraft(context, draftId) { onNavigateBack() }
    }

    Scaffold(
        containerColor = WarmPaperBg,
        topBar = {
            TopAppBar(
                title = { Text("Keep this moment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.discardDraft(context, draftId) { onNavigateBack() }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !uiState.isSaving,
                        onClick = { viewModel.saveMemory(context) { onSavedSuccess() } }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentRed)
                        } else {
                            Text("Save", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val activeDraft = uiState.draft ?: draft
            val imagePath = activeDraft.renderedImagePath.ifBlank { activeDraft.originalImagePath }
            val imageFile = remember(imagePath) {
                if (imagePath.isNotBlank() && !imagePath.startsWith("draft_")) File(imagePath) else null
            }

            Surface(
                color = Color.Transparent,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(StampGeometry.ASPECT_RATIO)
            ) {
                if (imageFile != null && imageFile.exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(imageFile),
                        contentDescription = "Stamp preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (uiState.title.isBlank()) {
                    Text(
                        "Name this memory",
                        color = TertiaryText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                BasicTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = AppSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = PrimaryText,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(AccentRed),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (uiState.note.isBlank()) {
                    Text(
                        "Write what you want to remember…",
                        color = TertiaryText,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = uiState.note,
                    onValueChange = viewModel::updateNote,
                    minLines = 2,
                    maxLines = 5,
                    textStyle = TextStyle(
                        fontFamily = AppSansFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = PrimaryText,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(AccentRed),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    val isLocationAndNetworkReady = LocationHelper.isLocationAndNetworkReady(context)
                    if (isLocationAndNetworkReady) {
                        MemoryMetaRow(
                            icon = { Icon(Icons.Outlined.LocationOn, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
                            title = "Location",
                            value = uiState.location.ifBlank { "Add" },
                            onClick = { showLocationDialog = true }
                        )
                        HorizontalDivider(color = UIBorder, modifier = Modifier.padding(start = 52.dp))
                    }
                    MemoryMetaRow(
                        icon = { Icon(Icons.Outlined.Mood, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
                        title = "Mood",
                        value = uiState.mood?.takeIf { it.isNotBlank() } ?: "Add",
                        accentValue = !uiState.mood.isNullOrBlank(),
                        onClick = { showMoodSheet = true }
                    )
                    HorizontalDivider(color = UIBorder, modifier = Modifier.padding(start = 52.dp))
                    val selectedCollection = roomCollections.find { it.id == uiState.collectionId }
                    MemoryMetaRow(
                        icon = { Icon(Icons.Outlined.CollectionsBookmark, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
                        title = "Collection",
                        value = selectedCollection?.name ?: "None",
                        onClick = { showCollectionSheet = true }
                    )
                    HorizontalDivider(color = UIBorder, modifier = Modifier.padding(start = 52.dp))
                    MemoryMetaRow(
                        icon = { Icon(Icons.Outlined.Visibility, null, tint = SecondaryText, modifier = Modifier.size(20.dp)) },
                        title = "Quyền riêng tư",
                        value = "${uiState.audienceType.icon} ${uiState.audienceType.label}",
                        accentValue = true,
                        onClick = { showAudienceSheet = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAudienceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudienceSheet = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text("Ai có thể xem tem này?", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Chọn đối tượng có thể nhìn thấy và tương tác với con tem của bạn",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(18.dp))

                AudienceType.values().forEach { audience ->
                    val selected = uiState.audienceType == audience
                    Surface(
                        onClick = {
                            viewModel.updateAudience(audience)
                            showAudienceSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) AccentRedSoft else SurfaceSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(audience.icon, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    audience.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (selected) AccentRed else PrimaryText
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    audience.description,
                                    fontSize = 12.sp,
                                    color = if (selected) AccentRed.copy(alpha = 0.85f) else SecondaryText
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    tint = AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showMoodSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoodSheet = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("How did this feel?", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 340.dp)
                ) {
                    items(moods) { (emoji, label) ->
                        val selected = uiState.mood == emoji
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) AccentRedSoft else SurfaceSoft)
                                .clickable {
                                    viewModel.updateMood(emoji)
                                    showMoodSheet = false
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) AccentRed else PrimaryText)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showCollectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCollectionSheet = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Choose a collection", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(14.dp))
                CollectionChoiceRow(
                    emoji = "—",
                    label = "No collection",
                    selected = uiState.collectionId == null
                ) {
                    viewModel.updateCollectionId(null)
                    showCollectionSheet = false
                }
                roomCollections.forEach { col ->
                    CollectionChoiceRow(
                        emoji = col.iconEmoji ?: "•",
                        label = col.name,
                        selected = uiState.collectionId == col.id
                    ) {
                        viewModel.updateCollectionId(col.id)
                        showCollectionSheet = false
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showLocationDialog) {
        LocationPickerModalSheet(
            initialLocation = uiState.location,
            onDismiss = { showLocationDialog = false },
            onLocationSelected = { locationName, _, story ->
                viewModel.updateLocation(locationName)
                if (uiState.note.isBlank() && story != null) {
                    viewModel.updateNote(story.poeticNote)
                }
                showLocationDialog = false
            }
        )
    }
}

@Composable
private fun MemoryMetaRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    accentValue: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 13.sp, color = if (accentValue) AccentRed else SecondaryText)
        Spacer(modifier = Modifier.width(5.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TertiaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CollectionChoiceRow(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) AccentRedSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(SurfaceSoft),
            contentAlignment = Alignment.Center
        ) { Text(emoji) }
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) AccentRed else PrimaryText)
    }
}
