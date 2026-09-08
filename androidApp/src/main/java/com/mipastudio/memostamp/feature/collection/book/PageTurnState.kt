package com.mipastudio.memostamp.feature.collection.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Deterministic states for the 3D Stamp Book lifecycle.
 */
enum class BookState {
    CLOSED,
    OPENING,
    OPEN,
    TURNING_FORWARD,
    TURNING_BACKWARD,
    CLOSING
}

/**
 * Direction of interactive page turn.
 */
enum class TurnDirection {
    NONE,
    FORWARD,
    BACKWARD
}

/**
 * Visual stamp model for book pages.
 */
data class AlbumStampData(
    val id: String,
    val name: String,
    val imageUrl: String
)

/**
 * Data represented on an individual book page.
 */
data class BookPageData(
    val albumId: String,
    val pageIndex: Int,
    val stamps: List<AlbumStampData>,
    val placements: List<com.mipastudio.memostamp.domain.model.StampPlacement> = emptyList(),
    val isBlankArchival: Boolean = false,
    val isInsideCover: Boolean = false
)

/**
 * Future-canvas coordinate surface contract for a single page.
 * Coordinates are normalized: x in [0.0..1.0], y in [0.0..1.0].
 */
data class PageBounds(
    val width: Float = 1f,
    val height: Float = 1f
)

data class BookPageContent(
    val albumId: String,
    val pageIndex: Int,
    val pageBounds: PageBounds = PageBounds(),
    val stamps: List<AlbumStampData> = emptyList()
)

/**
 * Two-page spread representation: LEFT PAGE | SPINE | RIGHT PAGE.
 */
data class BookSpread(
    val spreadIndex: Int,
    val leftPage: BookPageData,
    val rightPage: BookPageData
)

/**
 * Calculates deterministic two-page spreads for an album.
 *
 * Page Index Authority (Section 2):
 * pageIndex = 0 means first editable inner page (Spread 0 Right).
 * Inside Front Cover is non-editable (pageIndex = -1, isInsideCover = true).
 *
 * Spread 0: Left = Inside Front Cover, Right = Page 0.
 * Spread S (S >= 1):
 *   Left = Page (2 * S - 1)
 *   Right = Page (2 * S) or Archival Blank Page if odd count.
 */
fun calculateSpreads(
    albumId: String,
    pages: List<com.mipastudio.memostamp.domain.model.AlbumPage> = emptyList(),
    stamps: List<AlbumStampData> = emptyList(),
    placements: List<com.mipastudio.memostamp.domain.model.StampPlacement> = emptyList(),
    stampsPerPage: Int = 4
): List<BookSpread> {
    // 1. CANONICAL PAGE AUTHORITY (Task #80)
    if (pages.isNotEmpty()) {
        val sortedPages = pages.sortedBy { it.pageIndex }
        val totalSpreads = (sortedPages.size / 2) + 1
        val spreads = mutableListOf<BookSpread>()

        for (spreadIdx in 0 until totalSpreads) {
            if (spreadIdx == 0) {
                val p0 = sortedPages[0]
                val p0Placements = placements.filter {
                    (it.pageId.isNotBlank() && it.pageId == p0.id) || it.pageIndex == p0.pageIndex
                }
                spreads.add(
                    BookSpread(
                        spreadIndex = 0,
                        leftPage = BookPageData(
                            albumId = albumId,
                            pageIndex = -1,
                            stamps = emptyList(),
                            placements = emptyList(),
                            isInsideCover = true
                        ),
                        rightPage = BookPageData(
                            albumId = albumId,
                            pageIndex = p0.pageIndex,
                            stamps = stamps,
                            placements = p0Placements,
                            isBlankArchival = false
                        )
                    )
                )
            } else {
                val leftIdx = 2 * spreadIdx - 1
                val rightIdx = 2 * spreadIdx

                val leftPageData = if (leftIdx < sortedPages.size) {
                    val lp = sortedPages[leftIdx]
                    val lpPlacements = placements.filter {
                        (it.pageId.isNotBlank() && it.pageId == lp.id) || it.pageIndex == lp.pageIndex
                    }
                    BookPageData(
                        albumId = albumId,
                        pageIndex = lp.pageIndex,
                        stamps = stamps,
                        placements = lpPlacements,
                        isBlankArchival = false
                    )
                } else {
                    BookPageData(
                        albumId = albumId,
                        pageIndex = leftIdx,
                        stamps = emptyList(),
                        placements = emptyList(),
                        isBlankArchival = true
                    )
                }

                val rightPageData = if (rightIdx < sortedPages.size) {
                    val rp = sortedPages[rightIdx]
                    val rpPlacements = placements.filter {
                        (it.pageId.isNotBlank() && it.pageId == rp.id) || it.pageIndex == rp.pageIndex
                    }
                    BookPageData(
                        albumId = albumId,
                        pageIndex = rp.pageIndex,
                        stamps = stamps,
                        placements = rpPlacements,
                        isBlankArchival = false
                    )
                } else {
                    BookPageData(
                        albumId = albumId,
                        pageIndex = rightIdx,
                        stamps = emptyList(),
                        placements = emptyList(),
                        isBlankArchival = true
                    )
                }

                spreads.add(
                    BookSpread(
                        spreadIndex = spreadIdx,
                        leftPage = leftPageData,
                        rightPage = rightPageData
                    )
                )
            }
        }
        return spreads
    }

    // 2. Legacy Placements Fallback
    if (placements.isNotEmpty()) {
        val maxPlacementPage = placements.maxOfOrNull { it.pageIndex } ?: 0
        val maxPage = maxOf(0, maxPlacementPage)
        val totalSpreads = (maxPage / 2) + 1
        val spreads = mutableListOf<BookSpread>()

        for (spreadIdx in 0 until totalSpreads) {
            if (spreadIdx == 0) {
                spreads.add(
                    BookSpread(
                        spreadIndex = 0,
                        leftPage = BookPageData(
                            albumId = albumId,
                            pageIndex = -1,
                            stamps = emptyList(),
                            placements = emptyList(),
                            isInsideCover = true
                        ),
                        rightPage = BookPageData(
                            albumId = albumId,
                            pageIndex = 0,
                            stamps = stamps,
                            placements = placements.filter { it.pageIndex == 0 },
                            isBlankArchival = false
                        )
                    )
                )
            } else {
                val leftPageIndex = 2 * spreadIdx - 1
                val rightPageIndex = 2 * spreadIdx
                val leftPlacements = placements.filter { it.pageIndex == leftPageIndex }
                val rightPlacements = placements.filter { it.pageIndex == rightPageIndex }
                val isRightArchival = rightPageIndex > maxPage && rightPlacements.isEmpty()

                spreads.add(
                    BookSpread(
                        spreadIndex = spreadIdx,
                        leftPage = BookPageData(
                            albumId = albumId,
                            pageIndex = leftPageIndex,
                            stamps = stamps,
                            placements = leftPlacements,
                            isBlankArchival = false
                        ),
                        rightPage = BookPageData(
                            albumId = albumId,
                            pageIndex = rightPageIndex,
                            stamps = stamps,
                            placements = rightPlacements,
                            isBlankArchival = isRightArchival
                        )
                    )
                )
            }
        }
        return spreads
    }

    // Default Fallback when no placements exist
    if (stamps.isEmpty()) {
        return listOf(
            BookSpread(
                spreadIndex = 0,
                leftPage = BookPageData(
                    albumId = albumId,
                    pageIndex = -1,
                    stamps = emptyList(),
                    isInsideCover = true
                ),
                rightPage = BookPageData(
                    albumId = albumId,
                    pageIndex = 0,
                    stamps = emptyList(),
                    isBlankArchival = false
                )
            )
        )
    }

    val chunks = stamps.chunked(stampsPerPage)
    val totalContentPages = chunks.size
    val spreads = mutableListOf<BookSpread>()

    // Spread 0: Inside cover on left (pageIndex = -1), Page 0 on right (chunks[0])
    spreads.add(
        BookSpread(
            spreadIndex = 0,
            leftPage = BookPageData(
                albumId = albumId,
                pageIndex = -1,
                stamps = emptyList(),
                isInsideCover = true
            ),
            rightPage = BookPageData(
                albumId = albumId,
                pageIndex = 0,
                stamps = chunks[0]
            )
        )
    )

    // Subsequent spreads (Spread 1: Page 1 & 2, Spread 2: Page 3 & 4...)
    var contentPageIdx = 1
    var spreadIdx = 1

    while (contentPageIdx < totalContentPages) {
        val leftStamps = chunks[contentPageIdx]
        val rightStamps = if (contentPageIdx + 1 < totalContentPages) {
            chunks[contentPageIdx + 1]
        } else {
            emptyList()
        }
        val isRightArchival = contentPageIdx + 1 >= totalContentPages

        spreads.add(
            BookSpread(
                spreadIndex = spreadIdx,
                leftPage = BookPageData(
                    albumId = albumId,
                    pageIndex = contentPageIdx,
                    stamps = leftStamps
                ),
                rightPage = BookPageData(
                    albumId = albumId,
                    pageIndex = contentPageIdx + 1,
                    stamps = rightStamps,
                    isBlankArchival = isRightArchival
                )
            )
        )

        contentPageIdx += 2
        spreadIdx++
    }

    return spreads
}

fun calculateSpreads(
    albumId: String,
    stamps: List<AlbumStampData>,
    placements: List<com.mipastudio.memostamp.domain.model.StampPlacement> = emptyList(),
    stampsPerPage: Int = 4
): List<BookSpread> = calculateSpreads(
    albumId = albumId,
    pages = emptyList(),
    stamps = stamps,
    placements = placements,
    stampsPerPage = stampsPerPage
)


/**
 * Pure state machine tracking transient book state.
 * Never persists animation/gesture state to Room/Supabase.
 */
class PageTurnStateMachine(
    val albumId: String,
    val initialSpread: Int = 0
) {
    var bookState by mutableStateOf(BookState.CLOSED)
        private set

    var currentSpreadIndex by mutableIntStateOf(initialSpread)
        private set

    var turnProgress by mutableFloatStateOf(0f)
        private set

    var turnDirection by mutableStateOf(TurnDirection.NONE)
        private set

    var interactionLocked by mutableStateOf(false)
        private set

    fun open() {
        if (bookState == BookState.CLOSED && !interactionLocked) {
            interactionLocked = true
            bookState = BookState.OPENING
        }
    }

    fun settleOpen() {
        bookState = BookState.OPEN
        turnProgress = 0f
        turnDirection = TurnDirection.NONE
        interactionLocked = false
    }

    fun close() {
        if (bookState == BookState.OPEN && !interactionLocked) {
            interactionLocked = true
            bookState = BookState.CLOSING
        }
    }

    fun settleClosed() {
        bookState = BookState.CLOSED
        currentSpreadIndex = 0
        turnProgress = 0f
        turnDirection = TurnDirection.NONE
        interactionLocked = false
    }

    fun startTurn(direction: TurnDirection, totalSpreads: Int): Boolean {
        if (bookState != BookState.OPEN || interactionLocked) return false
        if (direction == TurnDirection.FORWARD && currentSpreadIndex >= totalSpreads - 1) return false
        if (direction == TurnDirection.BACKWARD && currentSpreadIndex <= 0) return false

        turnDirection = direction
        turnProgress = 0f
        bookState = if (direction == TurnDirection.FORWARD) {
            BookState.TURNING_FORWARD
        } else {
            BookState.TURNING_BACKWARD
        }
        return true
    }

    fun updateProgress(progress: Float) {
        if (bookState == BookState.TURNING_FORWARD || bookState == BookState.TURNING_BACKWARD) {
            turnProgress = progress.coerceIn(0f, 1f)
        }
    }

    fun finishTurn(completed: Boolean, totalSpreads: Int) {
        if (completed) {
            if (turnDirection == TurnDirection.FORWARD && currentSpreadIndex < totalSpreads - 1) {
                currentSpreadIndex += 1
            } else if (turnDirection == TurnDirection.BACKWARD && currentSpreadIndex > 0) {
                currentSpreadIndex -= 1
            }
        }
        turnProgress = 0f
        turnDirection = TurnDirection.NONE
        bookState = BookState.OPEN
        interactionLocked = false
    }

    fun goToSpread(spreadIndex: Int) {
        currentSpreadIndex = spreadIndex.coerceAtLeast(0)
        turnProgress = 0f
        turnDirection = TurnDirection.NONE
        interactionLocked = false
    }

    fun reset() {
        bookState = BookState.CLOSED
        currentSpreadIndex = 0
        turnProgress = 0f
        turnDirection = TurnDirection.NONE
        interactionLocked = false
    }
}

/**
 * Calculates visual 3D turn parameters from turnProgress [0..1].
 */
data class TurnVisualParams(
    val pageAngleDegrees: Float,
    val shadowAlpha: Float,
    val highlightAlpha: Float,
    val spineCreaseAlpha: Float
)

fun calculateTurnVisuals(progress: Float, isForward: Boolean): TurnVisualParams {
    val clamped = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    // Turning right-to-left for forward: 0 degrees to -180 degrees
    // Turning left-to-right for backward: -180 degrees to 0 degrees
    val angle = if (isForward) {
        -clamped * 180f
    } else {
        -180f + (clamped * 180f)
    }

    val shadowFactor = sin(clamped * PI).toFloat()
    val shadow = (shadowFactor * 0.45f).coerceIn(0f, 0.45f)
    val highlight = (shadowFactor * 0.20f).coerceIn(0f, 0.20f)
    val spine = (0.25f + shadowFactor * 0.25f).coerceIn(0f, 0.5f)

    return TurnVisualParams(
        pageAngleDegrees = angle,
        shadowAlpha = shadow,
        highlightAlpha = highlight,
        spineCreaseAlpha = spine
    )
}
