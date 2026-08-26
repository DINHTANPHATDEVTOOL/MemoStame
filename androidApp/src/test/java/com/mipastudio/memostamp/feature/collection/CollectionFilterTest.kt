package com.mipastudio.memostamp.feature.collection

import com.mipastudio.memostamp.data.local.StampEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionFilterTest {

    @Test
    fun filterStampsByCollection_returnsCorrectMatches() {
        val stamps = listOf(
            StampEntity(id = "1", ownerId = "user_test", originalImagePath = "", stampImagePath = "", title = "Beach", note = "", createdAt = 0, memoryDate = 0, collectionId = "nature"),
            StampEntity(id = "2", ownerId = "user_test", originalImagePath = "", stampImagePath = "", title = "Coffee", note = "", createdAt = 0, memoryDate = 0, collectionId = "travel"),
            StampEntity(id = "3", ownerId = "user_test", originalImagePath = "", stampImagePath = "", title = "Mountain", note = "", createdAt = 0, memoryDate = 0, collectionId = "nature")
        )

        val natureStamps = stamps.filter { it.collectionId == "nature" }
        assertEquals(2, natureStamps.size)
        assertEquals("Beach", natureStamps[0].title)
        assertEquals("Mountain", natureStamps[1].title)

        val travelStamps = stamps.filter { it.collectionId == "travel" }
        assertEquals(1, travelStamps.size)
        assertEquals("Coffee", travelStamps[0].title)
    }
}
