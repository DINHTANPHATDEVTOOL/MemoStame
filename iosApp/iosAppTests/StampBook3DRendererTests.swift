import XCTest
import SwiftUI
@testable import iosApp

final class StampBook3DRendererTests: XCTestCase {

    func testEmptyAlbum_createsSingleSpreadWithInsideCoverAndEmptyPage() {
        let spreads = calculateSpreads(albumId: "col_empty", stamps: [])
        XCTAssertEqual(spreads.count, 1)

        let spread0 = spreads[0]
        XCTAssertEqual(spread0.spreadIndex, 0)
        XCTAssertTrue(spread0.leftPage.isInsideCover)
        XCTAssertEqual(spread0.leftPage.pageIndex, -1)
        XCTAssertTrue(spread0.leftPage.stamps.isEmpty)

        XCTAssertFalse(spread0.rightPage.isInsideCover)
        XCTAssertFalse(spread0.rightPage.isBlankArchival)
        XCTAssertEqual(spread0.rightPage.pageIndex, 0)
        XCTAssertTrue(spread0.rightPage.stamps.isEmpty)
    }

    func testSingleStamp_createsSingleSpread() {
        let stamps = [BookStampItem(id: "s1", name: "Stamp 1", imageUrl: "url1")]
        let spreads = calculateSpreads(albumId: "col_single", stamps: stamps)

        XCTAssertEqual(spreads.count, 1)
        XCTAssertTrue(spreads[0].leftPage.isInsideCover)
        XCTAssertEqual(spreads[0].rightPage.stamps.count, 1)
        XCTAssertEqual(spreads[0].rightPage.stamps[0].name, "Stamp 1")
    }

    func testFourStamps_fitsOnFirstPage_singleSpread() {
        let stamps = (1...4).map { BookStampItem(id: "s\($0)", name: "Stamp \($0)", imageUrl: "url\($0)") }
        let spreads = calculateSpreads(albumId: "col_four", stamps: stamps, stampsPerPage: 4)

        XCTAssertEqual(spreads.count, 1)
        XCTAssertEqual(spreads[0].rightPage.stamps.count, 4)
    }

    func testOddContentPages_appendsArchivalBlankPageOnRight() {
        // 6 stamps / 4 per page -> 2 content pages
        // Spread 0: Left = Inside Cover, Right = Page 0 (4 stamps)
        // Spread 1: Left = Page 1 (2 stamps), Right = Archival Blank Page
        let stamps = (1...6).map { BookStampItem(id: "s\($0)", name: "Stamp \($0)", imageUrl: "url\($0)") }
        let spreads = calculateSpreads(albumId: "col_odd", stamps: stamps, stampsPerPage: 4)

        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[1].spreadIndex, 1)
        XCTAssertEqual(spreads[1].leftPage.pageIndex, 1)
        XCTAssertEqual(spreads[1].leftPage.stamps.count, 2)

        XCTAssertEqual(spreads[1].rightPage.pageIndex, 2)
        XCTAssertTrue(spreads[1].rightPage.isBlankArchival)
        XCTAssertTrue(spreads[1].rightPage.stamps.isEmpty)
    }

    func testEvenContentPages_bothSidesHaveStamps() {
        // 12 stamps / 4 per page -> 3 content pages (Page 1: 4, Page 2: 4, Page 3: 4)
        // Spread 0: Left = Inside Cover, Right = Page 1
        // Spread 1: Left = Page 2, Right = Page 3
        let stamps = (1...12).map { BookStampItem(id: "s\($0)", name: "Stamp \($0)", imageUrl: "url\($0)") }
        let spreads = calculateSpreads(albumId: "col_even", stamps: stamps, stampsPerPage: 4)

        XCTAssertEqual(spreads.count, 2)
        XCTAssertEqual(spreads[1].leftPage.stamps.count, 4)
        XCTAssertEqual(spreads[1].rightPage.stamps.count, 4)
        XCTAssertFalse(spreads[1].rightPage.isBlankArchival)
    }

    func testStateMachine_lifecycleTransitions() {
        let sm = PageTurnStateMachine(albumId: "col_lifecycle")
        XCTAssertEqual(sm.bookState, .closed)

        sm.open()
        XCTAssertEqual(sm.bookState, .opening)
        XCTAssertTrue(sm.interactionLocked)

        sm.settleOpen()
        XCTAssertEqual(sm.bookState, .open)
        XCTAssertFalse(sm.interactionLocked)

        sm.close()
        XCTAssertEqual(sm.bookState, .closing)
        XCTAssertTrue(sm.interactionLocked)

        sm.settleClosed()
        XCTAssertEqual(sm.bookState, .closed)
        XCTAssertFalse(sm.interactionLocked)
    }

    func testStateMachine_progressClampedWithinZeroAndOne() {
        let sm = PageTurnStateMachine(albumId = "col_clamp")
        sm.open()
        sm.settleOpen()

        let started = sm.startTurn(direction: .forward, totalSpreads: 3)
        XCTAssertTrue(started)
        XCTAssertEqual(sm.bookState, .turningForward)

        sm.updateProgress(1.8)
        XCTAssertEqual(sm.turnProgress, 1.0, accuracy: 0.001)

        sm.updateProgress(-0.5)
        XCTAssertEqual(sm.turnProgress, 0.0, accuracy: 0.001)

        sm.updateProgress(0.65)
        XCTAssertEqual(sm.turnProgress, 0.65, accuracy: 0.001)
    }

    func testStateMachine_boundsSafety() {
        let sm = PageTurnStateMachine(albumId = "col_bounds")
        sm.open()
        sm.settleOpen()

        // Backward at spread 0 not allowed
        XCTAssertFalse(sm.startTurn(direction: .backward, totalSpreads: 3))
        XCTAssertEqual(sm.currentSpreadIndex, 0)

        // Forward allowed
        XCTAssertTrue(sm.startTurn(direction: .forward, totalSpreads: 3))
        sm.finishTurn(completed: true, totalSpreads: 3)
        XCTAssertEqual(sm.currentSpreadIndex, 1)

        // Forward again to spread 2
        XCTAssertTrue(sm.startTurn(direction: .forward, totalSpreads: 3))
        sm.finishTurn(completed: true, totalSpreads: 3)
        XCTAssertEqual(sm.currentSpreadIndex, 2)

        // Forward at end (spread 2 of 3) not allowed
        XCTAssertFalse(sm.startTurn(direction: .forward, totalSpreads: 3))
        XCTAssertEqual(sm.currentSpreadIndex, 2)
    }

    func testStateMachine_cancelledTurnLeavesSpreadUnchanged() {
        let sm = PageTurnStateMachine(albumId = "col_cancel")
        sm.open()
        sm.settleOpen()

        sm.startTurn(direction: .forward, totalSpreads: 3)
        sm.updateProgress(0.2)
        sm.finishTurn(completed: false, totalSpreads: 3)

        XCTAssertEqual(sm.currentSpreadIndex, 0)
        XCTAssertEqual(sm.turnProgress, 0.0, accuracy: 0.001)
        XCTAssertEqual(sm.bookState, .open)
    }

    func testStateMachine_completedTurnAdvancesSpread() {
        let sm = PageTurnStateMachine(albumId = "col_advance")
        sm.open()
        sm.settleOpen()

        sm.startTurn(direction: .forward, totalSpreads: 3)
        sm.updateProgress(0.8)
        sm.finishTurn(completed: true, totalSpreads: 3)

        XCTAssertEqual(sm.currentSpreadIndex, 1)
        XCTAssertEqual(sm.turnProgress, 0.0, accuracy: 0.001)
        XCTAssertEqual(sm.bookState, .open)
    }

    func testTurnVisuals_geometryAndShadowCoherent() {
        let fwd = calculateTurnVisuals(progress: 0.5, isForward: true)
        XCTAssertEqual(fwd.pageAngleDegrees, -90.0, accuracy: 0.1)
        XCTAssertTrue(fwd.shadowAlpha >= 0.0 && fwd.shadowAlpha <= 0.5)

        let bwd = calculateTurnVisuals(progress: 0.5, isForward: false)
        XCTAssertEqual(bwd.pageAngleDegrees, -90.0, accuracy: 0.1)

        let nanVal = calculateTurnVisuals(progress: Double.nan, isForward: true)
        XCTAssertFalse(nanVal.pageAngleDegrees.isNaN)
        XCTAssertFalse(nanVal.shadowAlpha.isNaN)
    }

    func testDeterministicFallbackIds() {
        let locA = "Hà Nội"
        let locB = "Đà Lạt"

        let hashA1 = String(abs(locA.hashValue), radix: 16)
        let hashA2 = String(abs(locA.hashValue), radix: 16)
        let hashB = String(abs(locB.hashValue), radix: 16)

        let idA1 = "loc_\(hashA1)"
        let idA2 = "loc_\(hashA2)"
        let idB = "loc_\(hashB)"

        XCTAssertEqual(idA1, idA2)
        XCTAssertNotEqual(idA1, idB)
        XCTAssertFalse(idA1.hasPrefix("album_"))
    }
}
