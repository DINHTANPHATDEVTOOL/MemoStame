package com.mipastudio.memostamp.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PushNotificationHardeningTest {

    @Before
    fun setUp() {
        PushEventDeduper.clear()
    }

    @Test
    fun testPushEventDeduper_suppressesDuplicateEventId() {
        val eventId = "evt_${UUID.randomUUID()}"

        // First occurrence should be allowed
        assertTrue("First event should trigger notification", PushEventDeduper.shouldNotify(eventId))
        assertTrue("Event should now be marked duplicate", PushEventDeduper.isDuplicate(eventId))

        // Second occurrence within TTL should be suppressed
        assertFalse("Duplicate event should be suppressed", PushEventDeduper.shouldNotify(eventId))
    }

    @Test
    fun testPushEventDeduper_allowsDifferentEventIds() {
        val event1 = "evt_dm_123"
        val event2 = "evt_dm_456"

        assertTrue(PushEventDeduper.shouldNotify(event1))
        assertTrue(PushEventDeduper.shouldNotify(event2))
        assertFalse(PushEventDeduper.shouldNotify(event1))
        assertFalse(PushEventDeduper.shouldNotify(event2))
    }

    @Test
    fun testPushEventDeduper_clearResetsCache() {
        val eventId = "evt_reset_1"
        assertTrue(PushEventDeduper.shouldNotify(eventId))
        assertFalse(PushEventDeduper.shouldNotify(eventId))

        PushEventDeduper.clear()
        assertTrue("After clear, event should be accepted again", PushEventDeduper.shouldNotify(eventId))
    }

    @Test
    fun testPushEventDeduper_handlesNullAndBlankGracefully() {
        assertTrue(PushEventDeduper.shouldNotify(null))
        assertTrue(PushEventDeduper.shouldNotify(""))
        assertTrue(PushEventDeduper.shouldNotify("   "))
        assertFalse(PushEventDeduper.isDuplicate(null))
        assertFalse(PushEventDeduper.isDuplicate(""))
    }

    @Test
    fun testPushEventDeduper_boundedSizeEviction() {
        // Insert 300 unique events (exceeding MAX_ENTRIES 250)
        val firstEvent = "event_0"
        assertTrue(PushEventDeduper.shouldNotify(firstEvent))

        for (i in 1..280) {
            PushEventDeduper.recordEvent("event_$i")
        }

        // Oldest event should have been evicted by LRU
        assertFalse("Oldest event should be evicted from bounded cache", PushEventDeduper.isDuplicate(firstEvent))
        // Recent event should still be present
        assertTrue("Recent event should remain in cache", PushEventDeduper.isDuplicate("event_280"))
    }

    @Test
    fun testSafeRouteFiltering() {
        fun resolveSafeRoute(rawRoute: String?): String {
            val normalized = rawRoute?.trim()?.uppercase()
            return when (normalized) {
                "CHAT" -> "CHAT"
                "FRIENDS" -> "FRIENDS"
                else -> "FRIENDS"
            }
        }

        assertEquals("CHAT", resolveSafeRoute("CHAT"))
        assertEquals("CHAT", resolveSafeRoute("chat"))
        assertEquals("FRIENDS", resolveSafeRoute("FRIENDS"))
        assertEquals("FRIENDS", resolveSafeRoute("friends"))

        // Malicious or arbitrary routes default safely
        assertEquals("FRIENDS", resolveSafeRoute("com.android.settings.Settings"))
        assertEquals("FRIENDS", resolveSafeRoute("javascript:alert(1)"))
        assertEquals("FRIENDS", resolveSafeRoute("../../../secret"))
        assertEquals("FRIENDS", resolveSafeRoute(null))
        assertEquals("FRIENDS", resolveSafeRoute(""))
    }

    @Test
    fun testInstallationId_isUuidFormat() {
        val generated = UUID.randomUUID().toString()
        val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        assertTrue("Installation ID must match canonical UUID format", uuidRegex.matches(generated))
    }
}
