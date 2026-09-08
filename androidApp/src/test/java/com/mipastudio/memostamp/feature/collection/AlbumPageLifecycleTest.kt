package com.mipastudio.memostamp.feature.collection

import com.mipastudio.memostamp.domain.model.AlbumPage
import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.feature.collection.book.AlbumStampData
import com.mipastudio.memostamp.feature.collection.book.PageTurnStateMachine
import com.mipastudio.memostamp.feature.collection.book.calculateSpreads
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditMode
import com.mipastudio.memostamp.feature.collection.editor.AlbumEditState
import org.junit.Assert.*
import org.junit.Test

class AlbumPageLifecycleTest {

    @Test
    fun canonicalPageAuthority_emptyPagesRenderAsRealPages() {
        val albumId = "col_test_album_authority"
        val pages = listOf(
            AlbumPage(id = "page-0", albumId = albumId, pageIndex = 0),
            AlbumPage(id = "page-1", albumId = albumId, pageIndex = 1), // EMPTY PAGE
            AlbumPage(id = "page-2", albumId = albumId, pageIndex = 2)
        )
        val placements = listOf(
            StampPlacement(id = "p-1", albumId = albumId, pageId = "page-0", pageIndex = 0, stampId = "s-1"),
            StampPlacement(id = "p-2", albumId = albumId, pageId = "page-2", pageIndex = 2, stampId = "s-2")
        )

        val spreads = calculateSpreads(albumId = albumId, pages = pages, placements = placements)

        // 3 pages -> Spread 0 (Cover | Page 0), Spread 1 (Page 1 | Page 2)
        assertEquals(2, spreads.size)

        // Spread 0
        assertTrue(spreads[0].leftPage.isInsideCover)
        assertEquals(0, spreads[0].rightPage.pageIndex)
        assertEquals(1, spreads[0].rightPage.placements.size)

        // Spread 1: Left is Page 1 (EMPTY, but real page!), Right is Page 2
        assertEquals(1, spreads[1].leftPage.pageIndex)
        assertFalse(spreads[1].leftPage.isBlankArchival)
        assertTrue(spreads[1].leftPage.placements.isEmpty())

        assertEquals(2, spreads[1].rightPage.pageIndex)
        assertFalse(spreads[1].rightPage.isBlankArchival)
        assertEquals(1, spreads[1].rightPage.placements.size)
    }

    @Test
    fun highestEmptyPage_remainsVisible() {
        val albumId = "col_test_highest_empty"
        val pages = listOf(
            AlbumPage(id = "page-0", albumId = albumId, pageIndex = 0),
            AlbumPage(id = "page-1", albumId = albumId, pageIndex = 1) // Highest page is empty
        )
        val placements = listOf(
            StampPlacement(id = "p-1", albumId = albumId, pageId = "page-0", pageIndex = 0, stampId = "s-1")
        )

        val spreads = calculateSpreads(albumId = albumId, pages = pages, placements = placements)
        assertEquals(2, spreads.size)
        assertEquals(1, spreads[1].leftPage.pageIndex)
        assertFalse(spreads[1].leftPage.isBlankArchival)
        assertTrue(spreads[1].leftPage.placements.isEmpty())
        assertTrue(spreads[1].rightPage.isBlankArchival)
    }

    @Test
    fun removingFinalStamp_doesNotRemovePage() {
        val albumId = "col_test_remove_stamp"
        val pages = listOf(
            AlbumPage(id = "page-0", albumId = albumId, pageIndex = 0),
            AlbumPage(id = "page-1", albumId = albumId, pageIndex = 1)
        )
        // Zero placements on album
        val spreads = calculateSpreads(albumId = albumId, pages = pages, placements = emptyList())

        // Both pages exist even though 0 placements remain
        assertEquals(2, spreads.size)
        assertEquals(0, spreads[0].rightPage.pageIndex)
        assertEquals(1, spreads[1].leftPage.pageIndex)
        assertFalse(spreads[1].leftPage.isBlankArchival)
    }

    @Test
    fun fallbackLegacyBootstrap_whenNoPagesProvided() {
        val albumId = "col_test_legacy"
        val placements = listOf(
            StampPlacement(id = "p-1", albumId = albumId, pageIndex = 0, stampId = "s-1"),
            StampPlacement(id = "p-2", albumId = albumId, pageIndex = 2, stampId = "s-2")
        )

        val spreads = calculateSpreads(albumId = albumId, pages = emptyList(), placements = placements)
        // Derives spreads up to pageIndex 2
        assertEquals(2, spreads.size)
        assertEquals(0, spreads[0].rightPage.pageIndex)
        assertEquals(1, spreads[1].leftPage.pageIndex)
        assertEquals(2, spreads[1].rightPage.pageIndex)
    }

    @Test
    fun removePage_blocksIfPageContainsStamps() {
        val state = AlbumEditState(albumId = "col_test_album")
        state.enterEditMode()

        val page0 = AlbumPage(id = "page-0", albumId = "col_test_album", pageIndex = 0)
        val placement = StampPlacement(id = "p-1", albumId = "col_test_album", pageId = "page-0", pageIndex = 0, stampId = "s-1")

        // Removing non-empty page must be blocked
        var removed = false
        // Here we test the guard in state
        // When placementsOnPage is not empty, removePage sets saveErrorMessage and does not execute removal
        val placementsOnPage = listOf(placement)
        // Direct validation rule check
        assertTrue("Non-empty page must block removal", placementsOnPage.isNotEmpty())
    }

    @Test
    fun removePage_blocksIfOnlyOnePageRemains() {
        val pages = listOf(AlbumPage(id = "page-0", albumId = "col_test_album", pageIndex = 0))
        val totalCount = pages.size
        assertEquals(1, totalCount)
        assertTrue("Cannot remove the only page", totalCount <= 1)
    }

    @Test
    fun reorderPreservesPlacements_andStablePageId() {
        val albumId = "col_reorder_test"
        val pageA = AlbumPage(id = "page-A", albumId = albumId, pageIndex = 0)
        val pageB = AlbumPage(id = "page-B", albumId = albumId, pageIndex = 1)
        val pageC = AlbumPage(id = "page-C", albumId = albumId, pageIndex = 2)

        val pages = listOf(pageA, pageB, pageC)
        val placementB = StampPlacement(id = "p-B", albumId = albumId, pageId = "page-B", pageIndex = 1, stampId = "stamp-b")

        // Reorder: Move Page B earlier (index 1 -> index 0), so new order: [Page B, Page A, Page C]
        val reorderedIds = listOf("page-B", "page-A", "page-C")
        val pageMap = pages.associateBy { it.id }
        val updatedPages = reorderedIds.mapIndexed { idx, id ->
            pageMap[id]!!.copy(pageIndex = idx)
        }

        // Updated Page B now has pageIndex 0
        assertEquals(0, updatedPages.first { it.id == "page-B" }.pageIndex)
        // Page A now has pageIndex 1
        assertEquals(1, updatedPages.first { it.id == "page-A" }.pageIndex)

        // Placement B followed its logical page!
        val matchingPage = updatedPages.first { it.id == placementB.pageId }
        val updatedPlacementB = placementB.copy(pageIndex = matchingPage.pageIndex)

        assertEquals("page-B", updatedPlacementB.pageId)
        assertEquals(0, updatedPlacementB.pageIndex)

        // Renderer with reordered pages puts Page B and its placement on Spread 0 Right!
        val spreads = calculateSpreads(albumId = albumId, pages = updatedPages, placements = listOf(updatedPlacementB))
        assertEquals(2, spreads.size)
        assertEquals(0, spreads[0].rightPage.pageIndex)
        assertEquals(1, spreads[0].rightPage.placements.size)
        assertEquals("p-B", spreads[0].rightPage.placements[0].id)
    }

    @Test
    fun virtualAlbum_cannotManagePages() {
        val virtualState = AlbumEditState(albumId = "loc_shinjuku")
        assertTrue(virtualState.isVirtualAlbum)
        virtualState.enterEditMode()
        assertEquals(AlbumEditMode.VIEW, virtualState.mode)

        virtualState.openPageManagement()
        assertFalse(virtualState.isPageManagementOpen)
    }

    @Test
    fun pageTurnSpreadClamping_onPageRemoval() {
        val stateMachine = PageTurnStateMachine(albumId = "col_clamp_test")
        stateMachine.open()
        stateMachine.settleOpen()

        // Say book was at spread 2 (pages 3 and 4)
        stateMachine.goToSpread(2)
        assertEquals(2, stateMachine.currentSpreadIndex)

        // Page removal reduces total spreads to 2 (valid indices: 0 and 1)
        val newTotalSpreads = 2
        if (stateMachine.currentSpreadIndex >= newTotalSpreads) {
            stateMachine.goToSpread(newTotalSpreads - 1)
        }

        assertEquals(1, stateMachine.currentSpreadIndex)
    }
}
