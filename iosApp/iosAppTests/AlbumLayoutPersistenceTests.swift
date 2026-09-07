import XCTest
import SwiftUI
@testable import iosApp

final class AlbumLayoutPersistenceTests: XCTestCase {

    func testPageIndexAuthority_spreadMappingFollowsZeroBasedInnerPages() {
        // Page Index Authority Contract (Section 2):
        // pageIndex = 0 means first editable inner page (Spread 0 Right)
        // Inside Front Cover is non-editable (pageIndex = -1, isInsideCover = true)
        let albumId = "album_authority_ios"
        let stamps = [
            BookStampItem(id: "s0", name: "Stamp 0", imageUrl: "url0"),
            BookStampItem(id: "s1", name: "Stamp 1", imageUrl: "url1"),
            BookStampItem(id: "s2", name: "Stamp 2", imageUrl: "url2")
        ]
        let placements = [
            PersistedStampPlacementData(id: "p0", albumId: albumId, pageIndex: 0, stampId: "s0", x: 0.2, y: 0.3, scale: 1.0, rotationDegrees: 0.0, zIndex: 1, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p1", albumId: albumId, pageIndex: 1, stampId: "s1", x: 0.5, y: 0.5, scale: 1.2, rotationDegrees: 15.0, zIndex: 2, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p2", albumId: albumId, pageIndex: 2, stampId: "s2", x: 0.8, y: 0.7, scale: 0.9, rotationDegrees: -10.0, zIndex: 1, createdAt: 100, updatedAt: 100)
        ]

        let spreads = calculateSpreads(albumId: albumId, stamps: stamps, placements: placements)
        XCTAssertEqual(spreads.count, 2)

        // Spread 0: Left = inside cover (-1), Right = page 0
        let spread0 = spreads[0]
        XCTAssertEqual(spread0.spreadIndex, 0)
        XCTAssertTrue(spread0.leftPage.isInsideCover)
        XCTAssertEqual(spread0.leftPage.pageIndex, -1)
        XCTAssertEqual(spread0.rightPage.pageIndex, 0)
        XCTAssertEqual(spread0.rightPage.placements.count, 1)
        XCTAssertEqual(spread0.rightPage.placements.first?.id, "p0")

        // Spread 1: Left = page 1, Right = page 2
        let spread1 = spreads[1]
        XCTAssertEqual(spread1.spreadIndex, 1)
        XCTAssertEqual(spread1.leftPage.pageIndex, 1)
        XCTAssertEqual(spread1.leftPage.placements.count, 1)
        XCTAssertEqual(spread1.leftPage.placements.first?.id, "p1")

        XCTAssertEqual(spread1.rightPage.pageIndex, 2)
        XCTAssertEqual(spread1.rightPage.placements.count, 1)
        XCTAssertEqual(spread1.rightPage.placements.first?.id, "p2")
    }

    func testGeometryValidation_acceptsValidRanges_rejectsMalformed() {
        XCTAssertTrue(IOSAlbumLayoutRepository.isValidGeometry(x: 0.0, y: 0.0, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertTrue(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 1.5, rotationDegrees: -45.0, zIndex: 10))
        XCTAssertTrue(IOSAlbumLayoutRepository.isValidGeometry(x: 1.0, y: 1.0, scale: 5.0, rotationDegrees: 360.0, zIndex: 1000))

        // Negative or out of bounds
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: -0.01, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 1.01, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: -0.01, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 1.01, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))

        // Scale bounds
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 0.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 0.04, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 5.01, rotationDegrees: 0.0, zIndex: 0))

        // Rotation bounds
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 1.0, rotationDegrees: -361.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 1.0, rotationDegrees: 361.0, zIndex: 0))

        // Z-Index bounds
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: -1001))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: 1001))

        // NaN & Infinite
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: Double.nan, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
        XCTAssertFalse(IOSAlbumLayoutRepository.isValidGeometry(x: 0.5, y: Double.infinity, scale: 1.0, rotationDegrees: 0.0, zIndex: 0))
    }

    func testDeterministicZOrder_sortsByZIndexAscendingWithStableIdTieBreak() {
        let placements = [
            PersistedStampPlacementData(id: "p_b", albumId: "a1", pageIndex: 0, stampId: "s2", x: 0.5, y: 0.5, scale: 1.0, rotationDegrees: 0.0, zIndex: 5, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p_a", albumId: "a1", pageIndex: 0, stampId: "s1", x: 0.2, y: 0.2, scale: 1.0, rotationDegrees: 0.0, zIndex: 5, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p_c", albumId: "a1", pageIndex: 0, stampId: "s3", x: 0.8, y: 0.8, scale: 1.0, rotationDegrees: 0.0, zIndex: 1, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p_d", albumId: "a1", pageIndex: 0, stampId: "s4", x: 0.1, y: 0.1, scale: 1.0, rotationDegrees: 0.0, zIndex: 10, createdAt: 100, updatedAt: 100)
        ]

        let sorted = placements.sorted {
            if $0.zIndex != $1.zIndex { return $0.zIndex < $1.zIndex }
            return $0.id < $1.id
        }

        XCTAssertEqual(sorted.map { $0.id }, ["p_c", "p_a", "p_b", "p_d"])
    }

    func testVirtualAlbumDetection_identifiesLocPrefix() {
        XCTAssertTrue(IOSAlbumLayoutRepository.isVirtualAlbum("loc_hanoi_old_quarter"))
        XCTAssertTrue(IOSAlbumLayoutRepository.isVirtualAlbum("loc_saigon_post_office"))
        XCTAssertFalse(IOSAlbumLayoutRepository.isVirtualAlbum("col_real_uuid_12345"))
        XCTAssertFalse(IOSAlbumLayoutRepository.isVirtualAlbum("col_favorites"))
    }

    func testBackwardCompatibility_legacyPayloadWithoutLayoutDecodesSuccessfully() throws {
        // Old JSON payload without albumPages or albumPlacements fields
        let legacyJson = """
        {
            "user": {
                "uid": "user_legacy_123",
                "username": "collector",
                "displayName": "Test Collector",
                "avatarUrl": null,
                "bio": "Stamp lover",
                "stampsCreatedCount": 5,
                "stampsCollectedCount": 10,
                "placesVisitedCount": 2
            },
            "stamps": [],
            "collections": []
        }
        """

        let data = legacyJson.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(PersistedPayload.self, from: data)

        XCTAssertEqual(decoded.user?.uid, "user_legacy_123")
        XCTAssertNil(decoded.albumPages)
        XCTAssertNil(decoded.albumPlacements)
    }

    func testUnknownStampId_handledSafelyWithoutCrashing() {
        let albumId = "col_unknown_ios"
        let availableStamps = [BookStampItem(id: "s1", name: "Valid Stamp", imageUrl: "url1")]
        let placements = [
            PersistedStampPlacementData(id: "p1", albumId: albumId, pageIndex: 0, stampId: "s1", x: 0.3, y: 0.3, scale: 1.0, rotationDegrees: 0.0, zIndex: 0, createdAt: 100, updatedAt: 100),
            PersistedStampPlacementData(id: "p2", albumId: albumId, pageIndex: 0, stampId: "s999_foreign", x: 0.7, y: 0.7, scale: 1.0, rotationDegrees: 0.0, zIndex: 1, createdAt: 100, updatedAt: 100)
        ]

        let spreads = calculateSpreads(albumId: albumId, stamps: availableStamps, placements: placements)
        let rightPage = spreads[0].rightPage

        XCTAssertEqual(rightPage.placements.count, 2)
        let stampDict = Dictionary(availableStamps.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let resolved = rightPage.placements.compactMap { stampDict[$0.stampId] }

        XCTAssertEqual(resolved.count, 1)
        XCTAssertEqual(resolved.first?.id, "s1")
    }
}
