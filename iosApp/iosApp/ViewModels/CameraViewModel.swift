import SwiftUI
import Combine
import shared

/// Native Swift ViewModel for CameraScreen managing filter presets, zoom scales, tune adjustments, and shutter captures.
public class CameraViewModel: ObservableObject {
    @Published public var selectedFilterIndex: Int = 5 // Film 35mm
    @Published public var zoomScale: CGFloat = 1.0
    @Published public var selectedZoomPill: String = "1x"
    @Published public var contrast: Double = 1.0
    @Published public var brightness: Double = 0.0
    @Published public var saturation: Double = 1.0
    @Published public var grain: Double = 0.2
    @Published public var flashOn: Bool = false
    @Published public var isCapturing: Bool = false
    @Published public var toastMessage: String? = nil
    @Published public var showToast: Bool = false
    @Published public var selectedImageIndex: Int = 0

    public let filters = FilterPresets.shared.ALL
    public let zoomOptions = ["1x", "2x", "3x", "5x"]
    public let samplePhotos = [
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
        "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
        "https://images.unsplash.com/photo-1528127269322-539801943592?w=600"
    ]

    public init() {}

    public var currentFilter: CameraFilterSpec {
        if selectedFilterIndex >= 0 && selectedFilterIndex < filters.count {
            return filters[selectedFilterIndex]
        }
        return FilterPresets.shared.ORIGINAL
    }

    public var currentPhotoUrl: String {
        samplePhotos[selectedImageIndex]
    }

    public func selectFilter(index: Int) {
        guard index >= 0 && index < filters.count else { return }
        selectedFilterIndex = index
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(filters[index].name)
    }

    public func nextFilter() {
        selectedFilterIndex = (selectedFilterIndex + 1) % filters.count
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(currentFilter.name)
    }

    public func previousFilter() {
        selectedFilterIndex = (selectedFilterIndex - 1 + filters.count) % filters.count
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(currentFilter.name)
    }

    public func setZoomPill(_ pill: String) {
        selectedZoomPill = pill
        HapticFeedbackManager.shared.playImpact(style: .medium)
        switch pill {
        case "1x": zoomScale = 1.0
        case "2x": zoomScale = 1.8
        case "3x": zoomScale = 2.8
        case "5x": zoomScale = 4.2
        default: zoomScale = 1.0
        }
    }

    public func toggleFlash() {
        flashOn.toggle()
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    public func cycleSamplePhoto() {
        selectedImageIndex = (selectedImageIndex + 1) % samplePhotos.count
        HapticFeedbackManager.shared.playImpact(style: .light)
    }

    public func capturePhoto(completion: @escaping (String) -> Void) {
        HapticFeedbackManager.shared.playImpact(style: .heavy)
        SoundEffectsManager.shared.playShutterSound()
        isCapturing = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
            guard let self = self else { return }
            self.isCapturing = false
            completion(self.currentPhotoUrl)
        }
    }

    private func showFilterToast(_ filterName: String) {
        toastMessage = "Preset: \(filterName)"
        withAnimation(.easeInOut(duration: 0.2)) {
            showToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            withAnimation(.easeInOut(duration: 0.2)) {
                self?.showToast = false
            }
        }
    }
}
