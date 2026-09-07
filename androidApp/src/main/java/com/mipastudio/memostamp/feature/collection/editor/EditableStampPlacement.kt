package com.mipastudio.memostamp.feature.collection.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.feature.collection.book.AlbumStampData
import com.mipastudio.memostamp.ui.theme.PrimaryText

private val BookPaperBorder = Color(0xFFE8E2D9)
private val SelectionGold = Color(0xFFD1A559)
private val SelectionOutlineColor = Color(0xFFC89B3C)

@Composable
fun EditableStampPlacement(
    placement: StampPlacement,
    stamp: AlbumStampData,
    pageWidthDp: Dp,
    pageHeightDp: Dp,
    editState: AlbumEditState,
    albumLayoutRepo: AlbumLayoutRepository,
    onStampClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isSelected = editState.selectedPlacementId == placement.id
    val isEditMode = editState.mode == AlbumEditMode.EDIT

    val transform = editState.getResolvedTransform(placement)

    val baseWidth = 84.dp * transform.scale.toFloat()
    val baseHeight = 104.dp * transform.scale.toFloat()

    val posX = (pageWidthDp * transform.x.toFloat()) - (baseWidth / 2f)
    val posY = (pageHeightDp * transform.y.toFloat()) - (baseHeight / 2f)

    // Current gesture snapshot to track deltas cleanly without baseline jumps
    var gestureCurrentX by remember(transform.x) { mutableDoubleStateOf(transform.x) }
    var gestureCurrentY by remember(transform.y) { mutableDoubleStateOf(transform.y) }
    var gestureCurrentScale by remember(transform.scale) { mutableDoubleStateOf(transform.scale) }
    var gestureCurrentRotation by remember(transform.rotationDegrees) { mutableDoubleStateOf(transform.rotationDegrees) }

    Box(
        modifier = modifier
            .offset(x = posX, y = posY)
            .size(width = baseWidth.coerceAtLeast(44.dp), height = baseHeight.coerceAtLeast(44.dp))
            .rotate(transform.rotationDegrees.toFloat())
            .zIndex(transform.zIndex.toFloat() + (if (isSelected) 100f else 0f))
            .then(
                if (isEditMode) {
                    Modifier
                        .pointerInput(placement.id, isSelected) {
                            detectTapGestures(
                                onTap = {
                                    editState.selectPlacement(placement.id)
                                }
                            )
                        }
                        .pointerInput(placement.id) {
                            detectTransformGestures(
                                onGesture = { _, pan, zoom, rotation ->
                                    if (!isSelected) {
                                        editState.selectPlacement(placement.id)
                                    }
                                    val widthPx = (pageWidthDp.toPx()).coerceAtLeast(1f)
                                    val heightPx = (pageHeightDp.toPx()).coerceAtLeast(1f)

                                    gestureCurrentX = AlbumEditState.clampX(gestureCurrentX + (pan.x / widthPx))
                                    gestureCurrentY = AlbumEditState.clampY(gestureCurrentY + (pan.y / heightPx))
                                    gestureCurrentScale = AlbumEditState.clampScale(gestureCurrentScale * zoom)
                                    gestureCurrentRotation = AlbumEditState.clampRotation(gestureCurrentRotation + rotation)

                                    // Local optimistic UI transform (NO DB/network writes per frame)
                                    editState.updateTransientTransform(
                                        placementId = placement.id,
                                        x = gestureCurrentX,
                                        y = gestureCurrentY,
                                        scale = gestureCurrentScale,
                                        rotationDegrees = gestureCurrentRotation,
                                        zIndex = transform.zIndex
                                    )
                                }
                            )
                        }
                        // On touch up, detectTransformGestures finishes and we commit to repository
                        .pointerInput(placement.id, isSelected) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val allUp = event.changes.all { !it.pressed }
                                    if (allUp && editState.transientTransforms.containsKey(placement.id)) {
                                        editState.commitTransform(
                                            placementId = placement.id,
                                            repository = albumLayoutRepo,
                                            originalPlacement = placement,
                                            coroutineScope = coroutineScope
                                        )
                                    }
                                }
                            }
                        }
                } else {
                    Modifier.clickable { onStampClick(stamp.id) }
                }
            )
    ) {
        // Selection Frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected && isEditMode) {
                        Modifier
                            .padding(2.dp)
                            .border(2.dp, SelectionOutlineColor, RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(if (isSelected && isEditMode) 4.dp else 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .shadow(if (isSelected) 8.dp else 4.dp, RoundedCornerShape(4.dp))
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
                    fontSize = (9 * transform.scale).coerceIn(8.0, 13.0).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Subtle Corner Indicator Dots when selected
        if (isSelected && isEditMode) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopStart)
                    .background(SelectionGold, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .background(SelectionGold, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.BottomStart)
                    .background(SelectionGold, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.BottomEnd)
                    .background(SelectionGold, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
        }
    }
}
