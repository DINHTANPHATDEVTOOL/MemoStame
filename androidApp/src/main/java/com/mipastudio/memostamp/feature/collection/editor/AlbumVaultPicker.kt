package com.mipastudio.memostamp.feature.collection.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.local.StampEntity
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.ui.theme.PrimaryText
import com.mipastudio.memostamp.ui.theme.SecondaryText

private val SheetBgColor = Color(0xFFF9F6F0)
private val PlacedBadgeBg = Color(0x33000000)
private val GoldAccent = Color(0xFFD1A559)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumVaultPicker(
    availableStamps: List<StampEntity>,
    placements: List<StampPlacement>,
    targetPageIndex: Int,
    editState: AlbumEditState,
    albumLayoutRepo: AlbumLayoutRepository,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val placedStampIds = remember(placements) { placements.map { it.stampId }.toSet() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBgColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.album_editor_vault_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Text(
                        text = stringResource(R.string.album_editor_vault_subtitle),
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.book_close),
                        tint = PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (availableStamps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.album_editor_vault_empty),
                        fontSize = 14.sp,
                        color = SecondaryText
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(bottom = 24.dp)
                ) {
                    items(availableStamps, key = { it.id }) { stamp ->
                        val isAlreadyPlaced = placedStampIds.contains(stamp.id)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (!isAlreadyPlaced) {
                                        Modifier.clickable {
                                            editState.addPlacementFromVault(
                                                stampId = stamp.id,
                                                targetPageIndex = targetPageIndex,
                                                existingPlacements = placements,
                                                repository = albumLayoutRepo,
                                                coroutineScope = coroutineScope
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(
                                        width = 1.dp,
                                        color = if (isAlreadyPlaced) Color.LightGray else GoldAccent.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = stamp.stampImagePath),
                                    contentDescription = stamp.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(3.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                if (isAlreadyPlaced) {
                                    // Dimmed overlay + Placed badge
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PlacedBadgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.album_editor_vault_placed),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = stamp.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isAlreadyPlaced) SecondaryText else PrimaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
