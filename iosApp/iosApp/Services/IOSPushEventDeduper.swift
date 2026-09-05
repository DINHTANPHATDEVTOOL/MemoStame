import Foundation

/**
 * Thread-safe bounded in-memory event deduplication cache for iOS.
 * Synchronizes Supabase Realtime local notifications and APNs background push delivery
 * to guarantee that duplicate banners are suppressed when an event is received through multiple channels.
 */
final class IOSPushEventDeduper {

    static let shared = IOSPushEventDeduper()

    private let maxEntries = 250
    private let ttlSeconds: TimeInterval = 24 * 60 * 60 // 24 hours
    private let lock = NSLock()

    // Ordered list of keys for LRU pruning + timestamp dictionary
    private var eventTimestamps: [String: Date] = [:]
    private var eventOrder: [String] = []

    private init() {}

    /**
     * Returns true if event has not been seen within TTL, and records it immediately.
     * Returns false if event is a duplicate.
     */
    func shouldNotify(eventId: String?) -> Bool {
        guard let id = eventId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
            return true
        }

        lock.lock()
        defer { lock.unlock() }

        let now = Date()
        if let seenDate = eventTimestamps[id], now.timeIntervalSince(seenDate) < ttlSeconds {
            return false
        }

        recordEventInternal(id: id, now: now)
        return true
    }

    func recordEvent(eventId: String?) {
        guard let id = eventId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
            return
        }

        lock.lock()
        defer { lock.unlock() }

        recordEventInternal(id: id, now: Date())
    }

    func isDuplicate(eventId: String?) -> Bool {
        guard let id = eventId?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
            return false
        }

        lock.lock()
        defer { lock.unlock() }

        guard let seenDate = eventTimestamps[id] else { return false }
        return Date().timeIntervalSince(seenDate) < ttlSeconds
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }

        eventTimestamps.removeAll()
        eventOrder.removeAll()
    }

    private func recordEventInternal(id: String, now: Date) {
        eventTimestamps[id] = now
        eventOrder.removeAll { $0 == id }
        eventOrder.append(id)

        // Bounded capacity eviction
        while eventOrder.count > maxEntries {
            let oldest = eventOrder.removeFirst()
            eventTimestamps.removeValue(forKey: oldest)
        }
    }
}
