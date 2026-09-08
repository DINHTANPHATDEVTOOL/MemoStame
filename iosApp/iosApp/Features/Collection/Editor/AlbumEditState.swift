import Foundation
import SwiftUI
import Combine

public enum AlbumEditMode: Equatable {
    case view
    case edit
}

public struct TransientPlacementTransformData: Equatable {
    public var x: Double
    public var y: Double
    public var scale: Double
    public var rotationDegrees: Double
    public var zIndex: Int32

    public init(x: Double, y: Double, scale: Double, rotationDegrees: Double, zIndex: Int32) {
        self.x = x
        self.y = y
        self.scale = scale
        self.rotationDegrees = rotationDegrees
        self.zIndex = zIndex
    }
}

public enum AlbumEditorAction {
    case add(PersistedStampPlacementData)
    case transform(
        placementId: String,
        oldX: Double,
        oldY: Double,
        oldScale: Double,
        oldRotation: Double,
        oldZIndex: Int32,
        newX: Double,
        newY: Double,
        newScale: Double,
        newRotation: Double,
        newZIndex: Int32
    )
    case zOrder(placementId: String, oldZIndex: Int32, newZIndex: Int32)
    case movePage(placementId: String, oldPageIndex: Int32, newPageIndex: Int32)
    case delete(PersistedStampPlacementData)
}

public final class AlbumEditState: ObservableObject {
    public let albumId: String
    public let isVirtualAlbum: Bool

    @Published public var mode: AlbumEditMode = .view
    @Published public var selectedPlacementId: String? = nil
    @Published public var activePageIndex: Int32 = 0
    @Published public var isVaultPickerOpen: Bool = false
    @Published public var isMovePageMenuOpen: Bool = false
    @Published public var isPageManagementOpen: Bool = false
    @Published public var isSaving: Bool = false
    @Published public var saveErrorMessage: String? = nil

    @Published public var transientTransforms: [String: TransientPlacementTransformData] = [:]
    public var undoStack: [AlbumEditorAction] = []

    public static let minScale: Double = 0.35
    public static let maxScale: Double = 3.0
    public static let minCoord: Double = 0.08
    public static let maxCoord: Double = 0.92
    public static let minZIndex: Int32 = 1
    public static let maxZIndex: Int32 = 999

    public init(albumId: String) {
        self.albumId = albumId
        self.isVirtualAlbum = IOSAlbumLayoutRepository.isVirtualAlbum(albumId)
    }

    public static func clampX(_ x: Double) -> Double {
        return min(max(x, minCoord), maxCoord)
    }

    public static func clampY(_ y: Double) -> Double {
        return min(max(y, minCoord), maxCoord)
    }

    public static func clampScale(_ scale: Double) -> Double {
        return min(max(scale, minScale), maxScale)
    }

    public static func clampRotation(_ deg: Double) -> Double {
        var r = deg.truncatingRemainder(dividingBy: 360.0)
        if r > 180.0 { r -= 360.0 }
        if r < -180.0 { r += 360.0 }
        return r
    }

    public static func clampZIndex(_ z: Int32) -> Int32 {
        return min(max(z, minZIndex), maxZIndex)
    }

    public func enterEditMode() {
        guard !isVirtualAlbum else { return }
        mode = .edit
    }

    public func exitEditMode() {
        flushAllPendingTransforms()
        selectedPlacementId = nil
        isVaultPickerOpen = false
        isMovePageMenuOpen = false
        isPageManagementOpen = false
        mode = .view
    }

    public func selectPlacement(_ id: String?) {
        guard mode == .edit else { return }
        selectedPlacementId = id
    }

    public func getResolvedTransform(for placement: PersistedStampPlacementData) -> TransientPlacementTransformData {
        if let transient = transientTransforms[placement.id] {
            return transient
        }
        return TransientPlacementTransformData(
            x: placement.x,
            y: placement.y,
            scale: placement.scale,
            rotationDegrees: placement.rotationDegrees,
            zIndex: placement.zIndex
        )
    }

    public func updateTransientTransform(
        placementId: String,
        x: Double,
        y: Double,
        scale: Double,
        rotationDegrees: Double,
        zIndex: Int32
    ) {
        guard mode == .edit else { return }
        let clamped = TransientPlacementTransformData(
            x: Self.clampX(x),
            y: Self.clampY(y),
            scale: Self.clampScale(scale),
            rotationDegrees: Self.clampRotation(rotationDegrees),
            zIndex: Self.clampZIndex(zIndex)
        )
        transientTransforms[placementId] = clamped
    }

    public func commitTransform(placementId: String, originalPlacement: PersistedStampPlacementData) {
        guard let finalTransform = transientTransforms.removeValue(forKey: placementId) else { return }
        guard !isVirtualAlbum else { return }

        // Skip if practically unchanged
        if abs(finalTransform.x - originalPlacement.x) < 0.0001 &&
            abs(finalTransform.y - originalPlacement.y) < 0.0001 &&
            abs(finalTransform.scale - originalPlacement.scale) < 0.001 &&
            abs(finalTransform.rotationDegrees - originalPlacement.rotationDegrees) < 0.1 &&
            finalTransform.zIndex == originalPlacement.zIndex {
            return
        }

        undoStack.append(
            .transform(
                placementId: placementId,
                oldX: originalPlacement.x,
                oldY: originalPlacement.y,
                oldScale: originalPlacement.scale,
                oldRotation: originalPlacement.rotationDegrees,
                oldZIndex: originalPlacement.zIndex,
                newX: finalTransform.x,
                newY: finalTransform.y,
                newScale: finalTransform.scale,
                newRotation: finalTransform.rotationDegrees,
                newZIndex: finalTransform.zIndex
            )
        )

        isSaving = true
        IOSAlbumLayoutRepository.shared.updatePlacementTransform(
            placementId: placementId,
            albumId: albumId,
            x: finalTransform.x,
            y: finalTransform.y,
            scale: finalTransform.scale,
            rotationDegrees: finalTransform.rotationDegrees,
            zIndex: finalTransform.zIndex
        ) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
            }
        }
    }

    public func flushAllPendingTransforms() {
        guard !transientTransforms.isEmpty, !isVirtualAlbum else { return }
        let pending = transientTransforms
        transientTransforms.removeAll()

        for (id, t) in pending {
            IOSAlbumLayoutRepository.shared.updatePlacementTransform(
                placementId: id,
                albumId: albumId,
                x: t.x,
                y: t.y,
                scale: t.scale,
                rotationDegrees: t.rotationDegrees,
                zIndex: t.zIndex
            )
        }
    }

    public func addPlacementFromVault(
        stampId: String,
        targetPageIndex: Int32,
        existingPlacements: [PersistedStampPlacementData]
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        let safePageIndex = max(targetPageIndex, 0)
        let maxZ = existingPlacements.map { $0.zIndex }.max() ?? 0
        let nextZ = Self.clampZIndex(maxZ + 1)

        isSaving = true
        IOSAlbumLayoutRepository.shared.upsertPlacement(
            albumId: albumId,
            pageIndex: safePageIndex,
            stampId: stampId,
            x: 0.5,
            y: 0.5,
            scale: 1.0,
            rotationDegrees: 0.0,
            zIndex: nextZ
        ) { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isSaving = false
                switch result {
                case .success(let created):
                    self.undoStack.append(.add(created))
                    self.selectedPlacementId = created.id
                    self.isVaultPickerOpen = false
                case .failure(let error):
                    self.saveErrorMessage = error.localizedDescription
                }
            }
        }
    }

    public func deletePlacement(
        placementId: String,
        currentPlacement: PersistedStampPlacementData
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        undoStack.append(.delete(currentPlacement))
        if selectedPlacementId == placementId {
            selectedPlacementId = nil
        }

        isSaving = true
        IOSAlbumLayoutRepository.shared.deletePlacement(placementId: placementId, albumId: albumId) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
            }
        }
    }

    public func bringForward(
        placementId: String,
        currentPlacements: [PersistedStampPlacementData]
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        guard let item = currentPlacements.first(where: { $0.id == placementId }) else { return }
        let sorted = currentPlacements.sorted { $0.zIndex < $1.zIndex }
        guard let itemIdx = sorted.firstIndex(where: { $0.id == placementId }),
              itemIdx < sorted.count - 1 else { return }

        let swapWith = sorted[itemIdx + 1]
        let oldZ = item.zIndex
        let newZ = Self.clampZIndex(max(oldZ + 1, swapWith.zIndex + 1))

        undoStack.append(.zOrder(placementId: placementId, oldZIndex: oldZ, newZIndex: newZ))

        IOSAlbumLayoutRepository.shared.updatePlacementTransform(
            placementId: placementId,
            albumId: albumId,
            x: item.x,
            y: item.y,
            scale: item.scale,
            rotationDegrees: item.rotationDegrees,
            zIndex: newZ
        )
    }

    public func sendBackward(
        placementId: String,
        currentPlacements: [PersistedStampPlacementData]
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        guard let item = currentPlacements.first(where: { $0.id == placementId }) else { return }
        let sorted = currentPlacements.sorted { $0.zIndex < $1.zIndex }
        guard let itemIdx = sorted.firstIndex(where: { $0.id == placementId }),
              itemIdx > 0 else { return }

        let swapWith = sorted[itemIdx - 1]
        let oldZ = item.zIndex
        let newZ = Self.clampZIndex(min(oldZ - 1, swapWith.zIndex - 1))

        undoStack.append(.zOrder(placementId: placementId, oldZIndex: oldZ, newZIndex: newZ))

        IOSAlbumLayoutRepository.shared.updatePlacementTransform(
            placementId: placementId,
            albumId: albumId,
            x: item.x,
            y: item.y,
            scale: item.scale,
            rotationDegrees: item.rotationDegrees,
            zIndex: newZ
        )
    }

    public func movePlacementToPage(
        placementId: String,
        targetPageIndex: Int32,
        targetPageId: String? = nil,
        currentPlacement: PersistedStampPlacementData
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        let safeTarget = max(targetPageIndex, 0)
        guard safeTarget != currentPlacement.pageIndex || (targetPageId != nil && targetPageId != currentPlacement.pageId) else { return }

        undoStack.append(.movePage(placementId: placementId, oldPageIndex: currentPlacement.pageIndex, newPageIndex: safeTarget))

        isSaving = true
        IOSAlbumLayoutRepository.shared.movePlacementToPage(
            placementId: placementId,
            albumId: albumId,
            newPageIndex: safeTarget,
            newPageId: targetPageId
        ) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
            }
        }
    }

    public func openPageManagement() {
        guard mode == .edit else { return }
        isPageManagementOpen = true
    }

    public func closePageManagement() {
        isPageManagementOpen = false
    }

    public func appendPage(completion: ((Result<PersistedAlbumPageData, Error>) -> Void)? = nil) {
        guard !isVirtualAlbum, mode == .edit else { return }
        isSaving = true
        IOSAlbumLayoutRepository.shared.appendPage(albumId: albumId) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
                completion?(result)
            }
        }
    }

    public func removePage(
        pageId: String,
        placementsOnPage: [PersistedStampPlacementData],
        totalPageCount: Int,
        completion: ((Result<Void, Error>) -> Void)? = nil
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        guard placementsOnPage.isEmpty else {
            saveErrorMessage = NSLocalizedString("album_editor_page_has_stamps_error", comment: "")
            return
        }
        guard totalPageCount > 1 else {
            saveErrorMessage = NSLocalizedString("album_editor_cannot_remove_only_page", comment: "")
            return
        }

        isSaving = true
        IOSAlbumLayoutRepository.shared.removePage(albumId: albumId, pageId: pageId) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
                completion?(result)
            }
        }
    }

    public func reorderPage(
        pageId: String,
        direction: Int,
        pages: [PersistedAlbumPageData]
    ) {
        guard !isVirtualAlbum, mode == .edit else { return }
        let sorted = pages.sorted { $0.pageIndex < $1.pageIndex }
        guard let currentIndex = sorted.firstIndex(where: { $0.id == pageId }) else { return }
        let targetIndex = currentIndex + direction
        guard targetIndex >= 0 && targetIndex < sorted.count else { return }

        var ids = sorted.map { $0.id }
        let item = ids.remove(at: currentIndex)
        ids.insert(item, at: targetIndex)

        isSaving = true
        IOSAlbumLayoutRepository.shared.reorderPages(albumId: albumId, pageIds: ids) { [weak self] result in
            DispatchQueue.main.async {
                self?.isSaving = false
                if case .failure(let error) = result {
                    self?.saveErrorMessage = error.localizedDescription
                }
            }
        }
    }

    public func undo() {
        guard !undoStack.isEmpty, !isVirtualAlbum else { return }
        let lastAction = undoStack.removeLast()

        switch lastAction {
        case .add(let placement):
            IOSAlbumLayoutRepository.shared.deletePlacement(placementId: placement.id, albumId: albumId)
            if selectedPlacementId == placement.id {
                selectedPlacementId = nil
            }
        case .transform(let placementId, let oldX, let oldY, let oldScale, let oldRotation, let oldZIndex, _, _, _, _, _):
            IOSAlbumLayoutRepository.shared.updatePlacementTransform(
                placementId: placementId,
                albumId: albumId,
                x: oldX,
                y: oldY,
                scale: oldScale,
                rotationDegrees: oldRotation,
                zIndex: oldZIndex
            )
        case .zOrder(let placementId, let oldZIndex, _):
            let currentPlacements = IOSAlbumLayoutRepository.shared.layoutPlacements[albumId] ?? []
            if let item = currentPlacements.first(where: { $0.id == placementId }) {
                IOSAlbumLayoutRepository.shared.updatePlacementTransform(
                    placementId: placementId,
                    albumId: albumId,
                    x: item.x,
                    y: item.y,
                    scale: item.scale,
                    rotationDegrees: item.rotationDegrees,
                    zIndex: oldZIndex
                )
            }
        case .movePage(let placementId, let oldPageIndex, _):
            IOSAlbumLayoutRepository.shared.movePlacementToPage(
                placementId: placementId,
                albumId: albumId,
                newPageIndex: oldPageIndex
            )
        case .delete(let placement):
            IOSAlbumLayoutRepository.shared.upsertPlacement(
                albumId: albumId,
                pageIndex: placement.pageIndex,
                stampId: placement.stampId,
                x: placement.x,
                y: placement.y,
                scale: placement.scale,
                rotationDegrees: placement.rotationDegrees,
                zIndex: placement.zIndex
            ) { [weak self] result in
                DispatchQueue.main.async {
                    if case .success(let restored) = result {
                        self?.selectedPlacementId = restored.id
                    }
                }
            }
        }
    }

    public func clearSaveError() {
        saveErrorMessage = nil
    }

    public func resetSession() {
        mode = .view
        selectedPlacementId = nil
        transientTransforms.removeAll()
        undoStack.removeAll()
        isVaultPickerOpen = false
        isMovePageMenuOpen = false
        isPageManagementOpen = false
        isSaving = false
        saveErrorMessage = nil
    }
}
