package com.mipastudio.memostamp.feature.collection.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.StampPlacement

private val BookGold = Color(0xFFD1A559)
private val EditorBarBg = Color(0xEE1E1614)

@Composable
fun AlbumEditorTopControls(
    isVirtualAlbum: Boolean,
    editState: AlbumEditState,
    albumLayoutRepo: AlbumLayoutRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isVirtualAlbum) {
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.album_editor_virtual_readonly),
                        tint = BookGold.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.album_editor_virtual_readonly),
                        color = BookGold.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            if (editState.mode == AlbumEditMode.VIEW) {
                FilledTonalButton(
                    onClick = { editState.enterEditMode() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BookGold.copy(alpha = 0.2f),
                        contentColor = BookGold
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.album_editor_edit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = { editState.exitEditMode(albumLayoutRepo, coroutineScope) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BookGold,
                        contentColor = Color(0xFF2C2421)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.album_editor_done),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumEditorBottomBar(
    placements: List<StampPlacement>,
    totalPagesCount: Int,
    editState: AlbumEditState,
    albumLayoutRepo: AlbumLayoutRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedPlacement = remember(placements, editState.selectedPlacementId) {
        placements.find { it.id == editState.selectedPlacementId }
    }

    Surface(
        color = EditorBarBg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Group: Add Stamp & Undo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { editState.isVaultPickerOpen = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BookGold,
                            contentColor = Color(0xFF2C2421)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.album_editor_add_stamp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { editState.undo(albumLayoutRepo, coroutineScope) },
                        enabled = editState.undoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = stringResource(R.string.album_editor_undo),
                            tint = if (editState.undoStack.isNotEmpty()) BookGold else BookGold.copy(alpha = 0.3f)
                        )
                    }
                }

                // Right Group: Selected Stamp Actions (Bring Forward, Send Backward, Move Page, Delete)
                if (selectedPlacement != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bring Forward
                        IconButton(
                            onClick = {
                                editState.bringForward(
                                    placementId = selectedPlacement.id,
                                    currentPlacements = placements,
                                    repository = albumLayoutRepo,
                                    coroutineScope = coroutineScope
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.album_editor_bring_forward),
                                tint = BookGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Send Backward
                        IconButton(
                            onClick = {
                                editState.sendBackward(
                                    placementId = selectedPlacement.id,
                                    currentPlacements = placements,
                                    repository = albumLayoutRepo,
                                    coroutineScope = coroutineScope
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = stringResource(R.string.album_editor_send_backward),
                                tint = BookGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Move Page
                        IconButton(
                            onClick = { editState.isMovePageMenuOpen = true }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = stringResource(R.string.album_editor_move_page),
                                tint = BookGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Delete Placement
                        IconButton(
                            onClick = {
                                editState.deletePlacement(
                                    placementId = selectedPlacement.id,
                                    currentPlacement = selectedPlacement,
                                    repository = albumLayoutRepo,
                                    coroutineScope = coroutineScope
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.album_editor_remove_placement),
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Move to Page Dialog
    if (editState.isMovePageMenuOpen && selectedPlacement != null) {
        val candidatePages = (0 until maxOf(totalPagesCount, selectedPlacement.pageIndex + 2)).toList()

        AlertDialog(
            onDismissRequest = { editState.isMovePageMenuOpen = false },
            title = {
                Text(
                    text = stringResource(R.string.album_editor_move_page),
                    color = Color(0xFF2C2421),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in candidatePages) {
                        val isCurrent = p == selectedPlacement.pageIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) BookGold.copy(alpha = 0.25f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrent) {
                                    editState.movePlacementToPage(
                                        placementId = selectedPlacement.id,
                                        targetPageIndex = p,
                                        currentPlacement = selectedPlacement,
                                        repository = albumLayoutRepo,
                                        coroutineScope = coroutineScope
                                    )
                                    editState.isMovePageMenuOpen = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.book_page_number_format, p + 1) + if (isCurrent) " (Current)" else "",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF2C2421)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editState.isMovePageMenuOpen = false }) {
                    Text(text = stringResource(R.string.book_close), color = Color(0xFF6D4C41))
                }
            },
            containerColor = Color(0xFFF4EBDD)
        )
    }
}
