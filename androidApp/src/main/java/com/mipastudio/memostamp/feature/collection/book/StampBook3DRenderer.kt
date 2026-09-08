package com.mipastudio.memostamp.feature.collection.book

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.AlbumPage
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditMode
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditState
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditorBottomBar
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditorTopControls
import com.mipastudio.memostamp.feature.collection.editor.AlbumPageManagementSheet
import com.mipastudio.memostamp.feature.collection.editor.AlbumVaultPicker
import com.mipastudio.memostamp.feature.collection.editor.EditableBookPage
import com.mipastudio.memostamp.ui.icon.MemoStampIcons
import com.mipastudio.memostamp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val BookDarkWoodBg = Color(0xFF2C2421)
private val BookSpineCreaseColor = Color(0xFF1E1614)
private val BookGold = Color(0xFFD1A559)
private val BookPageCream = Color(0xFFF4EBDD)
private val BookPaperBorder = Color(0xFFE8E2D9)

@Composable
fun StampBook3DRenderer(
    albumId: String,
    albumTitle: String,
    albumDescription: String,
    curatorName: String,
    coverColor: Color,
    iconKey: String?,
    stamps: List<AlbumStampData>,
    pages: List<AlbumPage> = emptyList(),
    placements: List<com.mipastudio.memostamp.domain.model.StampPlacement> = emptyList(),
    availableVaultStamps: List<StampEntity> = emptyList(),
    albumLayoutRepo: AlbumLayoutRepository? = null,
    onStampClick: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Reduced motion check
    val isReducedMotion = remember {
        try {
            val animScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            animScale == 0f || transScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Spreads calculation (pure & deterministic with canonical pages authority)
    val spreads = remember(albumId, pages, stamps, placements) {
        calculateSpreads(albumId = albumId, pages = pages, stamps = stamps, placements = placements)
    }
    val totalSpreads = spreads.size

    val stateMachine = remember(albumId) {
        PageTurnStateMachine(albumId = albumId)
    }

    // Safety: clamp spread index if page removal shrinks total spreads
    LaunchedEffect(totalSpreads) {
        if (totalSpreads > 0 && stateMachine.currentSpreadIndex >= totalSpreads) {
            stateMachine.goToSpread(totalSpreads - 1)
        }
    }

    val resolvedRepo = remember(context, albumLayoutRepo) {
        albumLayoutRepo ?: AlbumLayoutRepository.getInstance(context) { null }
    }
    val editState = remember(albumId) {
        AlbumEditState(albumId = albumId)
    }

    LaunchedEffect(stateMachine.currentSpreadIndex) {
        val spread = spreads.getOrNull(stateMachine.currentSpreadIndex)
        if (spread != null) {
            val pageIdx = if (!spread.leftPage.isInsideCover) spread.leftPage.pageIndex else spread.rightPage.pageIndex
            editState.activePageIndex = pageIdx
        }
    }

    // Animated values for 2.5D cover and page turns
    val coverOpenProgress = remember { Animatable(0f) }
    val turnAnimProgress = remember { Animatable(0f) }
    var bookSize by remember { mutableStateOf(IntSize.Zero) }

    // Safe Back handling during animation
    BackHandler {
        if (editState.mode == AlbumEditMode.EDIT) {
            editState.exitEditMode(resolvedRepo, coroutineScope)
            return@BackHandler
        }
        if (!stateMachine.interactionLocked) {
            if (stateMachine.bookState == BookState.OPEN) {
                coroutineScope.launch {
                    stateMachine.close()
                    coverOpenProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = if (isReducedMotion) tween(150) else tween(400, easing = FastOutSlowInEasing)
                    )
                    stateMachine.settleClosed()
                    onDismiss()
                }
            } else if (stateMachine.bookState == BookState.CLOSED) {
                onDismiss()
            }
        }
    }

    // Sequence 1: Book opens on entrance
    LaunchedEffect(albumId) {
        stateMachine.open()
        coverOpenProgress.snapTo(0f)
        coverOpenProgress.animateTo(
            targetValue = 1f,
            animationSpec = if (isReducedMotion) tween(150) else tween(500, easing = FastOutSlowInEasing)
        )
        stateMachine.settleOpen()
    }

    fun requestClose() {
        if (stateMachine.interactionLocked) return
        if (editState.mode == AlbumEditMode.EDIT) {
            editState.exitEditMode(resolvedRepo, coroutineScope)
        }
        coroutineScope.launch {
            stateMachine.close()
            coverOpenProgress.animateTo(
                targetValue = 0f,
                animationSpec = if (isReducedMotion) tween(150) else tween(350, easing = FastOutSlowInEasing)
            )
            stateMachine.settleClosed()
            onDismiss()
        }
    }

    fun navigateSpread(forward: Boolean) {
        if (stateMachine.bookState != BookState.OPEN || stateMachine.interactionLocked) return
        val direction = if (forward) TurnDirection.FORWARD else TurnDirection.BACKWARD
        val canTurn = stateMachine.startTurn(direction, totalSpreads)
        if (!canTurn) return

        coroutineScope.launch {
            if (isReducedMotion) {
                stateMachine.finishTurn(completed = true, totalSpreads = totalSpreads)
            } else {
                turnAnimProgress.snapTo(0f)
                turnAnimProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                )
                turnAnimProgress.snapTo(0f)
                stateMachine.finishTurn(completed = true, totalSpreads = totalSpreads)
            }
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BookDarkWoodBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumTitle,
                            color = BookGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.book_spread_page_indicator,
                                stateMachine.currentSpreadIndex + 1,
                                totalSpreads
                            ),
                            color = BookGold.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AlbumEditorTopControls(
                            isVirtualAlbum = editState.isVirtualAlbum,
                            editState = editState,
                            albumLayoutRepo = resolvedRepo
                        )

                        IconButton(
                            onClick = { requestClose() },
                            enabled = !stateMachine.interactionLocked
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.book_close),
                                tint = BookGold
                            )
                        }
                    }
                }

                // 2.5D Two-Page Book Arena
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .onSizeChanged { bookSize = it }
                        .pointerInput(albumId, stateMachine.currentSpreadIndex, totalSpreads, isReducedMotion, editState.mode) {
                            if (isReducedMotion || editState.mode == AlbumEditMode.EDIT) return@pointerInput
                            var totalDragX = 0f
                            val halfWidth = (size.width / 2f).coerceAtLeast(1f)

                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    totalDragX = 0f
                                    val isRightHalf = offset.x >= halfWidth
                                    val direction = if (isRightHalf) TurnDirection.FORWARD else TurnDirection.BACKWARD
                                    stateMachine.startTurn(direction, totalSpreads)
                                },
                                onDragEnd = {
                                    if (stateMachine.turnDirection != TurnDirection.NONE) {
                                        val progress = stateMachine.turnProgress
                                        val complete = progress >= 0.45f
                                        coroutineScope.launch {
                                            if (complete) {
                                                turnAnimProgress.snapTo(progress)
                                                turnAnimProgress.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                                turnAnimProgress.snapTo(0f)
                                                stateMachine.finishTurn(completed = true, totalSpreads = totalSpreads)
                                            } else {
                                                turnAnimProgress.snapTo(progress)
                                                turnAnimProgress.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                                turnAnimProgress.snapTo(0f)
                                                stateMachine.finishTurn(completed = false, totalSpreads = totalSpreads)
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    if (stateMachine.turnDirection != TurnDirection.NONE) {
                                        coroutineScope.launch {
                                            stateMachine.finishTurn(completed = false, totalSpreads = totalSpreads)
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (stateMachine.turnDirection == TurnDirection.FORWARD) {
                                        // Dragging leftwards (negative dragAmount) advances forward
                                        totalDragX -= dragAmount
                                        val p = (totalDragX / halfWidth).coerceIn(0f, 1f)
                                        stateMachine.updateProgress(p)
                                    } else if (stateMachine.turnDirection == TurnDirection.BACKWARD) {
                                        // Dragging rightwards (positive dragAmount) advances backward
                                        totalDragX += dragAmount
                                        val p = (totalDragX / halfWidth).coerceIn(0f, 1f)
                                        stateMachine.updateProgress(p)
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val currentSpread = spreads[stateMachine.currentSpreadIndex.coerceIn(0, totalSpreads - 1)]
                    val nextSpread = spreads.getOrNull(stateMachine.currentSpreadIndex + 1)
                    val prevSpread = spreads.getOrNull(stateMachine.currentSpreadIndex - 1)

                    val activeProgress = if (turnAnimProgress.isRunning) {
                        turnAnimProgress.value
                    } else {
                        stateMachine.turnProgress
                    }

                    // Stacked Edge Paper Layers (Book Thickness Simulation)
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.96f)
                            .shadow(24.dp, RoundedCornerShape(8.dp))
                            .background(Color(0xFFE2D6C2), RoundedCornerShape(8.dp))
                    )

                    // Two-Page Spread Container: [LEFT PAGE | SPINE | RIGHT PAGE]
                    Row(
                        modifier = Modifier
                            .fillMaxSize(0.95f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BookPageCream)
                    ) {
                        // === LEFT PAGE SURFACE ===
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stateMachine.turnDirection == TurnDirection.BACKWARD && prevSpread != null) {
                                // Background base for backward turn is previous spread's left page
                                BookPageSurface(
                                    pageData = prevSpread.leftPage,
                                    albumTitle = albumTitle,
                                    albumDesc = albumDescription,
                                    curatorName = curatorName,
                                    totalStampsCount = stamps.size,
                                    allPlacements = placements,
                                    editState = editState,
                                    albumLayoutRepo = resolvedRepo,
                                    onStampClick = onStampClick
                                )
                            } else {
                                // Normal current spread left page
                                BookPageSurface(
                                    pageData = currentSpread.leftPage,
                                    albumTitle = albumTitle,
                                    albumDesc = albumDescription,
                                    curatorName = curatorName,
                                    totalStampsCount = stamps.size,
                                    allPlacements = placements,
                                    editState = editState,
                                    albumLayoutRepo = resolvedRepo,
                                    onStampClick = onStampClick
                                )
                            }

                            // Dynamic backward turning page
                            if (stateMachine.turnDirection == TurnDirection.BACKWARD && prevSpread != null) {
                                val visuals = calculateTurnVisuals(activeProgress, isForward = false)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            rotationY = visuals.pageAngleDegrees
                                            cameraDistance = 16f * density.density
                                            transformOrigin = TransformOrigin(1f, 0.5f)
                                        }
                                        .background(BookPageCream)
                                ) {
                                    if (activeProgress < 0.5f) {
                                        // Front face: current spread's left page
                                        BookPageSurface(
                                            pageData = currentSpread.leftPage,
                                            albumTitle = albumTitle,
                                            albumDesc = albumDescription,
                                            curatorName = curatorName,
                                            totalStampsCount = stamps.size,
                                            allPlacements = placements,
                                            editState = editState,
                                            albumLayoutRepo = resolvedRepo,
                                            onStampClick = onStampClick
                                        )
                                    } else {
                                        // Back face: previous spread's right page
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationY = 180f }
                                        ) {
                                            BookPageSurface(
                                                pageData = prevSpread.rightPage,
                                                albumTitle = albumTitle,
                                                albumDesc = albumDescription,
                                                curatorName = curatorName,
                                                totalStampsCount = stamps.size,
                                                allPlacements = placements,
                                                editState = editState,
                                                albumLayoutRepo = resolvedRepo,
                                                onStampClick = onStampClick
                                            )
                                        }
                                    }

                                    // Dynamic Highlight & Shadow
                                    if (visuals.shadowAlpha > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = visuals.shadowAlpha))
                                        )
                                    }
                                }
                            }
                        }

                        // === CENTRAL BOOK SPINE / GUTTER ===
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.28f),
                                            BookSpineCreaseColor.copy(alpha = 0.40f),
                                            Color.Black.copy(alpha = 0.28f)
                                        )
                                    )
                                )
                        )

                        // === RIGHT PAGE SURFACE ===
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (stateMachine.turnDirection == TurnDirection.FORWARD && nextSpread != null) {
                                // Underneath right page reveals next spread's right page
                                BookPageSurface(
                                    pageData = nextSpread.rightPage,
                                    albumTitle = albumTitle,
                                    albumDesc = albumDescription,
                                    curatorName = curatorName,
                                    totalStampsCount = stamps.size,
                                    allPlacements = placements,
                                    editState = editState,
                                    albumLayoutRepo = resolvedRepo,
                                    onStampClick = onStampClick
                                )
                            } else {
                                // Normal current spread right page
                                BookPageSurface(
                                    pageData = currentSpread.rightPage,
                                    albumTitle = albumTitle,
                                    albumDesc = albumDescription,
                                    curatorName = curatorName,
                                    totalStampsCount = stamps.size,
                                    allPlacements = placements,
                                    editState = editState,
                                    albumLayoutRepo = resolvedRepo,
                                    onStampClick = onStampClick
                                )
                            }

                            // Dynamic forward turning page
                            if (stateMachine.turnDirection == TurnDirection.FORWARD && nextSpread != null) {
                                val visuals = calculateTurnVisuals(activeProgress, isForward = true)

                                // Cast shadow onto the underlying right page
                                if (visuals.shadowAlpha > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Black.copy(alpha = visuals.shadowAlpha),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            rotationY = visuals.pageAngleDegrees
                                            cameraDistance = 16f * density.density
                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                        }
                                        .background(BookPageCream)
                                ) {
                                    if (activeProgress < 0.5f) {
                                        // Front face: current spread's right page
                                        BookPageSurface(
                                            pageData = currentSpread.rightPage,
                                            albumTitle = albumTitle,
                                            albumDesc = albumDescription,
                                            curatorName = curatorName,
                                            totalStampsCount = stamps.size,
                                            allPlacements = placements,
                                            editState = editState,
                                            albumLayoutRepo = resolvedRepo,
                                            onStampClick = onStampClick
                                        )
                                    } else {
                                        // Back face: next spread's left page
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationY = 180f }
                                        ) {
                                            BookPageSurface(
                                                pageData = nextSpread.leftPage,
                                                albumTitle = albumTitle,
                                                albumDesc = albumDescription,
                                                curatorName = curatorName,
                                                totalStampsCount = stamps.size,
                                                allPlacements = placements,
                                                editState = editState,
                                                albumLayoutRepo = resolvedRepo,
                                                onStampClick = onStampClick
                                            )
                                        }
                                    }

                                    // Dynamic Highlight on turning page
                                    if (visuals.highlightAlpha > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = visuals.highlightAlpha),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cover Opening / Closing 3D Layer
                    val coverProgress = coverOpenProgress.value
                    if (coverProgress < 1f) {
                        val coverRotation = -coverProgress * 180f
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.95f)
                                .align(Alignment.Center)
                        ) {
                            // The right half is covered when cover is closed (rotation = 0),
                            // and flips open around the center spine to the left.
                            Row(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            rotationY = coverRotation
                                            cameraDistance = 18f * density.density
                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                        }
                                        .shadow(16.dp, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                        .background(coverColor, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                        .border(1.5.dp, BookGold.copy(alpha = 0.7f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverProgress < 0.5f) {
                                        // Outer Front Cover
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            iconKey?.let { key ->
                                                Icon(
                                                    imageVector = MemoStampIcons.resolve(key),
                                                    contentDescription = null,
                                                    tint = BookGold,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = albumTitle,
                                                color = BookGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.book_tap_to_open),
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    } else {
                                        // Inside Cover Lining
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationY = 180f }
                                                .background(BookPageCream)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive Stamp Editor Bottom Bar
                if (editState.mode == AlbumEditMode.EDIT) {
                    AlbumEditorBottomBar(
                        placements = placements,
                        totalPagesCount = totalSpreads * 2,
                        pages = pages,
                        editState = editState,
                        albumLayoutRepo = resolvedRepo
                    )
                }

                // Bottom Accessible Navigation Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navigateSpread(forward = false) },
                        enabled = stateMachine.currentSpreadIndex > 0 && !stateMachine.interactionLocked
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.book_spread_prev),
                            tint = if (stateMachine.currentSpreadIndex > 0) BookGold else BookGold.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = stringResource(R.string.book_drag_hint),
                        color = BookGold.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )

                    IconButton(
                        onClick = { navigateSpread(forward = true) },
                        enabled = stateMachine.currentSpreadIndex < totalSpreads - 1 && !stateMachine.interactionLocked
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = stringResource(R.string.book_spread_next),
                            tint = if (stateMachine.currentSpreadIndex < totalSpreads - 1) BookGold else BookGold.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        // Vault Chooser Modal Bottom Sheet
        if (editState.isVaultPickerOpen) {
            AlbumVaultPicker(
                availableStamps = availableVaultStamps,
                placements = placements,
                targetPageIndex = editState.activePageIndex,
                editState = editState,
                albumLayoutRepo = resolvedRepo,
                onDismiss = { editState.isVaultPickerOpen = false }
            )
        }

        // Page Management Modal Bottom Sheet
        if (editState.isPageManagementOpen) {
            AlbumPageManagementSheet(
                pages = pages,
                placements = placements,
                activePageIndex = editState.activePageIndex,
                onDismiss = { editState.closePageManagement() },
                onAddPage = {
                    editState.appendPage(resolvedRepo, coroutineScope)
                },
                onRemovePage = { page, placementsOnPage ->
                    editState.removePage(
                        pageId = page.id,
                        pageIndex = page.pageIndex,
                        placementsOnPage = placementsOnPage,
                        totalPageCount = pages.size,
                        repository = resolvedRepo,
                        coroutineScope = coroutineScope
                    )
                },
                onReorderPage = { pageId, direction ->
                    editState.reorderPage(
                        pageId = pageId,
                        direction = direction,
                        pages = pages,
                        repository = resolvedRepo,
                        coroutineScope = coroutineScope
                    )
                }
            )
        }

        // Save error dialog if an operation fails
        if (editState.saveErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { editState.clearSaveError() },
                text = { Text(text = editState.saveErrorMessage ?: "") },
                confirmButton = {
                    TextButton(onClick = { editState.clearSaveError() }) {
                        Text(text = stringResource(R.string.book_close))
                    }
                }
            )
        }
    }
}

/**
 * Procedural surface for a single book page.
 * Ready for future freeform-canvas coordinates.
 */
@Composable
fun BookPageSurface(
    pageData: BookPageData,
    albumTitle: String,
    albumDesc: String,
    curatorName: String,
    totalStampsCount: Int,
    allPlacements: List<com.mipastudio.memostamp.domain.model.StampPlacement> = emptyList(),
    editState: AlbumEditState? = null,
    albumLayoutRepo: AlbumLayoutRepository? = null,
    onStampClick: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, BookPaperBorder, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (pageData.isInsideCover) {
            // Inside Front Cover: Metadata & Curator
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.book_inside_cover_title),
                    color = BookGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = albumTitle,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                if (albumDesc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = albumDesc,
                        color = SecondaryText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BookGold.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.book_curator_format, curatorName),
                    color = SecondaryText,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.book_total_stamps_format, totalStampsCount),
                    color = SecondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else if (pageData.isBlankArchival) {
            // Archival Blank Page (for odd page counts)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.book_archival_blank_page),
                        color = SecondaryText.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.book_end_of_album),
                        color = SecondaryText.copy(alpha = 0.3f),
                        fontSize = 10.sp
                    )
                }
            }
        } else if (editState != null && albumLayoutRepo != null) {
            // Interactive Stamp Placement Surface (Issue #78)
            EditableBookPage(
                pageData = pageData,
                stampsList = pageData.stamps,
                placements = allPlacements.ifEmpty { pageData.placements },
                editState = editState,
                albumLayoutRepo = albumLayoutRepo,
                onStampClick = onStampClick
            )
        } else if (pageData.stamps.isEmpty()) {
            // Empty album page
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_empty_page_hint),
                        color = SecondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (pageData.placements.isNotEmpty()) {
            // Persisted Placement Layout (Custom Physical Stamp Placement)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageWidth = maxWidth
                val pageHeight = maxHeight
                val stampMap = remember(pageData.stamps) { pageData.stamps.associateBy { it.id } }

                val sortedPlacements = remember(pageData.placements) {
                    pageData.placements.sortedWith(
                        compareBy<com.mipastudio.memostamp.domain.model.StampPlacement> { it.zIndex }
                            .thenBy { it.id }
                    )
                }

                for (placement in sortedPlacements) {
                    val stamp = stampMap[placement.stampId]
                    if (stamp != null) {
                        val baseWidth = 84.dp * placement.scale.toFloat()
                        val baseHeight = 104.dp * placement.scale.toFloat()
                        val posX = (pageWidth * placement.x.toFloat()) - (baseWidth / 2f)
                        val posY = (pageHeight * placement.y.toFloat()) - (baseHeight / 2f)

                        Box(
                            modifier = Modifier
                                .offset(x = posX, y = posY)
                                .size(width = baseWidth, height = baseHeight)
                                .rotate(placement.rotationDegrees.toFloat())
                                .zIndex(placement.zIndex.toFloat())
                        ) {
                            BookStampCell(
                                stamp = stamp,
                                onClick = { onStampClick(stamp.id) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Page Number (display pageData.pageIndex + 1 as user-facing 1-based page number)
                Text(
                    text = stringResource(R.string.book_page_number_format, pageData.pageIndex + 1),
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Normal Page with Stamps (2x2 grid fallback)
            Column(modifier = Modifier.fillMaxSize()) {
                // 2x2 stamp layout
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val stampRows = pageData.stamps.chunked(2)
                    for (row in stampRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (stamp in row) {
                                BookStampCell(
                                    stamp = stamp,
                                    onClick = { onStampClick(stamp.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Page Number
                Text(
                    text = stringResource(R.string.book_page_number_format, pageData.pageIndex),
                    color = SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BookStampCell(
    stamp: AlbumStampData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, BookPaperBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = stamp.imageUrl),
                contentDescription = stamp.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stamp.name,
            color = PrimaryText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
