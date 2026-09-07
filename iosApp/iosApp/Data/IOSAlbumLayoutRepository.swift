import Foundation
import Combine
import SwiftUI

struct SupabaseAlbumPageRecord: Codable {
    let id: String
    let owner_id: String
    let album_id: String
    let page_index: Int32
    let created_at: String?
    let updated_at: String?
}

struct SupabaseStampPlacementRecord: Codable {
    let id: String
    let owner_id: String
    let album_id: String
    let page_index: Int32
    let stamp_id: String
    let x: Double
    let y: Double
    let scale: Double
    let rotation_degrees: Double
    let z_index: Int32
    let created_at: String?
    let updated_at: String?
}

final class IOSAlbumLayoutRepository: ObservableObject {
    static let shared = IOSAlbumLayoutRepository()

    private let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"

    @Published var layoutPlacements: [String: [PersistedStampPlacementData]] = [:]
    @Published var layoutPages: [String: [PersistedAlbumPageData]] = [:]

    private(set) var activeUserId: String = ""
    private(set) var activeToken: String? = nil
    private var sessionGeneration: Int64 = 0

    private init() {
        if let session = SupabaseAuthService.shared.activeSession {
            self.activeUserId = session.userId
            self.activeToken = session.accessToken
        }
    }

    static func isVirtualAlbum(_ albumId: String) -> Bool {
        return albumId.hasPrefix("loc_")
    }

    static func isValidGeometry(x: Double, y: Double, scale: Double, rotationDegrees: Double, zIndex: Int32) -> Bool {
        guard !x.isNaN && !x.isInfinite && x >= 0.0 && x <= 1.0 else { return false }
        guard !y.isNaN && !y.isInfinite && y >= 0.0 && y <= 1.0 else { return false }
        guard !scale.isNaN && !scale.isInfinite && scale > 0.05 && scale <= 5.0 else { return false }
        guard !rotationDegrees.isNaN && !rotationDegrees.isInfinite && rotationDegrees >= -360.0 && rotationDegrees <= 360.0 else { return false }
        guard zIndex >= -1000 && zIndex <= 1000 else { return false }
        return true
    }

    func onSessionChanged(userId: String, accessToken: String?) {
        DispatchQueue.main.async {
            self.sessionGeneration += 1
            self.activeUserId = userId.trimmingCharacters(in: .whitespacesAndNewlines)
            self.activeToken = accessToken?.trimmingCharacters(in: .whitespacesAndNewlines)
            self.layoutPlacements.removeAll()
            self.layoutPages.removeAll()
        }
    }

    func loadLocalLayout(albumId: String) -> [PersistedStampPlacementData] {
        guard !activeUserId.isEmpty else { return [] }
        let localPlacements = IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)
        let localPages = IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)

        DispatchQueue.main.async {
            self.layoutPlacements[albumId] = localPlacements
            self.layoutPages[albumId] = localPages
        }
        return localPlacements
    }

    func syncLayout(albumId: String, completion: (([PersistedStampPlacementData]) -> Void)? = nil) {
        let cached = loadLocalLayout(albumId: albumId)

        // Virtual fallback albums must never sync or create permanent cloud records
        if Self.isVirtualAlbum(albumId) {
            completion?(cached)
            return
        }

        guard !activeUserId.isEmpty, let token = activeToken, !token.isEmpty else {
            completion?(cached)
            return
        }

        let captureSession = self.sessionGeneration
        let captureUserId = self.activeUserId

        // 1. Fetch pages
        fetchCloudPages(albumId: albumId, token: token) { [weak self] pageResult in
            guard let self = self else { return }
            guard self.sessionGeneration == captureSession && self.activeUserId == captureUserId else {
                print("Stale cloud page response discarded: session generation mismatch")
                return
            }

            let cloudPages = (try? pageResult.get()) ?? []

            // 2. Fetch placements
            self.fetchCloudPlacements(albumId: albumId, token: token) { [weak self] placementResult in
                guard let self = self else { return }
                guard self.sessionGeneration == captureSession && self.activeUserId == captureUserId else {
                    print("Stale cloud placement response discarded: session generation mismatch")
                    return
                }

                switch placementResult {
                case .success(let cloudPlacements):
                    // Geometry validation filter
                    let validPlacements = cloudPlacements.filter { p in
                        Self.isValidGeometry(x: p.x, y: p.y, scale: p.scale, rotationDegrees: p.rotationDegrees, zIndex: p.zIndex)
                    }

                    // Reconcile and save locally
                    if !cloudPages.isEmpty || !validPlacements.isEmpty {
                        IOSLocalPersistenceStore.shared.saveAlbumLayout(
                            userId: captureUserId,
                            albumId: albumId,
                            pages: cloudPages,
                            placements: validPlacements
                        )
                    }

                    DispatchQueue.main.async {
                        guard self.sessionGeneration == captureSession && self.activeUserId == captureUserId else { return }
                        if !cloudPages.isEmpty {
                            self.layoutPages[albumId] = cloudPages
                        }
                        if !validPlacements.isEmpty {
                            self.layoutPlacements[albumId] = validPlacements
                            completion?(validPlacements)
                        } else {
                            completion?(cached)
                        }
                    }
                case .failure:
                    DispatchQueue.main.async {
                        completion?(cached)
                    }
                }
            }
        }
    }

    private func fetchCloudPages(albumId: String, token: String, completion: @escaping (Result<[PersistedAlbumPageData], Error>) -> Void) {
        guard let encodedAlbumId = albumId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(supabaseUrl)/rest/v1/album_pages?album_id=eq.\(encodedAlbumId)&select=*&order=page_index.asc") else {
            completion(.failure(NSError(domain: "SupabaseAlbumLayout", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let data = data,
                  let records = try? JSONDecoder().decode([SupabaseAlbumPageRecord].self, from: data) else {
                completion(.failure(NSError(domain: "SupabaseAlbumLayout", code: 500, userInfo: [NSLocalizedDescriptionKey: "Parse error"])))
                return
            }

            let pages = records.map { r in
                PersistedAlbumPageData(
                    id: r.id,
                    albumId: r.album_id,
                    pageIndex: r.page_index,
                    createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                    updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
                )
            }
            completion(.success(pages))
        }.resume()
    }

    private func fetchCloudPlacements(albumId: String, token: String, completion: @escaping (Result<[PersistedStampPlacementData], Error>) -> Void) {
        guard let encodedAlbumId = albumId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(supabaseUrl)/rest/v1/album_stamp_placements?album_id=eq.\(encodedAlbumId)&select=*&order=page_index.asc,z_index.asc,id.asc") else {
            completion(.failure(NSError(domain: "SupabaseAlbumLayout", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let data = data,
                  let records = try? JSONDecoder().decode([SupabaseStampPlacementRecord].self, from: data) else {
                completion(.failure(NSError(domain: "SupabaseAlbumLayout", code: 500, userInfo: [NSLocalizedDescriptionKey: "Parse error"])))
                return
            }

            let placements = records.map { r in
                PersistedStampPlacementData(
                    id: r.id,
                    albumId: r.album_id,
                    pageIndex: r.page_index,
                    stampId: r.stamp_id,
                    x: r.x,
                    y: r.y,
                    scale: r.scale,
                    rotationDegrees: r.rotation_degrees,
                    zIndex: r.z_index,
                    createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                    updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
                )
            }.sorted {
                if $0.pageIndex != $1.pageIndex { return $0.pageIndex < $1.pageIndex }
                if $0.zIndex != $1.zIndex { return $0.zIndex < $1.zIndex }
                return $0.id < $1.id
            }
            completion(.success(placements))
        }.resume()
    }

    func upsertPage(albumId: String, pageIndex: Int32, completion: ((Result<PersistedAlbumPageData, Error>) -> Void)? = nil) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }
        guard pageIndex >= 0 else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Negative pageIndex rejected"])))
            return
        }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let pageId = UUID().uuidString.lowercased()
        let page = PersistedAlbumPageData(
            id: pageId,
            albumId: albumId,
            pageIndex: pageIndex,
            createdAt: now,
            updatedAt: now
        )

        var currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        if let idx = currentPages.firstIndex(where: { $0.pageIndex == pageIndex }) {
            currentPages[idx] = page
        } else {
            currentPages.append(page)
        }
        currentPages.sort { $0.pageIndex < $1.pageIndex }

        let currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)
        IOSLocalPersistenceStore.shared.saveAlbumLayout(userId: activeUserId, albumId: albumId, pages: currentPages, placements: currentPlacements)

        DispatchQueue.main.async {
            self.layoutPages[albumId] = currentPages
        }

        if Self.isVirtualAlbum(albumId) || activeToken == nil {
            completion?(.success(page))
            return
        }

        // Push to Supabase
        guard let token = activeToken,
              let url = URL(string: "\(supabaseUrl)/rest/v1/album_pages?on_conflict=owner_id,album_id,page_index") else {
            completion?(.success(page))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")

        let body: [String: Any] = [
            "id": pageId,
            "owner_id": activeUserId,
            "album_id": albumId,
            "page_index": pageIndex
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: [body])

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion?(.success(page))
        }.resume()
    }

    func upsertPlacement(
        albumId: String,
        pageIndex: Int32,
        stampId: String,
        x: Double,
        y: Double,
        scale: Double,
        rotationDegrees: Double,
        zIndex: Int32,
        completion: ((Result<PersistedStampPlacementData, Error>) -> Void)? = nil
    ) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }
        guard pageIndex >= 0 else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Negative pageIndex rejected"])))
            return
        }
        guard Self.isValidGeometry(x: x, y: y, scale: scale, rotationDegrees: rotationDegrees, zIndex: zIndex) else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid geometry"])))
            return
        }

        // Ensure page exists
        upsertPage(albumId: albumId, pageIndex: pageIndex)

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)

        let placementId: String
        if let existing = currentPlacements.first(where: { $0.stampId == stampId }) {
            placementId = existing.id
        } else {
            placementId = UUID().uuidString.lowercased()
        }

        let placement = PersistedStampPlacementData(
            id: placementId,
            albumId: albumId,
            pageIndex: pageIndex,
            stampId: stampId,
            x: x,
            y: y,
            scale: scale,
            rotationDegrees: rotationDegrees,
            zIndex: zIndex,
            createdAt: now,
            updatedAt: now
        )

        // Enforce 1 placement per stamp per album
        currentPlacements.removeAll { $0.stampId == stampId }
        currentPlacements.append(placement)
        currentPlacements.sort {
            if $0.pageIndex != $1.pageIndex { return $0.pageIndex < $1.pageIndex }
            if $0.zIndex != $1.zIndex { return $0.zIndex < $1.zIndex }
            return $0.id < $1.id
        }

        let currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        IOSLocalPersistenceStore.shared.saveAlbumLayout(userId: activeUserId, albumId: albumId, pages: currentPages, placements: currentPlacements)

        DispatchQueue.main.async {
            self.layoutPlacements[albumId] = currentPlacements
        }

        if Self.isVirtualAlbum(albumId) || activeToken == nil {
            completion?(.success(placement))
            return
        }

        // Push to Supabase
        guard let token = activeToken,
              let url = URL(string: "\(supabaseUrl)/rest/v1/album_stamp_placements?on_conflict=owner_id,album_id,stamp_id") else {
            completion?(.success(placement))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")

        let body: [String: Any] = [
            "id": placementId,
            "owner_id": activeUserId,
            "album_id": albumId,
            "page_index": pageIndex,
            "stamp_id": stampId,
            "x": x,
            "y": y,
            "scale": scale,
            "rotation_degrees": rotationDegrees,
            "z_index": zIndex
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: [body])

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion?(.success(placement))
        }.resume()
    }

    func updatePlacementTransform(
        placementId: String,
        albumId: String,
        x: Double,
        y: Double,
        scale: Double,
        rotationDegrees: Double,
        zIndex: Int32,
        completion: ((Result<PersistedStampPlacementData, Error>) -> Void)? = nil
    ) {
        guard let current = (layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)).first(where: { $0.id == placementId }) else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Placement not found"])))
            return
        }
        upsertPlacement(
            albumId: albumId,
            pageIndex: current.pageIndex,
            stampId: current.stampId,
            x: x,
            y: y,
            scale: scale,
            rotationDegrees: rotationDegrees,
            zIndex: zIndex,
            completion: completion
        )
    }

    func movePlacementToPage(
        placementId: String,
        albumId: String,
        newPageIndex: Int32,
        x: Double? = nil,
        y: Double? = nil,
        completion: ((Result<PersistedStampPlacementData, Error>) -> Void)? = nil
    ) {
        guard let current = (layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)).first(where: { $0.id == placementId }) else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Placement not found"])))
            return
        }
        upsertPlacement(
            albumId: albumId,
            pageIndex: newPageIndex,
            stampId: current.stampId,
            x: x ?? current.x,
            y: y ?? current.y,
            scale: current.scale,
            rotationDegrees: current.rotationDegrees,
            zIndex: current.zIndex,
            completion: completion
        )
    }

    func deletePlacement(placementId: String, albumId: String, completion: ((Result<Bool, Error>) -> Void)? = nil) {
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }

        IOSLocalPersistenceStore.shared.deletePlacement(userId: activeUserId, placementId: placementId)
        DispatchQueue.main.async {
            self.layoutPlacements[albumId]?.removeAll { $0.id == placementId }
        }

        if Self.isVirtualAlbum(albumId) || activeToken == nil {
            completion?(.success(true))
            return
        }

        guard let token = activeToken,
              let encodedId = placementId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(supabaseUrl)/rest/v1/album_stamp_placements?id=eq.\(encodedId)") else {
            completion?(.success(true))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion?(.success(true))
        }.resume()
    }
}
