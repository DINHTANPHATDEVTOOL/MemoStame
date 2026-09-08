package com.mipastudio.memostamp.feature.collection.editor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipastudio.memostamp.R
import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.feature.collection.book.AlbumStampData
import com.mipastudio.memostamp.feature.collection.book.BookPageData
import com.mipastudio.memostamp.ui.theme.SecondaryText

@Composable
fun EditableBookPage(
    pageData: BookPageData,
    stampsList: List<AlbumStampData>,
    placements: List<StampPlacement>,
    editState: AlbumEditState,
    albumLayoutRepo: AlbumLayoutRepository,
    onStampClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (editState.mode == AlbumEditMode.EDIT) {
                    Modifier.pointerInput(pageData.pageIndex) {
                        detectTapGestures(
                            onTap = {
                                // Background tap deselects active stamp and sets active page
                                editState.activePageIndex = pageData.pageIndex
                                editState.selectPlacement(null)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        val stampMap = remember(stampsList) { stampsList.associateBy { it.id } }

        val pagePlacements = remember(placements, pageData.pageIndex) {
            placements.filter { it.pageIndex == pageData.pageIndex }
        }

        val sortedPlacements = remember(pagePlacements) {
            pagePlacements.sortedWith(
                compareBy<StampPlacement> { it.zIndex }
                    .thenBy { it.id }
            )
        }

        if (sortedPlacements.isEmpty()) {
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
        } else {
            for (placement in sortedPlacements) {
                val stamp = stampMap[placement.stampId]
                if (stamp != null) {
                    EditableStampPlacement(
                        placement = placement,
                        stamp = stamp,
                        pageWidthDp = pageWidth,
                        pageHeightDp = pageHeight,
                        editState = editState,
                        albumLayoutRepo = albumLayoutRepo,
                        onStampClick = onStampClick,
                        modifier = Modifier
                    )
                }
            }
        }

        // Page Number Indicator (1-based display for users)
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
}
