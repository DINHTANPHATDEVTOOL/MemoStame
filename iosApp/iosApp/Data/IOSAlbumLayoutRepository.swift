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
    let page_id: String?
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
                    pageId: r.page_id,
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
        pageId: String? = nil,
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
        var currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        var resolvedPageId = pageId
        if resolvedPageId == nil || resolvedPageId?.isEmpty == true {
            resolvedPageId = currentPages.first(where: { $0.pageIndex == pageIndex })?.id
        }
        if resolvedPageId == nil {
            upsertPage(albumId: albumId, pageIndex: pageIndex)
            currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
            resolvedPageId = currentPages.first(where: { $0.pageIndex == pageIndex })?.id
        }

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
            pageId: resolvedPageId,
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

        var body: [String: Any] = [
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
        if let pid = resolvedPageId, !pid.isEmpty {
            body["page_id"] = pid
        }
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
        newPageId: String? = nil,
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
            pageId: newPageId,
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

    func ensurePageStructure(albumId: String, completion: (() -> Void)? = nil) {
        if Self.isVirtualAlbum(albumId) {
            completion?()
            return
        }
        guard !activeUserId.isEmpty else {
            completion?()
            return
        }

        let currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        let currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)

        if currentPages.isEmpty {
            let maxPage = currentPlacements.map { $0.pageIndex }.max() ?? 0
            let requiredCount = max(1, Int(maxPage) + 1)
            for i in 0..<requiredCount {
                upsertPage(albumId: albumId, pageIndex: Int32(i))
            }
        }
        completion?()
    }

    func appendPage(albumId: String, completion: ((Result<PersistedAlbumPageData, Error>) -> Void)? = nil) {
        if Self.isVirtualAlbum(albumId) {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Synthetic albums are read-only"])))
            return
        }
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }

        var currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        guard currentPages.count < 50 else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Maximum pages reached"])))
            return
        }

        let maxIdx = currentPages.map { $0.pageIndex }.max() ?? -1
        let nextIndex = maxIdx + 1
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let pageId = UUID().uuidString.lowercased()
        let page = PersistedAlbumPageData(id: pageId, albumId: albumId, pageIndex: nextIndex, createdAt: now, updatedAt: now)

        currentPages.append(page)
        currentPages.sort { $0.pageIndex < $1.pageIndex }

        let currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)
        IOSLocalPersistenceStore.shared.saveAlbumLayout(userId: activeUserId, albumId: albumId, pages: currentPages, placements: currentPlacements)

        DispatchQueue.main.async {
            self.layoutPages[albumId] = currentPages
        }

        guard let token = activeToken, let url = URL(string: "\(supabaseUrl)/rest/v1/rpc/append_album_page") else {
            completion?(.success(page))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body = ["p_album_id": albumId]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let data = data,
               let cloudRecord = try? JSONDecoder().decode(SupabaseAlbumPageRecord.self, from: data) {
                let cloudPage = PersistedAlbumPageData(
                    id: cloudRecord.id,
                    albumId: cloudRecord.album_id,
                    pageIndex: cloudRecord.page_index,
                    createdAt: now,
                    updatedAt: now
                )
                DispatchQueue.main.async {
                    if let idx = self.layoutPages[albumId]?.firstIndex(where: { $0.id == pageId }) {
                        self.layoutPages[albumId]?[idx] = cloudPage
                    }
                    completion?(.success(cloudPage))
                }
            } else {
                completion?(.success(page))
            }
        }.resume()
    }

    func removePage(albumId: String, pageId: String, completion: ((Result<Void, Error>) -> Void)? = nil) {
        if Self.isVirtualAlbum(albumId) {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Synthetic albums are read-only"])))
            return
        }
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }

        var currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        guard currentPages.count > 1 else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Cannot remove the only page"])))
            return
        }

        guard let targetIndex = currentPages.firstIndex(where: { $0.id == pageId }) else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "Page not found"])))
            return
        }

        let pageToRemove = currentPages[targetIndex]
        var currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)
        let hasStamps = currentPlacements.contains { p in
            (p.pageId != nil && p.pageId == pageId) || p.pageIndex == pageToRemove.pageIndex
        }
        if hasStamps {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Move stamps before removing this page"])))
            return
        }

        // Local deletion & contiguous reindexing
        currentPages.remove(at: targetIndex)
        var updatedPages: [PersistedAlbumPageData] = []
        for (i, p) in currentPages.enumerated() {
            updatedPages.append(PersistedAlbumPageData(
                id: p.id,
                albumId: p.albumId,
                pageIndex: Int32(i),
                createdAt: p.createdAt,
                updatedAt: p.updatedAt
            ))
        }

        // Also shift placements whose pageIndex was greater than removed page
        var updatedPlacements: [PersistedStampPlacementData] = []
        for p in currentPlacements {
            if p.pageIndex > pageToRemove.pageIndex {
                updatedPlacements.append(PersistedStampPlacementData(
                    id: p.id,
                    albumId: p.albumId,
                    pageIndex: p.pageIndex - 1,
                    pageId: p.pageId,
                    stampId: p.stampId,
                    x: p.x,
                    y: p.y,
                    scale: p.scale,
                    rotationDegrees: p.rotationDegrees,
                    zIndex: p.zIndex,
                    createdAt: p.createdAt,
                    updatedAt: p.updatedAt
                ))
            } else {
                updatedPlacements.append(p)
            }
        }

        IOSLocalPersistenceStore.shared.saveAlbumLayout(userId: activeUserId, albumId: albumId, pages: updatedPages, placements: updatedPlacements)
        DispatchQueue.main.async {
            self.layoutPages[albumId] = updatedPages
            self.layoutPlacements[albumId] = updatedPlacements
        }

        guard let token = activeToken, let url = URL(string: "\(supabaseUrl)/rest/v1/rpc/remove_album_page") else {
            completion?(.success(()))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = ["p_album_id": albumId, "p_page_id": pageId]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion?(.success(()))
        }.resume()
    }

    func reorderPages(albumId: String, pageIds: [String], completion: ((Result<Void, Error>) -> Void)? = nil) {
        if Self.isVirtualAlbum(albumId) {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "Synthetic albums are read-only"])))
            return
        }
        guard !activeUserId.isEmpty else {
            completion?(.failure(NSError(domain: "IOSAlbumLayoutRepository", code: 401, userInfo: [NSLocalizedDescriptionKey: "Unauthenticated"])))
            return
        }

        let currentPages = layoutPages[albumId] ?? IOSLocalPersistenceStore.shared.getAlbumPages(userId: activeUserId, albumId: albumId)
        var pageMap: [String: PersistedAlbumPageData] = [:]
        for p in currentPages {
            pageMap[p.id] = p
        }

        var reorderedPages: [PersistedAlbumPageData] = []
        var newIndexMap: [String: Int32] = [:]
        for (i, pid) in pageIds.enumerated() {
            if let p = pageMap[pid] {
                reorderedPages.append(PersistedAlbumPageData(
                    id: p.id,
                    albumId: p.albumId,
                    pageIndex: Int32(i),
                    createdAt: p.createdAt,
                    updatedAt: p.updatedAt
                ))
                newIndexMap[p.id] = Int32(i)
            }
        }

        // Placements stay with their page!
        let currentPlacements = layoutPlacements[albumId] ?? IOSLocalPersistenceStore.shared.getStampPlacements(userId: activeUserId, albumId: albumId)
        var updatedPlacements: [PersistedStampPlacementData] = []
        for p in currentPlacements {
            let matchingPageId = p.pageId ?? currentPages.first(where: { $0.pageIndex == p.pageIndex })?.id
            let newPageIndex = (matchingPageId != nil ? newIndexMap[matchingPageId!] : nil) ?? p.pageIndex
            updatedPlacements.append(PersistedStampPlacementData(
                id: p.id,
                albumId: p.albumId,
                pageIndex: newPageIndex,
                pageId: matchingPageId ?? p.pageId,
                stampId: p.stampId,
                x: p.x,
                y: p.y,
                scale: p.scale,
                rotationDegrees: p.rotationDegrees,
                zIndex: p.zIndex,
                createdAt: p.createdAt,
                updatedAt: p.updatedAt
            ))
        }

        IOSLocalPersistenceStore.shared.saveAlbumLayout(userId: activeUserId, albumId: albumId, pages: reorderedPages, placements: updatedPlacements)
        DispatchQueue.main.async {
            self.layoutPages[albumId] = reorderedPages
            self.layoutPlacements[albumId] = updatedPlacements
        }

        guard let token = activeToken, let url = URL(string: "\(supabaseUrl)/rest/v1/rpc/reorder_album_pages") else {
            completion?(.success(()))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = ["p_album_id": albumId, "p_page_ids": pageIds]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { _, _, _ in
            completion?(.success(()))
        }.resume()
    }
}
