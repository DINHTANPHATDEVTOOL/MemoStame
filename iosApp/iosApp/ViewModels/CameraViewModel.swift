import SwiftUI
import Combine
#if canImport(UIKit)
import UIKit
import AVFoundation
#endif
import shared

/// Native Swift ViewModel for CameraScreen managing filter presets, zoom scales, tune adjustments, shutter captures, and camera states.
class CameraViewModel: ObservableObject {
    @Published var selectedFilterIndex: Int = 5 // Film 35mm
    @Published var nativeZoomFactor: CGFloat = 1.0
    @Published var displayZoom: CGFloat = 1.0
    @Published var opticalLenses: [OpticalLensPreset] = [
        OpticalLensPreset(id: "1x", displayFactor: 1.0, nativeZoomFactor: 1.0, label: "1x")
    ]
    @Published var minNativeZoom: CGFloat = 1.0
    @Published var maxNativeZoom: CGFloat = 5.0
    @Published var zoomMultiplier: CGFloat = 1.0
    @Published var contrast: Double = 1.0
    @Published var brightness: Double = 0.0
    @Published var saturation: Double = 1.0
    @Published var grain: Double = 0.2
    @Published var flashOn: Bool = false
    #if canImport(UIKit)
    @Published var cameraPosition: AVCaptureDevice.Position = .back
    @Published var capturedImage: UIImage? = nil
    #endif
    @Published var isCapturing: Bool = false
    @Published var toastMessage: String? = nil
    @Published var showToast: Bool = false
    @Published var showPhotoPicker: Bool = false
    @Published var selectedImageIndex: Int = 0

    let filters = FilterPresets.shared.ALL
    
    init() {}

    var currentFilter: CameraFilterSpec {
        if selectedFilterIndex >= 0 && selectedFilterIndex < filters.count {
            return filters[selectedFilterIndex]
        }
        return FilterPresets.shared.ORIGINAL
    }

    var currentPhotoUrl: String {
        ""
    }

    func toggleCameraPosition() {
        #if canImport(UIKit)
        cameraPosition = (cameraPosition == .back) ? .front : .back
        #endif
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func selectFilter(index: Int) {
        guard index >= 0 && index < filters.count else { return }
        selectedFilterIndex = index
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(filters[index].name)
    }

    func nextFilter() {
        selectedFilterIndex = (selectedFilterIndex + 1) % filters.count
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(currentFilter.name)
    }

    func previousFilter() {
        selectedFilterIndex = (selectedFilterIndex - 1 + filters.count) % filters.count
        HapticFeedbackManager.shared.playImpact(style: .light)
        showFilterToast(currentFilter.name)
    }

    func selectOpticalLens(_ lens: OpticalLensPreset) {
        nativeZoomFactor = lens.nativeZoomFactor
        displayZoom = lens.displayFactor
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func setContinuousZoom(nativeFactor: CGFloat) {
        let clamped = min(max(nativeFactor, minNativeZoom), maxNativeZoom)
        nativeZoomFactor = clamped
        displayZoom = clamped * zoomMultiplier
    }

    func toggleFlash() {
        flashOn.toggle()
        HapticFeedbackManager.shared.playImpact(style: .medium)
    }

    func capturePhoto(completion: @escaping (String) -> Void) {
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
