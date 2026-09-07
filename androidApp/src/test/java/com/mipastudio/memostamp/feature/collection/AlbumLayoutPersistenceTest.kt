package com.mipastudio.memostamp.feature.collection

import com.mipastudio.memostamp.data.repository.AlbumLayoutRepository
import com.mipastudio.memostamp.domain.model.AlbumPage
import com.mipastudio.memostamp.domain.model.StampPlacement
import com.mipastudio.memostamp.feature.collection.book.AlbumStampData
import com.mipastudio.memostamp.feature.collection.book.calculateSpreads
import org.junit.Assert.*
import org.junit.Test

class AlbumLayoutPersistenceTest {

    @Test
    fun pageIndexAuthority_spreadMappingFollowsZeroBasedInnerPages() {
        // Page Index Authority Contract (Section 2):
        // pageIndex = 0 means first editable inner page (Spread 0 Right)
        // Inside Front Cover is non-editable (pageIndex = -1, isInsideCover = true)
        // Spread S (S >= 1): Left = (2*S - 1), Right = (2*S)
        val albumId = "album_authority_test"
        val stamps = listOf(
            AlbumStampData("s0", "Stamp 0", "url0"),
            AlbumStampData("s1", "Stamp 1", "url1"),
            AlbumStampData("s2", "Stamp 2", "url2")
        )
        val placements = listOf(
            StampPlacement(id = "p0", albumId = albumId, pageIndex = 0, stampId = "s0", x = 0.2, y = 0.3, scale = 1.0, rotationDegrees = 0.0, zIndex = 1),
            StampPlacement(id = "p1", albumId = albumId, pageIndex = 1, stampId = "s1", x = 0.5, y = 0.5, scale = 1.2, rotationDegrees = 15.0, zIndex = 2),
            StampPlacement(id = "p2", albumId = albumId, pageIndex = 2, stampId = "s2", x = 0.8, y = 0.7, scale = 0.9, rotationDegrees = -10.0, zIndex = 1)
        )

        val spreads = calculateSpreads(albumId, stamps, placements)
        assertEquals(2, spreads.size)

        // Spread 0: Left = inside cover (-1), Right = page 0
        val spread0 = spreads[0]
        assertEquals(0, spread0.spreadIndex)
        assertTrue(spread0.leftPage.isInsideCover)
        assertEquals(-1, spread0.leftPage.pageIndex)
        assertEquals(0, spread0.rightPage.pageIndex)
        assertEquals(1, spread0.rightPage.placements.size)
        assertEquals("p0", spread0.rightPage.placements[0].id)

        // Spread 1: Left = page 1, Right = page 2
        val spread1 = spreads[1]
        assertEquals(1, spread1.spreadIndex)
        assertEquals(1, spread1.leftPage.pageIndex)
        assertEquals(1, spread1.leftPage.placements.size)
        assertEquals("p1", spread1.leftPage.placements[0].id)

        assertEquals(2, spread1.rightPage.pageIndex)
        assertEquals(1, spread1.rightPage.placements.size)
        assertEquals("p2", spread1.rightPage.placements[0].id)
    }

    @Test
    fun geometryValidation_acceptsValidRanges_rejectsMalformed() {
        // Valid geometries
        assertTrue(AlbumLayoutRepository.isValidGeometry(0.0, 0.0, 1.0, 0.0, 0))
        assertTrue(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.5, -45.0, 10))
        assertTrue(AlbumLayoutRepository.isValidGeometry(1.0, 1.0, 5.0, 360.0, 1000))

        // Negative or out of bounds coordinates
        assertFalse(AlbumLayoutRepository.isValidGeometry(-0.01, 0.5, 1.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(1.01, 0.5, 1.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, -0.01, 1.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 1.01, 1.0, 0.0, 0))

        // Scale bounds
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 0.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 0.04, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 5.01, 0.0, 0))

        // Rotation bounds
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.0, -361.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.0, 361.0, 0))

        // Z-Index bounds
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.0, 0.0, -1001))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.0, 0.0, 1001))

        // NaN and Infinity safety
        assertFalse(AlbumLayoutRepository.isValidGeometry(Double.NaN, 0.5, 1.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, Double.POSITIVE_INFINITY, 1.0, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, Double.NaN, 0.0, 0))
        assertFalse(AlbumLayoutRepository.isValidGeometry(0.5, 0.5, 1.0, Double.NEGATIVE_INFINITY, 0))
    }

    @Test
    fun deterministicZOrder_sortsByZIndexAscendingWithStableIdTieBreak() {
        val placements = listOf(
            StampPlacement(id = "p_b", albumId = "a1", pageIndex = 0, stampId = "s2", x = 0.5, y = 0.5, scale = 1.0, rotationDegrees = 0.0, zIndex = 5),
            StampPlacement(id = "p_a", albumId = "a1", pageIndex = 0, stampId = "s1", x = 0.2, y = 0.2, scale = 1.0, rotationDegrees = 0.0, zIndex = 5),
            StampPlacement(id = "p_c", albumId = "a1", pageIndex = 0, stampId = "s3", x = 0.8, y = 0.8, scale = 1.0, rotationDegrees = 0.0, zIndex = 1),
            StampPlacement(id = "p_d", albumId = "a1", pageIndex = 0, stampId = "s4", x = 0.1, y = 0.1, scale = 1.0, rotationDegrees = 0.0, zIndex = 10)
        )

        val sorted = placements.sortedWith(
            compareBy<StampPlacement> { it.zIndex }.thenBy { it.id }
        )

        assertEquals(listOf("p_c", "p_a", "p_b", "p_d"), sorted.map { it.id })
    }

    @Test
    fun virtualAlbumDetection_identifiesLocPrefix() {
        assertTrue(AlbumLayoutRepository.isVirtualAlbum("loc_hanoi_old_quarter"))
        assertTrue(AlbumLayoutRepository.isVirtualAlbum("loc_saigon_post_office"))
        assertFalse(AlbumLayoutRepository.isVirtualAlbum("col_real_uuid_12345"))
        assertFalse(AlbumLayoutRepository.isVirtualAlbum("col_favorites"))
    }

    @Test
    fun unknownStampId_handledSafelyWithoutCrashing() {
        val albumId = "col_unknown_test"
        // Vault only contains s1
        val availableStamps = listOf(AlbumStampData("s1", "Valid Stamp", "url1"))
        // Placement references non-existent stamp s999 (e.g. deleted or foreign)
        val placements = listOf(
            StampPlacement(id = "p1", albumId = albumId, pageIndex = 0, stampId = "s1", x = 0.3, y = 0.3, scale = 1.0, rotationDegrees = 0.0, zIndex = 0),
            StampPlacement(id = "p2", albumId = albumId, pageIndex = 0, stampId = "s999_foreign", x = 0.7, y = 0.7, scale = 1.0, rotationDegrees = 0.0, zIndex = 1)
        )

        val spreads = calculateSpreads(albumId, availableStamps, placements)
        val rightPage = spreads[0].rightPage

        assertEquals(2, rightPage.placements.size)
        // Renderer resolves stamps from available list only
        val stampMap = availableStamps.associateBy { it.id }
        val resolvedStamps = rightPage.placements.mapNotNull { stampMap[it.stampId] }

        assertEquals(1, resolvedStamps.size)
        assertEquals("s1", resolvedStamps[0].id)
    }

    @Test
    fun emptyPlacements_fallsBackTo74DefaultGrid() {
        val albumId = "col_fallback_test"
        val stamps = (1..6).map { AlbumStampData("s$it", "Stamp $it", "path_$it") }

        // calculateSpreads with no placements
        val spreads = calculateSpreads(albumId = albumId, stamps = stamps, placements = emptyList())
        assertEquals(2, spreads.size)

        // Spread 0
        assertEquals(0, spreads[0].rightPage.pageIndex)
        assertEquals(4, spreads[0].rightPage.stamps.size)
        assertTrue(spreads[0].rightPage.placements.isEmpty())

        // Spread 1
        assertEquals(1, spreads[1].leftPage.pageIndex)
        assertEquals(2, spreads[1].leftPage.stamps.size)
        assertEquals(2, spreads[1].rightPage.pageIndex)
        assertTrue(spreads[1].rightPage.isBlankArchival)
    }
}
