import Foundation
import SwiftUI
import shared

struct PersistedStampData: Codable {
    let id: String
    let originalImagePath: String
    let stampImagePath: String
    let title: String
    let note: String
    let createdAt: Int64
    let memoryDate: Int64
    let location: String?
    let mood: String?
    let collectionId: String?
    let favorite: Bool
    let filterId: String?
    let shape: String
    let preset: String?
}

struct PersistedCollectionData: Codable {
    let id: String
    let name: String
    let description: String?
    let iconEmoji: String
    let collectionType: String
    let targetCount: Int32
    let stampsCount: Int32
    let privacy: String
}

struct PersistedUserData: Codable {
    let uid: String
    let username: String
    let displayName: String
    let avatarUrl: String?
    let bio: String
    let stampsCreatedCount: Int32
    let stampsCollectedCount: Int32
    let placesVisitedCount: Int32
}

struct PersistedFriendData: Codable {
    let id: String
    let displayName: String
    let username: String
    let avatarUrl: String
    let isOnline: Bool
    let tradeCount: Int32
}

struct PersistedFriendRequestData: Codable {
    let id: String
    let senderName: String
    let senderUsername: String
    let senderAvatar: String
    let status: String
    let createdAt: Int64
    let senderId: String?
    let recipientId: String?
    let recipientUsername: String?
}

struct PersistedTradeRequestData: Codable {
    let id: String
    let senderName: String
    let senderAvatar: String
    let stampTitle: String
    let stampUrl: String
    let status: String
    let createdAt: Int64
    let senderId: String?
    let recipientId: String?
    let recipientName: String?
    let stampId: String?
}

struct PersistedPayload: Codable {
    let user: PersistedUserData?
    let stamps: [PersistedStampData]
    let collections: [PersistedCollectionData]
    let friends: [PersistedFriendData]?
    let friendRequests: [PersistedFriendRequestData]?
    let tradeRequests: [PersistedTradeRequestData]?
}

class IOSLocalPersistenceStore {
    static let shared = IOSLocalPersistenceStore()
    private let fileManager = FileManager.default

    func sanitizeUserId(_ userId: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_-"))
        let clean = userId.lowercased().components(separatedBy: allowed.inverted).joined(separator: "_")
        var hash: UInt32 = 2166136261
        for byte in userId.utf8 {
            hash = (hash ^ UInt32(byte)).multipliedReportingOverflow(by: 16777619).partialValue
        }
        let hexHash = String(format: "%08x", hash)
        let base = clean.isEmpty ? "user" : clean
        return "\(base)_\(hexHash)"
    }

    func storageUrl(userId: String) -> URL {
        let safeUid = sanitizeUserId(userId)
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("memostamp_local_v2_\(safeUid).json")
    }

    private var legacyV1Url: URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("memostamp_local_v1.json")
    }

    func isValidAuthenticatedUserId(_ userId: String?) -> Bool {
        guard let userId = userId?.trimmingCharacters(in: .whitespacesAndNewlines), !userId.isEmpty else {
            return false
        }
        let lower = userId.lowercased()
        if lower == "user_me" || lower == "guest" || lower.hasPrefix("guest_") {
            return false
        }
        return true
    }

    @discardableResult
    func loadData(into repository: SharedMemoStampRepository, userId: String) -> Bool {
        guard isValidAuthenticatedUserId(userId) else { return false }
        let targetUrl = storageUrl(userId: userId)

        // 1. Check account-scoped V2 file
        if fileManager.fileExists(atPath: targetUrl.path),
           let data = try? Data(contentsOf: targetUrl),
           let payload = try? JSONDecoder().decode(PersistedPayload.self, from: data) {

            // Guard: Never let a persistence file silently switch identity
            if let user = payload.user, !user.uid.isEmpty && user.uid != userId {
                print("Persistence identity mismatch: file uid '\(user.uid)' != requested userId '\(userId)'")
                return false
            }

            restorePayload(payload, into: repository, userId: userId)
            return true
        }

        // 2. Legacy V1 Migration fallback
        if fileManager.fileExists(atPath: legacyV1Url.path),
           let legacyData = try? Data(contentsOf: legacyV1Url),
           let legacyPayload = try? JSONDecoder().decode(PersistedPayload.self, from: legacyData) {

            let legacyUid = legacyPayload.user?.uid ?? ""
            if !legacyUid.isEmpty && legacyUid == userId {
                restorePayload(legacyPayload, into: repository, userId: userId)
                // Write immediately to v2 file for this user
                saveData(repository: repository, userId: userId)
                // Remove old v1 file after successful migration
                try? fileManager.removeItem(at: legacyV1Url)
                return true
            } else {
                print("Legacy V1 identity mismatch: payload user '\(legacyUid)' != requested userId '\(userId)'")
                return false
            }
        }

        return false
    }

    private func restorePayload(_ payload: PersistedPayload, into repository: SharedMemoStampRepository, userId: String) {
        if let user = payload.user {
            let current = (repository.currentUser.value as? UserProfile)
            let profile = UserProfile(
                uid: userId,
                username: current?.username ?? user.username,
                displayName: current?.displayName ?? user.displayName,
                avatarUrl: current?.avatarUrl ?? user.avatarUrl,
                bio: current?.bio ?? user.bio,
                stampsCreatedCount: max(current?.stampsCreatedCount ?? 0, user.stampsCreatedCount),
                stampsCollectedCount: max(current?.stampsCollectedCount ?? 0, user.stampsCollectedCount),
                placesVisitedCount: max(current?.placesVisitedCount ?? 0, user.placesVisitedCount)
            )
            repository.setCurrentUser(profile: profile)
        }

        // Restore Collections
        let loadedCollections = payload.collections.map { c in
            CollectionItem(
                id: c.id,
                name: c.name,
                description: c.description,
                iconEmoji: c.iconEmoji,
                collectionType: c.collectionType,
                targetCount: c.targetCount,
                stampsCount: c.stampsCount,
                privacy: c.privacy
            )
        }
        repository.restoreCollections(collections: loadedCollections)

        // Restore Stamps
        let loadedStamps = payload.stamps.map { s in
            StampItem(
                id: s.id,
                originalImagePath: s.originalImagePath,
                stampImagePath: s.stampImagePath,
                title: s.title,
                note: s.note,
                createdAt: s.createdAt,
                memoryDate: s.memoryDate,
                location: s.location,
                mood: s.mood,
                collectionId: s.collectionId,
                favorite: s.favorite,
                filterId: s.filterId,
                shape: s.shape,
                preset: s.preset ?? "NATURAL"
            )
        }
        repository.restoreStamps(stamps: loadedStamps)

        // Restore Friends
        if let friends = payload.friends {
            let loadedFriends = friends.map { f in
                FriendItem(
                    id: f.id,
                    displayName: f.displayName,
                    username: f.username,
                    avatarUrl: f.avatarUrl,
                    isOnline: f.isOnline,
                    tradeCount: f.tradeCount
                )
            }
            repository.restoreFriends(friends: loadedFriends)
        } else {
            repository.restoreFriends(friends: [])
        }

        // Restore Friend Requests
        if let requests = payload.friendRequests {
            let loadedReqs = requests.map { r in
                FriendRequestItem(
                    id: r.id,
                    senderName: r.senderName,
                    senderUsername: r.senderUsername,
                    senderAvatar: r.senderAvatar,
                    status: r.status,
                    createdAt: r.createdAt,
                    senderId: r.senderId ?? "",
                    recipientId: r.recipientId ?? "",
                    recipientUsername: r.recipientUsername ?? ""
                )
            }
            repository.restoreFriendRequests(requests: loadedReqs)
        } else {
            repository.restoreFriendRequests(requests: [])
        }

        // Restore Trade Requests
        if let trades = payload.tradeRequests {
            let loadedTrades = trades.map { t in
                TradeRequest(
                    id: t.id,
                    senderName: t.senderName,
                    senderAvatar: t.senderAvatar,
                    stampTitle: t.stampTitle,
                    stampUrl: t.stampUrl,
                    status: t.status,
                    createdAt: t.createdAt,
                    senderId: t.senderId ?? "",
                    recipientId: t.recipientId ?? "",
                    recipientName: t.recipientName ?? "",
                    stampId: t.stampId ?? ""
                )
            }
            repository.restoreTradeRequests(trades: loadedTrades)
        } else {
            repository.restoreTradeRequests(trades: [])
        }
    }

    @discardableResult
    func saveData(repository: SharedMemoStampRepository, userId: String) -> Bool {
        guard isValidAuthenticatedUserId(userId) else { return false }
        let targetUrl = storageUrl(userId: userId)
        
        let userData: PersistedUserData?
        if let currentUser = repository.currentUser.value as? UserProfile {
            userData = PersistedUserData(
                uid: userId,
                username: currentUser.username,
                displayName: currentUser.displayName,
                avatarUrl: currentUser.avatarUrl,
                bio: currentUser.bio,
                stampsCreatedCount: currentUser.stampsCreatedCount,
                stampsCollectedCount: currentUser.stampsCollectedCount,
                placesVisitedCount: currentUser.placesVisitedCount
            )
        } else {
            userData = nil
        }

        let rawStamps = (repository.stamps.value as? [StampItem]) ?? []
        let stampDatas = rawStamps.map { (s: StampItem) in
            PersistedStampData(
                id: s.id,
                originalImagePath: s.originalImagePath,
                stampImagePath: s.stampImagePath,
                title: s.title,
                note: s.note,
                createdAt: s.createdAt,
                memoryDate: s.memoryDate,
                location: s.location,
                mood: s.mood,
                collectionId: s.collectionId,
                favorite: s.favorite,
                filterId: s.filterId,
                shape: s.shape,
                preset: s.preset
            )
        }

        let rawCollections = (repository.collections.value as? [CollectionItem]) ?? []
        let collectionDatas = rawCollections.map { (c: CollectionItem) in
            PersistedCollectionData(
                id: c.id,
                name: c.name,
                description: c.description_,
                iconEmoji: c.iconEmoji,
                collectionType: c.collectionType,
                targetCount: c.targetCount,
                stampsCount: c.stampsCount,
                privacy: c.privacy
            )
        }

        let rawFriends = (repository.friends.value as? [FriendItem]) ?? []
        let friendDatas = rawFriends.map { (f: FriendItem) in
            PersistedFriendData(
                id: f.id,
                displayName: f.displayName,
                username: f.username,
                avatarUrl: f.avatarUrl,
                isOnline: f.isOnline,
                tradeCount: f.tradeCount
            )
        }

        let rawFriendRequests = (repository.friendRequests.value as? [FriendRequestItem]) ?? []
        let friendRequestDatas = rawFriendRequests.map { (r: FriendRequestItem) in
            PersistedFriendRequestData(
                id: r.id,
                senderName: r.senderName,
                senderUsername: r.senderUsername,
                senderAvatar: r.senderAvatar,
                status: r.status,
                createdAt: r.createdAt,
                senderId: r.senderId,
                recipientId: r.recipientId,
                recipientUsername: r.recipientUsername
            )
        }

        let rawTradeRequests = (repository.tradeRequests.value as? [TradeRequest]) ?? []
        let tradeRequestDatas = rawTradeRequests.map { (t: TradeRequest) in
            PersistedTradeRequestData(
                id: t.id,
                senderName: t.senderName,
                senderAvatar: t.senderAvatar,
                stampTitle: t.stampTitle,
                stampUrl: t.stampUrl,
                status: t.status,
                createdAt: t.createdAt,
                senderId: t.senderId,
                recipientId: t.recipientId,
                recipientName: t.recipientName,
                stampId: t.stampId
            )
        }

        let payload = PersistedPayload(
            user: userData,
            stamps: stampDatas,
            collections: collectionDatas,
            friends: friendDatas,
            friendRequests: friendRequestDatas,
            tradeRequests: tradeRequestDatas
        )

        if let encoded = try? JSONEncoder().encode(payload) {
            do {
                try encoded.write(to: targetUrl, options: .atomic)
                return true
            } catch {
                return false
            }
        }
        return false
    }
}
