package com.mipastudio.memostamp.core.notification

import java.util.LinkedHashMap

/**
 * Thread-safe, bounded in-memory event deduplication cache.
 * Synchronizes Realtime local notifications and FCM background push delivery
 * to guarantee that the user never receives duplicate notifications for the same event ID.
 */
object PushEventDeduper {

    private const val MAX_ENTRIES = 250
    private const val TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

    private val lock = Any()

    // LRU map storing eventId -> timestamp
    private val eventCache = object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    /**
     * Checks if the event should trigger a notification.
     * Returns true if the event has NOT been seen within TTL, and records it immediately.
     * Returns false if the event is a duplicate.
     */
    fun shouldNotify(eventId: String?): Boolean {
        if (eventId.isNullOrBlank()) return true
        val cleanId = eventId.trim()
        val now = System.currentTimeMillis()

        synchronized(lock) {
            val lastSeen = eventCache[cleanId]
            if (lastSeen != null && (now - lastSeen) < TTL_MILLIS) {
                return false
            }
            eventCache[cleanId] = now
            return true
        }
    }

    /**
     * Explicitly marks an event ID as handled without returning a decision.
     */
    fun recordEvent(eventId: String?) {
        if (eventId.isNullOrBlank()) return
        val cleanId = eventId.trim()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            eventCache[cleanId] = now
        }
    }

    /**
     * Checks whether an event was already recorded within the TTL window.
     */
    fun isDuplicate(eventId: String?): Boolean {
        if (eventId.isNullOrBlank()) return false
        val cleanId = eventId.trim()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val lastSeen = eventCache[cleanId] ?: return false
            return (now - lastSeen) < TTL_MILLIS
        }
    }

    /**
     * Clears in-memory deduplication cache. Useful on account logout or test teardown.
     */
    fun clear() {
        synchronized(lock) {
            eventCache.clear()
        }
    }
}
