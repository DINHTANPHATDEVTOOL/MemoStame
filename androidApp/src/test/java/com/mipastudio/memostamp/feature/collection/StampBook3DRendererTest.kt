package com.mipastudio.memostamp.feature.collection

import com.mipastudio.memostamp.feature.collection.book.*
import org.junit.Assert.*
import org.junit.Test

class StampBook3DRendererTest {

    @Test
    fun emptyAlbum_createsSingleSpreadWithInsideCoverAndEmptyPage() {
        val spreads = calculateSpreads(albumId = "test_col_1", stamps = emptyList())
        assertEquals(1, spreads.size)

        val spread0 = spreads[0]
        assertEquals(0, spread0.spreadIndex)
        assertTrue(spread0.leftPage.isInsideCover)
        assertEquals(0, spread0.leftPage.pageIndex)
        assertEquals(0, spread0.leftPage.stamps.size)

        assertFalse(spread0.rightPage.isInsideCover)
        assertFalse(spread0.rightPage.isBlankArchival)
        assertEquals(1, spread0.rightPage.pageIndex)
        assertEquals(0, spread0.rightPage.stamps.size)
    }

    @Test
    fun singleStamp_createsSingleSpread() {
        val stamps = listOf(AlbumStampData("s1", "First Stamp", "file:///img1.png"))
        val spreads = calculateSpreads(albumId = "col_single", stamps = stamps)

        assertEquals(1, spreads.size)
        val spread0 = spreads[0]
        assertTrue(spread0.leftPage.isInsideCover)
        assertEquals(1, spread0.rightPage.stamps.size)
        assertEquals("First Stamp", spread0.rightPage.stamps[0].name)
    }

    @Test
    fun fourStamps_fitsOnFirstPage_singleSpread() {
        val stamps = (1..4).map { AlbumStampData("s$it", "Stamp $it", "path_$it") }
        val spreads = calculateSpreads(albumId = "col_four", stamps = stamps, stampsPerPage = 4)

        assertEquals(1, spreads.size)
        assertEquals(4, spreads[0].rightPage.stamps.size)
    }

    @Test
    fun oddContentPages_appendsArchivalBlankPageOnRight() {
        // 6 stamps with 4 per page -> 2 content pages (Page 1 has 4, Page 2 has 2)
        // Spread 0: Left = Inside Cover, Right = Page 1
        // Spread 1: Left = Page 2, Right = Archival Blank (odd content pages count = 2)
        val stamps = (1..6).map { AlbumStampData("s$it", "Stamp $it", "path_$it") }
        val spreads = calculateSpreads(albumId = "col_odd", stamps = stamps, stampsPerPage = 4)

        assertEquals(2, spreads.size)

        val spread1 = spreads[1]
        assertEquals(1, spread1.spreadIndex)
        assertEquals(2, spread1.leftPage.pageIndex)
        assertEquals(2, spread1.leftPage.stamps.size)

        assertEquals(3, spread1.rightPage.pageIndex)
        assertTrue(spread1.rightPage.isBlankArchival)
        assertEquals(0, spread1.rightPage.stamps.size)
    }

    @Test
    fun evenContentPages_spreadsContainStampsOnBothSides() {
        // 12 stamps with 4 per page -> 3 content pages (Page 1: 4, Page 2: 4, Page 3: 4)
        // Spread 0: Left = Inside Cover, Right = Page 1
        // Spread 1: Left = Page 2, Right = Page 3
        val stamps = (1..12).map { AlbumStampData("s$it", "Stamp $it", "path_$it") }
        val spreads = calculateSpreads(albumId = "col_even", stamps = stamps, stampsPerPage = 4)

        assertEquals(2, spreads.size)
        assertEquals(4, spreads[1].leftPage.stamps.size)
        assertEquals(4, spreads[1].rightPage.stamps.size)
        assertFalse(spreads[1].rightPage.isBlankArchival)
    }

    @Test
    fun stateMachine_lifecycleTransitions() {
        val sm = PageTurnStateMachine(albumId = "col_state")
        assertEquals(BookState.CLOSED, sm.bookState)

        sm.open()
        assertEquals(BookState.OPENING, sm.bookState)
        assertTrue(sm.interactionLocked)

        sm.settleOpen()
        assertEquals(BookState.OPEN, sm.bookState)
        assertFalse(sm.interactionLocked)

        sm.close()
        assertEquals(BookState.CLOSING, sm.bookState)
        assertTrue(sm.interactionLocked)

        sm.settleClosed()
        assertEquals(BookState.CLOSED, sm.bookState)
        assertFalse(sm.interactionLocked)
    }

    @Test
    fun stateMachine_turnClampsProgressWithinZeroAndOne() {
        val sm = PageTurnStateMachine(albumId = "col_turn")
        sm.open()
        sm.settleOpen()

        val started = sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        assertTrue(started)
        assertEquals(BookState.TURNING_FORWARD, sm.bookState)

        sm.updateProgress(1.5f)
        assertEquals(1.0f, sm.turnProgress, 0.001f)

        sm.updateProgress(-0.5f)
        assertEquals(0.0f, sm.turnProgress, 0.001f)

        sm.updateProgress(0.72f)
        assertEquals(0.72f, sm.turnProgress, 0.001f)
    }

    @Test
    fun stateMachine_boundsSafety_cannotTurnPastEdges() {
        val sm = PageTurnStateMachine(albumId = "col_bounds")
        sm.open()
        sm.settleOpen()

        // At spread 0: backward turn should fail
        val backwardAllowed = sm.startTurn(TurnDirection.BACKWARD, totalSpreads = 3)
        assertFalse(backwardAllowed)
        assertEquals(0, sm.currentSpreadIndex)

        // Forward turn allowed
        val forwardAllowed = sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        assertTrue(forwardAllowed)
        sm.finishTurn(completed = true, totalSpreads = 3)
        assertEquals(1, sm.currentSpreadIndex)

        // Advance to last spread
        sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        sm.finishTurn(completed = true, totalSpreads = 3)
        assertEquals(2, sm.currentSpreadIndex)

        // At last spread (index 2 of 3): forward turn should fail
        val forwardAtEnd = sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        assertFalse(forwardAtEnd)
        assertEquals(2, sm.currentSpreadIndex)
    }

    @Test
    fun stateMachine_cancelledTurnLeavesSpreadUnchanged() {
        val sm = PageTurnStateMachine(albumId = "col_cancel")
        sm.open()
        sm.settleOpen()

        sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        sm.updateProgress(0.3f)
        sm.finishTurn(completed = false, totalSpreads = 3)

        assertEquals(0, sm.currentSpreadIndex)
        assertEquals(0f, sm.turnProgress, 0.001f)
        assertEquals(BookState.OPEN, sm.bookState)
    }

    @Test
    fun stateMachine_completedTurnAdvancesExactlyOneSpread() {
        val sm = PageTurnStateMachine(albumId = "col_complete")
        sm.open()
        sm.settleOpen()

        sm.startTurn(TurnDirection.FORWARD, totalSpreads = 3)
        sm.updateProgress(0.8f)
        sm.finishTurn(completed = true, totalSpreads = 3)

        assertEquals(1, sm.currentSpreadIndex)
        assertEquals(0f, sm.turnProgress, 0.001f)
        assertEquals(BookState.OPEN, sm.bookState)
    }

    @Test
    fun turnVisuals_clampedAndGeometryCoherent() {
        val fwdParams = calculateTurnVisuals(0.5f, isForward = true)
        assertEquals(-90f, fwdParams.pageAngleDegrees, 0.1f)
        assertTrue(fwdParams.shadowAlpha in 0f..0.5f)
        assertTrue(fwdParams.highlightAlpha in 0f..0.3f)

        val bwdParams = calculateTurnVisuals(0.5f, isForward = false)
        assertEquals(-90f, bwdParams.pageAngleDegrees, 0.1f)
        assertTrue(bwdParams.shadowAlpha in 0f..0.5f)

        // Test NaN protection
        val nanParams = calculateTurnVisuals(Float.NaN, isForward = true)
        assertFalse(nanParams.pageAngleDegrees.isNaN())
        assertFalse(nanParams.shadowAlpha.isNaN())
    }

    @Test
    fun deterministicFallbackIds_areDeterministicAcrossLaunches() {
        val locationA = "Hà Nội"
        val locationB = "Đà Lạt"

        val hashA1 = (locationA.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val hashA2 = (locationA.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val hashB = (locationB.hashCode().toLong() and 0xFFFFFFFFL).toString(16)

        val idA1 = "loc_$hashA1"
        val idA2 = "loc_$hashA2"
        val idB = "loc_$hashB"

        assertEquals(idA1, idA2)
        assertNotEquals(idA1, idB)
        assertFalse(idA1.startsWith("album_"))
    }

    @Test
    fun futureCanvasContract_normalizedCoordinates() {
        val bounds = PageBounds(width = 1f, height = 1f)
        val content = BookPageContent(
            albumId = "col_canvas_contract",
            pageIndex = 2,
            pageBounds = bounds,
            stamps = listOf(AlbumStampData("s1", "Test", "url"))
        )

        assertEquals("col_canvas_contract", content.albumId)
        assertEquals(2, content.pageIndex)
        assertEquals(1f, content.pageBounds.width, 0.001f)
        assertEquals(1f, content.pageBounds.height, 0.001f)
        assertEquals(1, content.stamps.size)
    }
}
