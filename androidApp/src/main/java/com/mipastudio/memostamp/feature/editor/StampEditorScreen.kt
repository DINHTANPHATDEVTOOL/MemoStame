package com.mipastudio.memostamp.feature.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mipastudio.memostamp.domain.model.Stamp
import com.mipastudio.memostamp.domain.model.StampTemplates
import com.mipastudio.memostamp.domain.model.StampType
import com.mipastudio.memostamp.core.processor.CameraPreset
import com.mipastudio.memostamp.core.processor.getComposeColorMatrix
import com.mipastudio.memostamp.ui.theme.*
import com.mipastudio.memostamp.ui.components.PerforatedStampShape
import com.mipastudio.memostamp.ui.components.StampGeometry
import com.mipastudio.memostamp.feature.camera.SafeBitmapDecoder

import com.mipastudio.memostamp.core.location.LocationHelper
import com.mipastudio.memostamp.core.location.LocationPickerModalSheet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampEditorScreen(
    initialPhotoUrl: String? = null,
    stampId: String? = null,
    onNavigateBack: () -> Unit,
    onStampSaved: (Stamp) -> Unit,
    viewModel: StampEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val authRepo = remember(context) { com.mipastudio.memostamp.data.repository.UserAuthRepository.getInstance(context) }
    val currentUser by authRepo.currentUser.collectAsState()

    var titleText by remember { mutableStateOf("Memory Moment") }
    var locationText by remember { mutableStateOf("Da Lat, Vietnam") }
    var dateText by remember { mutableStateOf("13.08.26") }
    var captionText by remember { mutableStateOf("Một khoảnh khắc đáng nhớ.") }

    LaunchedEffect(stampId, initialPhotoUrl) {
        viewModel.loadStampData(context, stampId, initialPhotoUrl)
        LocationHelper.fetchCurrentLocation(context) { currentLoc ->
            if (locationText == "Da Lat, Vietnam") {
                locationText = currentLoc
            }
        }
    }
    var activeToolSheet by remember { mutableStateOf<Int?>(null) } // 1: Template, 2: Text, 3: Sticker, 4: Filter, 6: More
    var showLocationPickerSheet by remember { mutableStateOf(false) }
    var customTextVal by remember { mutableStateOf("") }
    var editingElementId by remember { mutableStateOf<String?>(null) }
    var editDialogText by remember { mutableStateOf("") }

    val studioPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = SafeBitmapDecoder.saveOriginalToAppFiles(context, uri.toString())
            if (savedPath != null) {
                viewModel.updateSourceImagePath(savedPath)
            }
        }
    }

    val colorPalette = listOf("#D94E41", "#55738F", "#D4A340", "#171717", "#77736E", "#FFFFFF")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.undoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.undoStack.isNotEmpty()) PrimaryText else SecondaryText.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.redoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.redoStack.isNotEmpty()) PrimaryText else SecondaryText.copy(alpha = 0.4f)
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveStamp(
                                context = context,
                                title = titleText,
                                location = locationText,
                                date = dateText,
                                caption = captionText,
                                onSuccess = { entity ->
                                    val newStamp = Stamp(
                                        id = entity.id,
                                        stampNumber = "#STAMP-${entity.id.take(8).uppercase()}",
                                        title = entity.title,
                                        imageUrl = entity.stampImagePath,
                                        creatorId = currentUser.userId,
                                        creatorName = currentUser.displayName.ifBlank { "User" },
                                        ownerId = currentUser.userId,
                                        ownerName = currentUser.displayName.ifBlank { "User" },
                                        createdDate = "Today",
                                        memoryDate = dateText,
                                        location = entity.location ?: "",
                                        caption = entity.note,
                                        type = StampType.PERSONAL
                                    )
                                    Toast.makeText(context, "Stamp saved!", Toast.LENGTH_SHORT).show()
                                    onStampSaved(newStamp)
                                },
                                onError = { msg ->
                                    Toast.makeText(context, "Save error: $msg", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentRed)
                        } else {
                            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        }
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
        ) {
            // Interactive Stamp Canvas Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { viewModel.selectElement(null) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val stampShape = if (uiState.borderStyle == "perforated") {
                    PerforatedStampShape()
                } else {
                    RoundedCornerShape(8.dp)
                }

                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .aspectRatio(StampGeometry.ASPECT_RATIO)
                        .shadow(8.dp, stampShape)
                        .clip(stampShape)
                ) {
                    AsyncImage(
                        model = uiState.sourceImagePath.ifBlank { "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(uiState.filterSpec.toComposeColorMatrix()),
                        modifier = Modifier.fillMaxSize()
                    )

                    // WYSIWYG Elements Layer (Smooth Drag & Drop + Transform)
                    uiState.elements.sortedBy { it.zIndex }.forEach { el ->
                        val currentEl by rememberUpdatedState(el)
                        val isSelected = currentEl.id == uiState.selectedElementId
                        val color = try {
                            Color(android.graphics.Color.parseColor(currentEl.colorHex))
                        } catch (e: Exception) {
                            Color.White
                        }
                        val alpha = currentEl.opacity.coerceIn(0f, 1f)

                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val boxWidthPx = with(density) { 280.dp.toPx() }
                        val boxHeightPx = with(density) { 350.dp.toPx() }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (280 * currentEl.x).dp, y = (350 * currentEl.y).dp)
                                .rotate(currentEl.rotation)
                                .pointerInput(currentEl.id) {
                                    detectTransformGestures(
                                        onGesture = { _, pan, zoom, rotationChange ->
                                            viewModel.selectElement(currentEl.id)
                                            if (pan.x != 0f || pan.y != 0f) {
                                                val deltaX = pan.x / boxWidthPx
                                                val deltaY = pan.y / boxHeightPx
                                                val newX = (currentEl.x + deltaX).coerceIn(0.05f, 0.95f)
                                                val newY = (currentEl.y + deltaY).coerceIn(0.05f, 0.95f)
                                                viewModel.updateElementPosition(currentEl.id, newX, newY)
                                            }
                                            if (zoom != 1f || rotationChange != 0f) {
                                                val newScale = (currentEl.scale * zoom).coerceIn(0.4f, 3.5f)
                                                val newRotation = (currentEl.rotation + rotationChange) % 360f
                                                viewModel.updateElementTransform(currentEl.id, newScale, newRotation)
                                            }
                                        }
                                    )
                                }
                                .clickable { viewModel.selectElement(currentEl.id) }
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .border(1.5.dp, AccentRed, RoundedCornerShape(6.dp))
                                            .background(AccentRed.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                    } else Modifier
                                )
                                .padding(6.dp)
                        ) {
                            when (el.type) {
                                "sticker" -> {
                                    Text(
                                        text = el.value,
                                        fontSize = (24 * el.scale).sp,
                                        color = color.copy(alpha = alpha)
                                    )
                                }
                                "badge" -> {
                                    Box(
                                        modifier = Modifier
                                            .background(color.copy(alpha = alpha), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = el.value,
                                            fontSize = (10 * el.scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = el.value,
                                        fontSize = (12 * el.scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color.copy(alpha = alpha)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Selected Element Quick Toolbar
            val selectedEl = uiState.elements.find { it.id == uiState.selectedElementId }
            if (selectedEl != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceWhite,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(colorPalette) { hex ->
                                val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.White }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(1.dp, UIBorder, CircleShape)
                                        .clickable { viewModel.updateElementColor(selectedEl.id, hex) }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedEl.type == "text" || selectedEl.type == "badge") {
                                IconButton(onClick = {
                                    editingElementId = selectedEl.id
                                    editDialogText = selectedEl.value
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit Text", tint = PrimaryText)
                                }
                            }
                            IconButton(onClick = { viewModel.duplicateElement(selectedEl.id) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", tint = PrimaryText)
                            }
                            IconButton(onClick = { viewModel.deleteElement(selectedEl.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = AccentRed)
                            }
                        }
                    }
                }
            }

            // Story Editor Bottom Tool Strip (Colorful custom tool buttons)
            Surface(
                color = SurfaceWhite,
                border = BorderStroke(0.5.dp, UIBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val isLocationAndNetworkReady = LocationHelper.isLocationAndNetworkReady(context)
                    val tools = remember(isLocationAndNetworkReady) {
                        buildList {
                            add(Triple(1, "Template", Icons.Outlined.Style to Color(0xFFFF5722)))
                            add(Triple(2, "Text", Icons.Outlined.TextFields to AccentBlue))
                            add(Triple(3, "Sticker", Icons.Outlined.SentimentSatisfied to Color(0xFF00BFA5)))
                            add(Triple(4, "Filter", Icons.Outlined.ColorLens to Color(0xFF8E24AA)))
                            if (isLocationAndNetworkReady) {
                                add(Triple(5, "Maps AI", Icons.Outlined.Place to Color(0xFF1E88E5)))
                            }
                            add(Triple(6, "More", Icons.Outlined.MoreHoriz to Color(0xFFFFB300)))
                        }
                    }

                    tools.forEach { (sheetId, label, iconPair) ->
                        val (icon, tintColor) = iconPair
                        val isActive = if (sheetId == 5) showLocationPickerSheet else activeToolSheet == sheetId

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isActive) tintColor.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isActive) 1.dp else 0.dp,
                                    color = if (isActive) tintColor.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    if (sheetId == 5) {
                                        showLocationPickerSheet = true
                                    } else {
                                        activeToolSheet = sheetId
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isActive) tintColor else PrimaryText.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isActive) tintColor else SecondaryText,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Tool BottomSheets
    activeToolSheet?.let { sheetId ->
        ModalBottomSheet(
            onDismissRequest = { activeToolSheet = null },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                when (sheetId) {
                    1 -> { // Template
                        Text("Select Stamp Template", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(StampTemplates.ALL) { template ->
                                val isSelected = uiState.templateId == template.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectTemplate(template.id)
                                        activeToolSheet = null
                                    },
                                    label = { Text("${template.iconEmoji} ${template.name}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    2 -> { // Text
                        Text("Add Custom Text", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customTextVal,
                            onValueChange = { customTextVal = it },
                            placeholder = { Text("e.g. DALAT MEMORY 2026") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val txt = customTextVal.ifBlank { "DALAT MEMORY" }
                                    viewModel.addElement("text", txt, "#D94E41")
                                    customTextVal = ""
                                    activeToolSheet = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                            ) {
                                Text("+ Text Element")
                            }
                            Button(
                                onClick = {
                                    val txt = customTextVal.ifBlank { "SPECIAL EDITION" }
                                    viewModel.addElement("badge", txt, "#55738F")
                                    customTextVal = ""
                                    activeToolSheet = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                            ) {
                                Text("+ Badge")
                            }
                        }
                    }
                    3 -> { // Sticker
                        Text("Stickers", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        val stickers = listOf("✿", "♡", "✈️", "☕", "✦", "🏷️", "🧧", "🎓", "🏖️", "⭐", "📮", "✉️", "💌", "🌿", "☀️", "🌊", "📸", "🗺️", "🥐", "🍷", "🌸", "🎏", "🎯", "🎉")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(stickers) { sticker ->
                                Surface(
                                    shape = CircleShape,
                                    color = WarmPaperBg,
                                    modifier = Modifier.clickable {
                                        viewModel.addElement("sticker", sticker, "#D4A340")
                                        activeToolSheet = null
                                    }
                                ) {
                                    Text(sticker, fontSize = 24.sp, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                    4 -> { // Filter
                        Text("Color Filters", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(com.mipastudio.memostamp.core.processor.FilterPresets.ALL) { spec ->
                                val isSelected = uiState.filterSpec.id == spec.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.selectFilter(spec)
                                        activeToolSheet = null
                                    },
                                    label = { Text(spec.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentRed,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    6 -> { // More
                        Text("Frame & Photo Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                studioPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                activeToolSheet = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change Photo 📷")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLocationPickerSheet) {
        LocationPickerModalSheet(
            initialLocation = locationText,
            onDismiss = { showLocationPickerSheet = false },
            onLocationSelected = { locationName, suggestedStampTitle, story ->
                locationText = locationName
                if (!suggestedStampTitle.isNullOrBlank()) {
                    titleText = suggestedStampTitle
                }
                // Add a vintage location badge to canvas
                viewModel.addElement("badge", "📍 " + locationName.take(18), "#D94E41")
                if (story != null && captionText == "Một khoảnh khắc đáng nhớ.") {
                    captionText = story.poeticNote
                }
                showLocationPickerSheet = false
            }
        )
    }

    // Element Text Editing Dialog
    editingElementId?.let { elId ->
        AlertDialog(
            onDismissRequest = { editingElementId = null },
            title = { Text("Edit Element Text", fontWeight = FontWeight.Bold, color = PrimaryText) },
            text = {
                OutlinedTextField(
                    value = editDialogText,
                    onValueChange = { editDialogText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editDialogText.isNotBlank()) {
                            viewModel.updateElementValue(elId, editDialogText)
                        }
                        editingElementId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingElementId = null }) {
                    Text("Cancel")
                }
            },
            containerColor = SurfaceWhite
        )
    }
}
