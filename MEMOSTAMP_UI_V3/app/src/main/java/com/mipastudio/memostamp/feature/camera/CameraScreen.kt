package com.mipastudio.memostamp.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.mipastudio.memostamp.core.processor.FilterPresets
import com.mipastudio.memostamp.core.theme.PostalRed
import com.mipastudio.memostamp.core.theme.StampDarkInk
import com.mipastudio.memostamp.core.theme.StampPaperCard
import com.mipastudio.memostamp.data.repository.StampRepository
import com.mipastudio.memostamp.feature.camera.components.CameraControls
import com.mipastudio.memostamp.feature.camera.components.StampPressOverlay
import com.mipastudio.memostamp.feature.camera.renderer.StampCaptureRect
import kotlinx.coroutines.launch
import com.mipastudio.memostamp.feature.camera.renderer.StampLayoutCalculator
import java.io.File

@Composable
fun CameraScreen(
    onNavigateToVault: () -> Unit,
    onNavigateToNote: (String) -> Unit,
    onNavigateToHome: () -> Unit = {},
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val repository = remember(context) { StampRepository.getInstance(context) }

    val captureState by viewModel.captureState.collectAsState()
    val draft by viewModel.capturedDraft.collectAsState()
    val draftIdState by viewModel.capturedDraftId.collectAsState()
    val frozenBitmap by viewModel.frozenBitmap.collectAsState()
    val flashEvent by viewModel.flashEvent.collectAsState()

    val cameraController = remember { CameraController(context) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var flashMode by remember { mutableIntStateOf(cameraController.flashMode) }
    var currentCaptureRect by remember { mutableStateOf(StampCaptureRect()) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            cameraController.release()
        }
    }

    var recoverDraftPair by remember { mutableStateOf<Pair<String, com.mipastudio.memostamp.domain.model.StampDraft>?>(null) }
    val repo = remember(context) { com.mipastudio.memostamp.data.repository.StampRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val newest = repo.getNewestDraft()
        if (newest != null) {
            recoverDraftPair = newest
        }
    }

    // When capture completes and reaches ADDING_NOTE, navigate with Room draftId
    LaunchedEffect(captureState, draftIdState) {
        if (captureState == CaptureState.ADDING_NOTE && draftIdState != null) {
            val dId = draftIdState!!
            viewModel.resetState()
            onNavigateToNote(dId)
        }
    }

    val activeDraftPair = recoverDraftPair
    if (activeDraftPair != null) {
        val (draftId, draft) = activeDraftPair
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("✦ Unfinished Memory ✦", fontWeight = FontWeight.Bold, color = StampDarkInk)
            },
            text = {
                Text("You have an uncommitted stamp memory draft. Would you like to continue editing or discard it?", fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dId = draftId
                        recoverDraftPair = null
                        onNavigateToNote(dId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PostalRed)
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val dId = draftId
                        recoverDraftPair = null
                        coroutineScope.launch {
                            repo.removeDraft(dId)
                        }
                    }
                ) {
                    Text("Discard", color = Color.Gray)
                }
            },
            containerColor = StampPaperCard
        )
    }

    val activeFilterSpec by viewModel.activeFilterSpec.collectAsState()
    var isComparingOriginal by remember { mutableStateOf(false) }
    var showAdvancedTuneSheet by remember { mutableStateOf(false) }

    // Smart Camera Gestures State (Swipe to Filter & Pinch Zoom)
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var swipeToastFilterName by remember { mutableStateOf<String?>(null) }
    var totalDragX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(swipeToastFilterName) {
        if (swipeToastFilterName != null) {
            kotlinx.coroutines.delay(1200)
            swipeToastFilterName = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val openingRect = remember(screenWidthPx, screenHeightPx) {
            StampLayoutCalculator.calculateOpeningRect(screenWidthPx, screenHeightPx)
        }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.processGalleryUri(context, uri, haptic, openingRect, screenWidthPx, screenHeightPx)
            }
        }

        // Layer 1: Camera Preview & Smart Gesture Detector (GPU Live Filter, Swipe & Pinch Zoom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraController.minZoomRatio, cameraController.maxZoomRatio, cameraController.hasUltraWideCamera) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            val minZ = if (cameraController.hasUltraWideCamera) 0.5f else cameraController.minZoomRatio
                            val newZoom = (zoomRatio * zoom).coerceIn(minZ, cameraController.maxZoomRatio)
                            zoomRatio = newZoom
                            previewViewRef?.let { pView ->
                                cameraController.setZoomPreset(newZoom, lifecycleOwner, pView)
                            } ?: cameraController.setZoomRatio(newZoom)
                        }
                    }
                }
                .pointerInput(activeFilterSpec) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            val threshold = 90f
                            if (totalDragX < -threshold) {
                                // Swipe Left -> Next Filter
                                val allFilters = FilterPresets.ALL
                                val currentIndex = allFilters.indexOfFirst { it.id == activeFilterSpec.id }
                                val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % allFilters.size else 0
                                val nextFilter = allFilters[nextIndex]
                                viewModel.selectFilterSpec(nextFilter)
                                swipeToastFilterName = nextFilter.name
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (totalDragX > threshold) {
                                // Swipe Right -> Previous Filter
                                val allFilters = FilterPresets.ALL
                                val currentIndex = allFilters.indexOfFirst { it.id == activeFilterSpec.id }
                                val prevIndex = if (currentIndex > 0) currentIndex - 1 else allFilters.size - 1
                                val prevFilter = allFilters[prevIndex]
                                viewModel.selectFilterSpec(prevFilter)
                                swipeToastFilterName = prevFilter.name
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
                .drawWithContent {
                    val useFilter = !isComparingOriginal && activeFilterSpec.id != "original"
                    if (useFilter) {
                        val filter = ColorFilter.colorMatrix(activeFilterSpec.toComposeColorMatrix())
                        val paint = Paint().apply {
                            colorFilter = filter
                        }
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                            drawContent()
                            canvas.restore()
                        }
                    } else {
                        drawContent()
                    }
                }
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { pView ->
                            previewViewRef = pView
                            cameraController.bindCamera(lifecycleOwner, pView) {
                                flashMode = cameraController.flashMode
                            }
                        }
                    },
                    update = { pView ->
                        previewViewRef = pView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Freeze bitmap overlay to prevent preview jump during stamp press
                val freeze = frozenBitmap
                if (freeze != null && !freeze.isRecycled && (captureState == CaptureState.PRESSING || captureState == CaptureState.REVEALING)) {
                    Image(
                        bitmap = freeze.asImageBitmap(),
                        contentDescription = "Frozen frame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Swipe Filter Name Animated Toast Overlay
                AnimatedVisibility(
                    visible = swipeToastFilterName != null,
                    enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                    exit = fadeOut(tween(250)) + scaleOut(tween(250)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = swipeToastFilterName ?: "",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Permission Request Fallback View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Cho phép truy cập máy ảnh",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "MemoStamp cần camera để bạn chụp và dập khoảnh khắc thành tem.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946))
                    ) {
                        Text("Cho phép", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Layer 2 & 3: Stamp Press Overlay & Canvas Mold Animation
        val pressOffsetPx by animateFloatAsState(
            targetValue = if (captureState == CaptureState.PRESSING) 40f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "pressOffset"
        )

        StampPressOverlay(
            modifier = Modifier.fillMaxSize(),
            pressOffsetPx = pressOffsetPx,
            onCalculatedRect = { rect ->
                currentCaptureRect = rect
            },
            isImpactState = captureState == CaptureState.PRESSING
        )

        // Screen Flash Overlay (300ms impact)
        AnimatedVisibility(
            visible = flashEvent,
            enter = fadeIn(tween(50)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Layer 3.5: Stamp Reveal Die-Cut Punch Animation
        if (captureState == CaptureState.REVEALING && draft != null) {
            var animTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                animTrigger = true
            }

            val stampOffsetY by animateFloatAsState(
                targetValue = if (animTrigger) -85f else 0f,
                animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "stampOffsetY"
            )
            val stampScale by animateFloatAsState(
                targetValue = if (animTrigger) 1.12f else 0.95f,
                animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "stampScale"
            )
            val stampRotation by animateFloatAsState(
                targetValue = if (animTrigger) -4f else 0f,
                animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "stampRotation"
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = stampOffsetY.dp)
                            .scale(stampScale)
                            .rotate(stampRotation)
                            .fillMaxWidth(0.58f)
                            .aspectRatio(0.8f),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageFile = File(draft!!.renderedImagePath)
                        Image(
                            painter = rememberAsyncImagePainter(model = imageFile),
                            contentDescription = "Punched Memory Stamp",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    AnimatedVisibility(
                        visible = animTrigger,
                        enter = fadeIn(tween(400, delayMillis = 150))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "✦ STAMPED MEMORY ✦",
                                color = Color(0xFFFFD700),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }

        // Layer 4: Camera Overlay & Controls
        CameraControls(
            captureState = captureState,
            flashMode = flashMode,
            zoomRatio = zoomRatio,
            minZoomRatio = cameraController.minZoomRatio,
            maxZoomRatio = cameraController.maxZoomRatio,
            hasUltraWideCamera = cameraController.hasUltraWideCamera,
            onZoomSelected = { z ->
                zoomRatio = z
                previewViewRef?.let { pView ->
                    cameraController.setZoomPreset(z, lifecycleOwner, pView)
                } ?: cameraController.setZoomRatio(z)
            },
            onCaptureClick = {
                if (hasCameraPermission) {
                    val previewSnapshot = previewViewRef?.bitmap
                    viewModel.onCaptureClick(context, cameraController, haptic, openingRect, screenWidthPx, screenHeightPx, previewSnapshot)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onFlashClick = {
                flashMode = cameraController.toggleFlash()
            },
            onLensToggleClick = {
                previewViewRef?.let { pView ->
                    cameraController.toggleCameraLens(lifecycleOwner, pView) {
                        flashMode = cameraController.flashMode
                    }
                }
            },
            onGalleryClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onVaultClick = onNavigateToVault,
            filterBarContent = {
                if (captureState == CaptureState.READY) {
                    com.mipastudio.memostamp.feature.camera.components.CameraFilterBar(
                        activeFilter = activeFilterSpec,
                        onSelectFilter = { viewModel.selectFilterSpec(it) }
                    )
                }
            }
        )

        if (showAdvancedTuneSheet) {
            com.mipastudio.memostamp.feature.camera.components.AdvancedTuneBottomSheet(
                activeFilter = activeFilterSpec,
                onDismissRequest = { showAdvancedTuneSheet = false },
                onTuneChange = { exp, con, sat, wrm, fde, grn, vig ->
                    viewModel.updateCustomFilterTune(exp, con, sat, wrm, fde, grn, vig)
                },
                onReset = {
                    viewModel.selectFilterSpec(com.mipastudio.memostamp.core.processor.FilterPresets.ORIGINAL)
                }
            )
        }
    }
}
