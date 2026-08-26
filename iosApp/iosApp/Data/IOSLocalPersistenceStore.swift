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

struct PersistedPayload: Codable {
    let user: PersistedUserData?
    let stamps: [PersistedStampData]
    let collections: [PersistedCollectionData]
}

class IOSLocalPersistenceStore {
    static let shared = IOSLocalPersistenceStore()
    private let fileManager = FileManager.default

    private var storageUrl: URL {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("memostamp_local_v1.json")
    }

    func loadData(into repository: SharedMemoStampRepository) {
        guard fileManager.fileExists(atPath: storageUrl.path),
              let data = try? Data(contentsOf: storageUrl),
              let payload = try? JSONDecoder().decode(PersistedPayload.self, from: data) else {
            return
        }

        // Restore User Profile if present
        if let user = payload.user {
            let profile = UserProfile(
                uid: user.uid,
                username: user.username,
                displayName: user.displayName,
                avatarUrl: user.avatarUrl,
                bio: user.bio,
                stampsCreatedCount: user.stampsCreatedCount,
                stampsCollectedCount: user.stampsCollectedCount,
                placesVisitedCount: user.placesVisitedCount
            )
            repository.setCurrentUser(profile: profile)
        }

        // Unconditionally restore Collections (including empty list)
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

        // Unconditionally restore Stamps (including empty list)
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
    }

    func saveData(repository: SharedMemoStampRepository) {
        let userData: PersistedUserData?
        if let currentUser = repository.currentUser.value as? UserProfile {
            userData = PersistedUserData(
                uid: currentUser.uid,
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

        let payload = PersistedPayload(
            user: userData,
            stamps: stampDatas,
            collections: collectionDatas
        )

        if let encoded = try? JSONEncoder().encode(payload) {
            try? encoded.write(to: storageUrl, options: .atomic)
        }
    }
}
