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
    let mood: String
    let collectionId: String?
    let favorite: Bool
    let shape: String
}

struct PersistedCollectionData: Codable {
    let id: String
    let name: String
    let description: String
    let iconEmoji: String
    let createdAt: Int64
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

        // Restore Stamps if present
        if !payload.stamps.isEmpty {
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
                    shape: s.shape
                )
            }
            // Add/Sync stamps to repository
            for stamp in loadedStamps.reversed() {
                if repository.stamps.value.first(where: { $0.id == stamp.id }) == nil {
                    _ = repository.addStamp(
                        title: stamp.title,
                        note: stamp.note,
                        location: stamp.location,
                        imageUrl: stamp.stampImagePath,
                        shape: stamp.shape,
                        collectionId: stamp.collectionId,
                        audience: AudienceType.ONLY_ME,
                        mood: stamp.mood,
                        memoryDate: stamp.memoryDate
                    )
                }
            }
        }
    }

    func saveData(repository: SharedMemoStampRepository) {
        let currentUser = repository.currentUser.value
        let userData = PersistedUserData(
            uid: currentUser.uid,
            username: currentUser.username,
            displayName: currentUser.displayName,
            avatarUrl: currentUser.avatarUrl,
            bio: currentUser.bio,
            stampsCreatedCount: currentUser.stampsCreatedCount,
            stampsCollectedCount: currentUser.stampsCollectedCount,
            placesVisitedCount: currentUser.placesVisitedCount
        )

        let stampDatas = repository.stamps.value.map { s in
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
                shape: s.shape
            )
        }

        let collectionDatas = repository.collections.value.map { c in
            PersistedCollectionData(
                id: c.id,
                name: c.name,
                description: c.description_,
                iconEmoji: c.iconEmoji,
                createdAt: c.createdAt,
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
