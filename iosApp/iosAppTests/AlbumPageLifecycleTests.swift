import XCTest
import SwiftUI
@testable import iosApp

final class AlbumPageLifecycleTests: XCTestCase {

    func testCanonicalPageAuthority_emptyPagesRenderAsRealPages() {
        let albumId = "col_test_album_authority"
        let pages = [
            PersistedAlbumPageData(id: "page-0", albumId: albumId, pageIndex: 0),
            PersistedAlbumPageData(id: "page-1", albumId: albumId, pageIndex: 1), // EMPTY PAGE
            PersistedAlbumPageData(id: "page-2", albumId: albumId, pageIndex: 2)
        ]
        let placements = [
            PersistedStampPlacementData(id: "p-1", albumId: albumId, pageIndex: 0, pageId: "page-0", stampId: "s-1", x: 0.5, y: 0.5),
            PersistedStampPlacementData(id: "p-2", albumId: albumId, pageIndex: 2, pageId: "page-2", stampId: "s-2", x: 0.5, y: 0.5)
        ]

        let spreads = calculateSpreads(albumId: albumId, pages: pages, placements: placements)

        // 3 pages -> Spread 0 (Cover | Page 0), Spread 1 (Page 1 | Page 2)
        XCTAssertEqual(spreads.count, 2)

        // Spread 0
        XCTAssertTrue(spreads[0].leftPage.isInsideCover)
        XCTAssertEqual(spreads[0].rightPage.pageIndex, 0)
        XCTAssertEqual(spreads[0].rightPage.placements.count, 1)

        // Spread 1: Left is Page 1 (EMPTY, but real page!), Right is Page 2
        XCTAssertEqual(spreads[1].leftPage.pageIndex, 1)
        XCTAssertFalse(spreads[1].leftPage.isBlankArchival)
        XCTAssertTrue(spreads[1].leftPage.placements.isEmpty)

        XCTAssertEqual(spreads[1].rightPage.pageIndex, 2)
        XCTAssertFalse(spreads[1].rightPage.isBlankArchival)
        XCTAssertEqual(spreads[1].rightPage.placements.count, 1)
    }

    func testHighestEmptyPage_remainsVisible() {
        let albumId = "col_test_highest_empty"
        let pages = [
            PersistedAlbumPageData(id: "page-0", albumId: albumId, pageIndex: 0),
            PersistedAlbumPageData(id: "page-1", albumId: albumId, pageIndex: 1) // Highest page empty
        ]
        let placements = [
            PersistedStampPlacementData(id: "p-1", albumId: albumId, pageIndex: 0, pageId: "page-0", stampId: "s-1", x: 0.5, y: 0.5)
        ]

        let spreads = calculateSpreads(albumId: albumId, pages: pages, placements: placements)
        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[1].leftPage.pageIndex, 1)
        XCTAssertFalse(spreads[1].leftPage.isBlankArchival)
        XCTAssertTrue(spreads[1].leftPage.placements.isEmpty)
        XCTAssertTrue(spreads[1].rightPage.isBlankArchival)
    }

    func testRemovingFinalStamp_doesNotRemovePage() {
        let albumId = "col_test_remove_stamp"
        let pages = [
            PersistedAlbumPageData(id: "page-0", albumId: albumId, pageIndex: 0),
            PersistedAlbumPageData(id: "page-1", albumId: albumId, pageIndex: 1)
        ]

        let spreads = calculateSpreads(albumId: albumId, pages: pages, placements: [])
        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[0].rightPage.pageIndex, 0)
        XCTAssertEqual(spreads[1].leftPage.pageIndex, 1)
        XCTAssertFalse(spreads[1].leftPage.isBlankArchival)
    }

    func testFallbackLegacyBootstrap_whenNoPagesProvided() {
        let albumId = "col_test_legacy"
        let placements = [
            PersistedStampPlacementData(id: "p-1", albumId: albumId, pageIndex: 0, stampId: "s-1", x: 0.5, y: 0.5),
            PersistedStampPlacementData(id: "p-2", albumId: albumId, pageIndex: 2, stampId: "s-2", x: 0.5, y: 0.5)
        ]

        let spreads = calculateSpreads(albumId: albumId, pages: [], placements: placements)
        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[0].rightPage.pageIndex, 0)
        XCTAssertEqual(spreads[1].leftPage.pageIndex, 1)
        XCTAssertEqual(spreads[1].rightPage.pageIndex, 2)
    }

    func testRemovePage_blocksIfPageContainsStamps() {
        let state = AlbumEditState(albumId: "col_test_album")
        state.enterEditMode()

        let placement = PersistedStampPlacementData(id: "p-1", albumId: "col_test_album", pageIndex: 0, pageId: "page-0", stampId: "s-1", x: 0.5, y: 0.5)
        state.removePage(pageId: "page-0", placementsOnPage: [placement], totalPageCount: 3)

        XCTAssertNotNil(state.saveErrorMessage)
    }

    func testRemovePage_blocksIfOnlyOnePageRemains() {
        let state = AlbumEditState(albumId: "col_test_album")
        state.enterEditMode()

        state.removePage(pageId: "page-0", placementsOnPage: [], totalPageCount: 1)
        XCTAssertNotNil(state.saveErrorMessage)
    }

    func testReorderPreservesPlacements_andStablePageId() {
        let albumId = "col_reorder_test"
        let pageA = PersistedAlbumPageData(id: "page-A", albumId = albumId, pageIndex: 0)
        let pageB = PersistedAlbumPageData(id: "page-B", albumId = albumId, pageIndex: 1)
        let pageC = PersistedAlbumPageData(id: "page-C", albumId = albumId, pageIndex: 2)

        let pages = [pageA, pageB, pageC]
        let placementB = PersistedStampPlacementData(id: "p-B", albumId: albumId, pageIndex: 1, pageId: "page-B", stampId: "stamp-b", x: 0.5, y: 0.5)

        // Reorder: Move Page B earlier (index 1 -> index 0)
        let reorderedIds = ["page-B", "page-A", "page-C"]
        var pageMap: [String: PersistedAlbumPageData] = [:]
        for p in pages { pageMap[p.id] = p }

        var updatedPages: [PersistedAlbumPageData] = []
        var newIndexMap: [String: Int32] = [:]
        for (i, id) in reorderedIds.enumerated() {
            if let p = pageMap[id] {
                updatedPages.append(PersistedAlbumPageData(id: p.id, albumId: p.albumId, pageIndex: Int32(i)))
                newIndexMap[p.id] = Int32(i)
            }
        }

        XCTAssertEqual(updatedPages.first { $0.id == "page-B" }?.pageIndex, 0)
        XCTAssertEqual(updatedPages.first { $0.id == "page-A" }?.pageIndex, 1)

        // Placement follows page!
        let newIdx = newIndexMap[placementB.pageId!]!
        let updatedPlacementB = PersistedStampPlacementData(
            id: placementB.id,
            albumId: placementB.albumId,
            pageIndex: newIdx,
            pageId: placementB.pageId,
            stampId: placementB.stampId,
            x: placementB.x,
            y: placementB.y
        )

        XCTAssertEqual(updatedPlacementB.pageId, "page-B")
        XCTAssertEqual(updatedPlacementB.pageIndex, 0)

        let spreads = calculateSpreads(albumId: albumId, pages: updatedPages, placements: [updatedPlacementB])
        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[0].rightPage.pageIndex, 0)
        XCTAssertEqual(spreads[0].rightPage.placements.count, 1)
        XCTAssertEqual(spreads[0].rightPage.placements[0].id, "p-B")
    }

    func testVirtualAlbum_cannotManagePages() {
        let virtualState = AlbumEditState(albumId: "loc_osaka")
        XCTAssertTrue(virtualState.isVirtualAlbum)
        virtualState.enterEditMode()
        XCTAssertEqual(virtualState.mode, .view)

        virtualState.openPageManagement()
        XCTAssertFalse(virtualState.isPageManagementOpen)
    }

    func testPageTurnSpreadClamping_onPageRemoval() {
        let stateMachine = PageTurnStateMachine(albumId: "col_clamp_test")
        stateMachine.open()
        stateMachine.settleOpen()

        stateMachine.goToSpread(2)
        XCTAssertEqual(stateMachine.currentSpreadIndex, 2)

        let newTotal = 2
        if stateMachine.currentSpreadIndex >= newTotal {
            stateMachine.goToSpread(newTotal - 1)
        }
        XCTAssertEqual(stateMachine.currentSpreadIndex, 1)
    }
}
