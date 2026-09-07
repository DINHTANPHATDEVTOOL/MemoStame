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
 * Spread 0: Left = Inside Front Cover (metadata/curator), Right = Page 1 (or empty title page).
 * Spread S (S >= 1):
 *   Left = Page (2 * S)
 *   Right = Page (2 * S + 1) or Archival Blank Page if odd count.
 */
fun calculateSpreads(
    albumId: String,
    stamps: List<AlbumStampData>,
    stampsPerPage: Int = 4
): List<BookSpread> {
    if (stamps.isEmpty()) {
        return listOf(
            BookSpread(
                spreadIndex = 0,
                leftPage = BookPageData(
                    albumId = albumId,
                    pageIndex = 0,
                    stamps = emptyList(),
                    isInsideCover = true
                ),
                rightPage = BookPageData(
                    albumId = albumId,
                    pageIndex = 1,
                    stamps = emptyList(),
                    isBlankArchival = false
                )
            )
        )
    }

    val chunks = stamps.chunked(stampsPerPage)
    val totalContentPages = chunks.size
    val spreads = mutableListOf<BookSpread>()

    // Spread 0: Inside cover on left, Page 1 on right
    spreads.add(
        BookSpread(
            spreadIndex = 0,
            leftPage = BookPageData(
                albumId = albumId,
                pageIndex = 0,
                stamps = emptyList(),
                isInsideCover = true
            ),
            rightPage = BookPageData(
                albumId = albumId,
                pageIndex = 1,
                stamps = chunks[0]
            )
        )
    )

    // Subsequent spreads (Spread 1, Spread 2, ...)
    var currentContentPageIndex = 2
    var spreadIdx = 1

    while (currentContentPageIndex <= totalContentPages) {
        val leftStamps = chunks[currentContentPageIndex - 1]
        val rightStamps = if (currentContentPageIndex < totalContentPages) {
            chunks[currentContentPageIndex]
        } else {
            emptyList()
        }
        val isRightArchival = currentContentPageIndex >= totalContentPages

        spreads.add(
            BookSpread(
                spreadIndex = spreadIdx,
                leftPage = BookPageData(
                    albumId = albumId,
                    pageIndex = currentContentPageIndex,
                    stamps = leftStamps
                ),
                rightPage = BookPageData(
                    albumId = albumId,
                    pageIndex = currentContentPageIndex + 1,
                    stamps = rightStamps,
                    isBlankArchival = isRightArchival
                )
            )
        )

        currentContentPageIndex += 2
        spreadIdx++
    }

    return spreads
}

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
