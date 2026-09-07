package com.mipastudio.memostamp.domain.model

import com.mipastudio.memostamp.data.local.CollectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSystemMigrationTest {

    @Test
    fun testSemanticIconRegistryKnownKeys() {
        assertTrue(MemoStampIconKey.isKnown("location"))
        assertTrue(MemoStampIconKey.isKnown("LOCATION"))
        assertTrue(MemoStampIconKey.isKnown("stamp"))
        assertTrue(MemoStampIconKey.isKnown("postmark"))
        assertTrue(MemoStampIconKey.isKnown("collection"))
        assertTrue(MemoStampIconKey.isKnown("travel"))
        assertTrue(MemoStampIconKey.isKnown("happy"))
        assertTrue(MemoStampIconKey.isKnown("special"))
    }

    @Test
    fun testLegacyCollectionEmojiMigration() {
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon("📁"))
        assertEquals("travel", MemoStampLegacyMigration.mapLegacyCollectionIcon("✈️"))
        assertEquals("travel", MemoStampLegacyMigration.mapLegacyCollectionIcon("✈"))
        assertEquals("cafe", MemoStampLegacyMigration.mapLegacyCollectionIcon("☕"))
        assertEquals("beach", MemoStampLegacyMigration.mapLegacyCollectionIcon("🏖️"))
        assertEquals("beach", MemoStampLegacyMigration.mapLegacyCollectionIcon("🏖"))
        assertEquals("education", MemoStampLegacyMigration.mapLegacyCollectionIcon("🎓"))
        assertEquals("stamp", MemoStampLegacyMigration.mapLegacyCollectionIcon("📮"))
        assertEquals("nature", MemoStampLegacyMigration.mapLegacyCollectionIcon("🏞️"))
        assertEquals("camera", MemoStampLegacyMigration.mapLegacyCollectionIcon("📸"))
        assertEquals("camera", MemoStampLegacyMigration.mapLegacyCollectionIcon("📷"))
        assertEquals("heart", MemoStampLegacyMigration.mapLegacyCollectionIcon("💖"))
        assertEquals("heart", MemoStampLegacyMigration.mapLegacyCollectionIcon("♡"))
        assertEquals("heart", MemoStampLegacyMigration.mapLegacyCollectionIcon("❤️"))
        assertEquals("nature", MemoStampLegacyMigration.mapLegacyCollectionIcon("🌲"))
        assertEquals("art", MemoStampLegacyMigration.mapLegacyCollectionIcon("🎨"))
        assertEquals("special", MemoStampLegacyMigration.mapLegacyCollectionIcon("👑"))
        assertEquals("flower", MemoStampLegacyMigration.mapLegacyCollectionIcon("🌸"))
        assertEquals("flower", MemoStampLegacyMigration.mapLegacyCollectionIcon("✿"))
        assertEquals("food", MemoStampLegacyMigration.mapLegacyCollectionIcon("🍔"))
        assertEquals("lifestyle", MemoStampLegacyMigration.mapLegacyCollectionIcon("🌿"))
        assertEquals("celebration", MemoStampLegacyMigration.mapLegacyCollectionIcon("🎉"))
    }

    @Test
    fun testUnknownLegacyCollectionEmojiHasSafeFallback() {
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon("👽"))
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon("🎲"))
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon(null))
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon(""))
        assertEquals("collection", MemoStampLegacyMigration.mapLegacyCollectionIcon("   "))
    }

    @Test
    fun testCollectionEntityDefaultsPreserveIconKey() {
        val legacyCol = CollectionEntity(
            id = "col_1",
            ownerId = "u1",
            name = "My Trips",
            description = "Travel memories",
            iconEmoji = "✈️"
        )
        assertEquals("travel", legacyCol.resolvedIconKey())

        val defaultCol = CollectionEntity(
            id = "col_2",
            ownerId = "u1",
            name = "Default",
            description = null
        )
        assertEquals("collection", defaultCol.resolvedIconKey())

        val newWriteCol = CollectionEntity(
            id = "col_3",
            ownerId = "u1",
            name = "Coffee time",
            description = "Cafe stamps",
            iconEmoji = "cafe",
            iconKey = "cafe"
        )
        assertEquals("cafe", newWriteCol.resolvedIconKey())
    }

    @Test
    fun testLegacyMoodMigration() {
        assertEquals("happy", MemoStampLegacyMigration.mapLegacyMood("😊"))
        assertEquals("happy", MemoStampLegacyMigration.mapLegacyMood("😊 Happy"))
        assertEquals("happy", MemoStampLegacyMigration.mapLegacyMood("HAPPY"))

        assertEquals("love", MemoStampLegacyMigration.mapLegacyMood("❤️"))
        assertEquals("love", MemoStampLegacyMigration.mapLegacyMood("❤️ Love"))

        assertEquals("travel", MemoStampLegacyMigration.mapLegacyMood("✈️"))
        assertEquals("travel", MemoStampLegacyMigration.mapLegacyMood("✈️ Travel"))

        assertEquals("chill", MemoStampLegacyMigration.mapLegacyMood("☕"))
        assertEquals("chill", MemoStampLegacyMigration.mapLegacyMood("☕ Chill"))

        assertEquals("excited", MemoStampLegacyMigration.mapLegacyMood("🔥"))
        assertEquals("excited", MemoStampLegacyMigration.mapLegacyMood("🔥 Excited"))

        assertEquals("nostalgic", MemoStampLegacyMigration.mapLegacyMood("📜"))
        assertEquals("nostalgic", MemoStampLegacyMigration.mapLegacyMood("🕰️ Nostalgic"))

        assertEquals("peaceful", MemoStampLegacyMigration.mapLegacyMood("🌿"))
        assertEquals("peaceful", MemoStampLegacyMigration.mapLegacyMood("🌿 Peaceful"))

        assertEquals("special", MemoStampLegacyMigration.mapLegacyMood("✨"))
        assertEquals("special", MemoStampLegacyMigration.mapLegacyMood("⭐ Special"))
        assertEquals("special", MemoStampLegacyMigration.mapLegacyMood(null))
        assertEquals("special", MemoStampLegacyMigration.mapLegacyMood(""))
        assertEquals("special", MemoStampLegacyMigration.mapLegacyMood("unknown_random"))
    }

    @Test
    fun testLegacyNotificationMigration() {
        assertEquals("chat", MemoStampLegacyMigration.mapLegacyNotification("💬"))
        assertEquals("friends", MemoStampLegacyMigration.mapLegacyNotification("🤝"))
        assertEquals("stamp", MemoStampLegacyMigration.mapLegacyNotification("📮"))
        assertEquals("success", MemoStampLegacyMigration.mapLegacyNotification("🎉"))
        assertEquals("warning", MemoStampLegacyMigration.mapLegacyNotification("⚠️"))
        assertEquals("notification", MemoStampLegacyMigration.mapLegacyNotification(null))
    }

    @Test
    fun testStampTemplateSemanticIcons() {
        assertEquals("stamp", StampTemplates.PHOTO_STAMP.iconKey)
        assertEquals("travel", StampTemplates.AIRMAIL.iconKey)
        assertEquals("camera", StampTemplates.POLAROID.iconKey)
        assertEquals("postmark", StampTemplates.VINTAGE.iconKey)
        assertEquals("passport", StampTemplates.PASSPORT.iconKey)
        assertEquals("flower", StampTemplates.SAKURA.iconKey)
    }
}
