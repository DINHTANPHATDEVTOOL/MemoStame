import SwiftUI
#if canImport(UIKit)
import UIKit
import AVFoundation
import PhotosUI
#endif
import shared

struct OpticalLensPreset: Identifiable, Equatable {
    let id: String
    let displayFactor: CGFloat
    let nativeZoomFactor: CGFloat
    let label: String
}

struct CameraZoomCapabilities: Equatable {
    let presets: [OpticalLensPreset]
    let multiplier: CGFloat
    let minNativeZoom: CGFloat
    let maxNativeZoom: CGFloat
    let defaultNativeZoom: CGFloat
}

#if canImport(UIKit)
// MARK: - Native AVFoundation Live Camera Preview Layer
struct CameraPreviewView: UIViewRepresentable {
    @Binding var cameraPosition: AVCaptureDevice.Position
    @Binding var flashOn: Bool
    @Binding var nativeZoomFactor: CGFloat
    @Binding var captureTrigger: Bool
    var onCapabilitiesDiscovered: ((CameraZoomCapabilities) -> Void)?
    var onPhotoCaptured: ((UIImage) -> Void)?

    class Coordinator: NSObject, AVCapturePhotoCaptureDelegate {
        var parent: CameraPreviewView

        init(_ parent: CameraPreviewView) {
            self.parent = parent
        }

        func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
            guard error == nil,
                  let data = photo.fileDataRepresentation(),
                  let image = UIImage(data: data) else {
                return
            }
            DispatchQueue.main.async {
                self.parent.onPhotoCaptured?(image)
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> CameraPreviewContainerView {
        let view = CameraPreviewContainerView()
        view.onCapabilitiesDiscovered = onCapabilitiesDiscovered
        view.setupSession(position: cameraPosition, coordinator: context.coordinator)
        return view
    }

    func updateUIView(_ uiView: CameraPreviewContainerView, context: Context) {
        uiView.onCapabilitiesDiscovered = onCapabilitiesDiscovered
        uiView.updateCamera(position: cameraPosition)
        uiView.setZoomFactor(nativeZoomFactor)
        if captureTrigger {
            DispatchQueue.main.async {
                captureTrigger = false
            }
            uiView.capturePhoto(coordinator: context.coordinator, flashOn: flashOn)
        }
    }
}

class CameraPreviewContainerView: UIView {
    private var captureSession: AVCaptureSession?
    private var videoPreviewLayer: AVCaptureVideoPreviewLayer?
    private var photoOutput = AVCapturePhotoOutput()
    private var currentPosition: AVCaptureDevice.Position = .back
    var onCapabilitiesDiscovered: ((CameraZoomCapabilities) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        videoPreviewLayer?.frame = self.bounds
    }

    static func selectBestCameraDevice(for position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        if position == .back {
            let priorityTypes: [AVCaptureDevice.DeviceType] = [
                .builtInTripleCamera,
                .builtInDualWideCamera,
                .builtInDualCamera,
                .builtInWideAngleCamera
            ]
            let discovery = AVCaptureDevice.DiscoverySession(
                deviceTypes: priorityTypes,
                mediaType: .video,
                position: .back
            )
            for type in priorityTypes {
                if let dev = discovery.devices.first(where: { $0.deviceType == type }) {
                    return dev
                }
            }
            return discovery.devices.first ?? AVCaptureDevice.default(for: .video)
        } else {
            return AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front)
                ?? AVCaptureDevice.default(for: .video)
        }
    }

    static func discoverCapabilities(for device: AVCaptureDevice) -> CameraZoomCapabilities {
        let minNative = device.minAvailableVideoZoomFactor
        let maxNative = min(device.maxAvailableVideoZoomFactor, device.activeFormat.videoMaxZoomFactor)

        var resolvedMultiplier: CGFloat? = nil
        if device.responds(to: NSSelectorFromString("displayVideoZoomFactorMultiplier")),
           let num = device.value(forKey: "displayVideoZoomFactorMultiplier") as? NSNumber {
            resolvedMultiplier = CGFloat(num.doubleValue)
        } else if device.activeFormat.responds(to: NSSelectorFromString("displayVideoZoomFactorMultiplier")),
                  let num = device.activeFormat.value(forKey: "displayVideoZoomFactorMultiplier") as? NSNumber {
            resolvedMultiplier = CGFloat(num.doubleValue)
        }

        let constituents = device.constituentDevices
        let switchFactors = device.virtualDeviceSwitchOverVideoZoomFactors.map { CGFloat($0.doubleValue) }

        var presets: [OpticalLensPreset] = []
        var defaultNativeZoom: CGFloat = 1.0

        if !constituents.isEmpty {
            let hasUltraWide = constituents.first?.deviceType == .builtInUltraWideCamera
            if hasUltraWide && !switchFactors.isEmpty {
                // First switch factor is from Ultra Wide to Wide (1x baseline)
                let wideNativeFactor = switchFactors[0]
                let multiplier = resolvedMultiplier ?? (1.0 / wideNativeFactor)
                resolvedMultiplier = multiplier
                defaultNativeZoom = wideNativeFactor

                // Ultra-Wide lens
                let uwDisplay = minNative * multiplier
                presets.append(OpticalLensPreset(
                    id: "uw",
                    displayFactor: uwDisplay,
                    nativeZoomFactor: minNative,
                    label: formatDisplayFactor(uwDisplay)
                ))

                // Wide lens (1.0x)
                let wideDisplay = wideNativeFactor * multiplier
                presets.append(OpticalLensPreset(
                    id: "wide",
                    displayFactor: wideDisplay,
                    nativeZoomFactor: wideNativeFactor,
                    label: formatDisplayFactor(wideDisplay)
                ))

                // Telephoto lens if triple camera
                if switchFactors.count >= 2 {
                    let teleNativeFactor = switchFactors[1]
                    let teleDisplay = teleNativeFactor * multiplier
                    presets.append(OpticalLensPreset(
                        id: "tele",
                        displayFactor: teleDisplay,
                        nativeZoomFactor: teleNativeFactor,
                        label: formatDisplayFactor(teleDisplay)
                    ))
                }
            } else if !switchFactors.isEmpty {
                // Dual camera: Wide (constituent 0) + Telephoto (constituent 1)
                let multiplier = resolvedMultiplier ?? 1.0
                resolvedMultiplier = multiplier
                defaultNativeZoom = 1.0

                presets.append(OpticalLensPreset(
                    id: "wide",
                    displayFactor: 1.0,
                    nativeZoomFactor: 1.0,
                    label: "1x"
                ))

                let teleNativeFactor = switchFactors[0]
                let teleDisplay = teleNativeFactor * multiplier
                presets.append(OpticalLensPreset(
                    id: "tele",
                    displayFactor: teleDisplay,
                    nativeZoomFactor: teleNativeFactor,
                    label: formatDisplayFactor(teleDisplay)
                ))
            }
        }

        let finalMultiplier = resolvedMultiplier ?? 1.0

        if presets.isEmpty {
            presets.append(OpticalLensPreset(
                id: "1x",
                displayFactor: 1.0,
                nativeZoomFactor: 1.0,
                label: "1x"
            ))
            defaultNativeZoom = 1.0
        }

        return CameraZoomCapabilities(
            presets: presets,
            multiplier: finalMultiplier,
            minNativeZoom: minNative,
            maxNativeZoom: maxNative,
            defaultNativeZoom: defaultNativeZoom
        )
    }

    static func formatDisplayFactor(_ factor: CGFloat) -> String {
        if factor.truncatingRemainder(dividingBy: 1.0) == 0 {
            return String(format: "%.0fx", factor)
        } else {
            return String(format: "%.1fx", factor)
        }
    }

    func setupSession(position: AVCaptureDevice.Position, coordinator: CameraPreviewView.Coordinator) {
        self.currentPosition = position
        let session = AVCaptureSession()
        session.beginConfiguration()
        session.sessionPreset = .photo

        guard let device = Self.selectBestCameraDevice(for: position),
              let input = try? AVCaptureDeviceInput(device: device) else {
            session.commitConfiguration()
            return
        }

        if session.canAddInput(input) {
            session.addInput(input)
        }

        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }

        session.commitConfiguration()

        let capabilities = Self.discoverCapabilities(for: device)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = capabilities.defaultNativeZoom
            device.unlockForConfiguration()
        } catch {
            print("Initial videoZoomFactor error: \(error)")
        }

        DispatchQueue.main.async {
            self.onCapabilitiesDiscovered?(capabilities)
        }

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = self.bounds
        self.layer.addSublayer(previewLayer)

        self.captureSession = session
        self.videoPreviewLayer = previewLayer

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
    }

    func updateCamera(position: AVCaptureDevice.Position) {
        guard position != currentPosition, let session = captureSession else { return }
        currentPosition = position
        DispatchQueue.global(qos: .userInitiated).async {
            session.beginConfiguration()
            for input in session.inputs {
                session.removeInput(input)
            }
            if let device = Self.selectBestCameraDevice(for: position),
               let input = try? AVCaptureDeviceInput(device: device),
               session.canAddInput(input) {
                session.addInput(input)
                let capabilities = Self.discoverCapabilities(for: device)
                do {
                    try device.lockForConfiguration()
                    device.videoZoomFactor = capabilities.defaultNativeZoom
                    device.unlockForConfiguration()
                } catch {
                    print("Update videoZoomFactor error: \(error)")
                }
                DispatchQueue.main.async {
                    self.onCapabilitiesDiscovered?(capabilities)
                }
            }
            session.commitConfiguration()
        }
    }

    func setZoomFactor(_ factor: CGFloat) {
        guard let session = captureSession,
              let input = session.inputs.first as? AVCaptureDeviceInput else { return }
        let device = input.device
        do {
            try device.lockForConfiguration()
            let minZoom = device.minAvailableVideoZoomFactor
            let maxZoom = min(device.activeFormat.videoMaxZoomFactor, device.maxAvailableVideoZoomFactor)
            let clampedZoom = min(max(factor, minZoom), maxZoom)
            device.videoZoomFactor = clampedZoom
            device.unlockForConfiguration()
        } catch {
            print("Hardware zoom lock error: \(error)")
        }
    }

    func capturePhoto(coordinator: CameraPreviewView.Coordinator, flashOn: Bool) {
        let settings = AVCapturePhotoSettings()
        if photoOutput.supportedFlashModes.contains(.on) {
            settings.flashMode = flashOn ? .on : .off
        }
        photoOutput.capturePhoto(with: settings, delegate: coordinator)
    }
}

// MARK: - Native Photo Library Picker
struct ImagePickerView: UIViewControllerRepresentable {
    @Environment(\.presentationMode) var presentationMode
    var onImagePicked: (UIImage) -> Void

    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePickerView

        init(_ parent: ImagePickerView) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let uiImage = info[.originalImage] as? UIImage {
                parent.onImagePicked(uiImage)
            }
            parent.presentationMode.wrappedValue.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.presentationMode.wrappedValue.dismiss()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
}
#endif

struct CameraScreenView: View {
    let replyToPostId: String?
    var onNavigateToNote: (String) -> Void
    var onCancel: () -> Void

    @State private var selectedFilterIndex: Int = 5 // Film 35mm
    @State private var nativeZoomFactor: CGFloat = 1.0
    @State private var displayZoom: CGFloat = 1.0
    @State private var zoomBeforeGesture: CGFloat = 1.0
    @State private var opticalLenses: [OpticalLensPreset] = [
        OpticalLensPreset(id: "1x", displayFactor: 1.0, nativeZoomFactor: 1.0, label: "1x")
    ]
    @State private var zoomMultiplier: CGFloat = 1.0
    @State private var minNativeZoom: CGFloat = 1.0
    @State private var maxNativeZoom: CGFloat = 5.0
    @State private var contrast: Double = 1.0
    @State private var brightness: Double = 0.0
    @State private var saturation: Double = 1.0
    @State private var grain: Double = 0.2
    @State private var showTuneSheet: Bool = false
    @State private var flashOn: Bool = false
    @State private var cameraPosition: AVCaptureDevice.Position = .back
    @State private var captureTrigger: Bool = false
    @State private var isCapturing: Bool = false
    @State private var showPhotoPicker: Bool = false
    @State private var selectedUIImage: UIImage? = nil
    @State private var toastMessage: String? = nil
    @State private var showToast: Bool = false
    @State private var isCameraAvailable: Bool = true

    @State private var pressOffset: CGFloat = 0
    @State private var showFlashOverlay: Bool = false
    @State private var showPunchReveal: Bool = false

    @State private var showImagePicker: Bool = false
    @State private var pickerSourceType: UIImagePickerController.SourceType = .camera
    @State private var customCapturedImageUrl: String? = nil
    @State private var actualMoldFrame: CGRect = .zero

    let filters = FilterPresets.shared.ALL

    var activePhotoUrl: String {
        return customCapturedImageUrl ?? ""
    }

    var currentFilter: CameraFilterSpec {
        if selectedFilterIndex >= 0 && selectedFilterIndex < filters.count {
            return filters[selectedFilterIndex]
        }
        return FilterPresets.shared.ORIGINAL
    }

    var body: some View {
        ZStack {
            // Layer 1: Full-Screen Live Camera Preview / Frozen Captured Photo Background
            #if canImport(UIKit)
            if let uiImage = selectedUIImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .ignoresSafeArea()
            } else {
                CameraPreviewView(
                    cameraPosition: $cameraPosition,
                    flashOn: $flashOn,
                    nativeZoomFactor: $nativeZoomFactor,
                    captureTrigger: $captureTrigger,
                    onCapabilitiesDiscovered: { caps in
                        self.opticalLenses = caps.presets
                        self.zoomMultiplier = caps.multiplier
                        self.minNativeZoom = caps.minNativeZoom
                        self.maxNativeZoom = caps.maxNativeZoom
                        self.nativeZoomFactor = caps.defaultNativeZoom
                        self.zoomBeforeGesture = caps.defaultNativeZoom
                        self.displayZoom = caps.defaultNativeZoom * caps.multiplier
                    },
                    onPhotoCaptured: { img in
                        self.selectedUIImage = img
                    }
                )
                .ignoresSafeArea()
            }
            #else
            AsyncImage(url: URL(string: activePhotoUrl)) { phase in
                if let img = phase.image {
                    img.resizable()
                        .aspectRatio(contentMode: .fill)
                        .scaleEffect(displayZoom)
                        .ignoresSafeArea()
                } else {
                    Color.black.ignoresSafeArea()
                }
            }
            #endif

            // Color Filter Grading Overlay
            FilterOverlayView(filter: currentFilter)
                .allowsHitTesting(false)
                .ignoresSafeArea()

            // Dark Vignette Gradient Gradients for Control Contrast
            VStack {
                LinearGradient(colors: [Color.black.opacity(0.65), Color.clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 120)
                    .ignoresSafeArea()
                Spacer()
                LinearGradient(colors: [Color.clear, Color.black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 180)
                    .ignoresSafeArea()
            }
            .allowsHitTesting(false)

            // Layer 2: Mold Viewfinder & Controls Stack
            VStack(spacing: 0) {
                // Top Camera Controls Bar
                HStack {
                    Button(action: onCancel) {
                        Image(systemName: "xmark")
                            .font(.title2.bold())
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.black.opacity(0.35))
                            .clipShape(Circle())
                    }

                    Spacer()

                    if let replyId = replyToPostId {
                        HStack(spacing: 6) {
                            MemoStampIcon(key: MemoStampIconKey.replyStamp.key, contentDescription: "Reply")
                                .frame(width: 14, height: 14)
                                .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                            Text("REPLYING TO POST #\(String(replyId.prefix(4)))")
                                .font(.caption.bold())
                                .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.45))
                        .cornerRadius(14)

                        Spacer()
                    }

                    HStack(spacing: 12) {
                        // Camera Position Switch (Front / Back)
                        Button(action: {
                            cameraPosition = (cameraPosition == .back) ? .front : .back
                            triggerHapticFeedback()
                        }) {
                            Image(systemName: "camera.rotate.fill")
                                .font(.title3)
                                .foregroundColor(.white)
                                .frame(width: 40, height: 40)
                                .background(Color.black.opacity(0.35))
                                .clipShape(Circle())
                        }

                        // Flash Toggle
                        Button(action: {
                            flashOn.toggle()
                            triggerHapticFeedback()
                        }) {
                            Image(systemName: flashOn ? "bolt.fill" : "bolt.slash.fill")
                                .font(.title3)
                                .foregroundColor(flashOn ? .yellow : .white)
                                .frame(width: 40, height: 40)
                                .background(Color.black.opacity(0.35))
                                .clipShape(Circle())
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 6)

                Spacer()

                // Authentic Floating Metal Mold Frame Overlay (Crisp Sharp Stamp Press Mold matching Android)
                ZStack {
                    Image("stamp_press_mold")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .allowsHitTesting(false)

                    // Swipe Toast Notification
                    if showToast, let msg = toastMessage {
                        VStack {
                            Text(msg)
                                .font(.subheadline.bold())
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color.black.opacity(0.75))
                                .cornerRadius(20)
                                .transition(.move(edge: .top).combined(with: .opacity))
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .aspectRatio(881.0 / 1159.0, contentMode: .fit)
                .background(
                    GeometryReader { geo in
                        Color.clear.preference(key: MoldFramePreferenceKey.self, value: geo.frame(in: .global))
                    }
                )
                .shadow(color: Color.black.opacity(0.5), radius: 16, x: 0, y: 8)
                .padding(.horizontal, 20)
                .offset(y: pressOffset)
                .gesture(
                    DragGesture(minimumDistance: 30)
                        .onEnded { value in
                            if value.translation.width < -40 {
                                selectedFilterIndex = (selectedFilterIndex + 1) % filters.count
                                showFilterToast(currentFilter.name)
                            } else if value.translation.width > 40 {
                                selectedFilterIndex = (selectedFilterIndex - 1 + filters.count) % filters.count
                                showFilterToast(currentFilter.name)
                            }
                        }
                )
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            let target = zoomBeforeGesture * value
                            let clamped = min(max(target, minNativeZoom), maxNativeZoom)
                            nativeZoomFactor = clamped
                            displayZoom = clamped * zoomMultiplier
                        }
                        .onEnded { _ in
                            zoomBeforeGesture = nativeZoomFactor
                        }
                )

                Spacer()

                // Optical Lens Presets & Live Zoom Readout
                VStack(spacing: 6) {
                    let isMatchingLens = opticalLenses.contains(where: { abs(displayZoom - $0.displayFactor) < 0.08 })
                    if !isMatchingLens {
                        Text(String(format: "%.1fx", displayZoom))
                            .font(.caption2.bold())
                            .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(Color(red: 1.0, green: 0.84, blue: 0.0).opacity(0.4), lineWidth: 1)
                            )
                            .transition(.opacity)
                    }

                    HStack(spacing: 8) {
                        ForEach(opticalLenses) { lens in
                            let isSelected = abs(displayZoom - lens.displayFactor) < 0.08
                            Button(action: {
                                triggerHapticFeedback()
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    nativeZoomFactor = lens.nativeZoomFactor
                                    zoomBeforeGesture = lens.nativeZoomFactor
                                    displayZoom = lens.displayFactor
                                }
                            }) {
                                Text(lens.label)
                                    .font(.caption2.bold())
                                    .foregroundColor(isSelected ? .black : .white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(isSelected ? Color.white : Color.black.opacity(0.4))
                                    .cornerRadius(14)
                            }
                        }
                    }
                }
                .padding(.vertical, 8)

                Spacer()

                // Filter Selection Carousel Bar
                VStack(spacing: 14) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(0..<filters.count, id: \.self) { index in
                                let filter = filters[index]
                                Button(action: {
                                    selectedFilterIndex = index
                                    triggerHapticFeedback()
                                    showFilterToast(filter.name)
                                }) {
                                    VStack(spacing: 4) {
                                        Text(filter.name)
                                            .font(.caption.weight(selectedFilterIndex == index ? .bold : .medium))
                                            .foregroundColor(selectedFilterIndex == index ? Color(red: 0.85, green: 0.25, blue: 0.20) : .white.opacity(0.8))

                                        Circle()
                                            .fill(selectedFilterIndex == index ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.clear)
                                            .frame(width: 6, height: 6)
                                    }
                                    .padding(.horizontal, 8)
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                    }

                    // Shutter & Photo Picker Control Bar
                    HStack {
                        // Open Device Gallery / Photo Picker
                        Button(action: {
                            triggerHapticFeedback()
                            pickerSourceType = .photoLibrary
                            showImagePicker = true
                        }) {
                            ZStack {
                                Circle()
                                    .fill(Color.white.opacity(0.18))
                                    .frame(width: 48, height: 48)
                                Image(systemName: "photo.on.rectangle")
                                    .font(.title3)
                                    .foregroundColor(.white)
                            }
                        }

                        Spacer()

                        // Master Camera Shutter / Confirm Button
                        Button(action: {
                            triggerHapticFeedback()
                            #if canImport(UIKit)
                            AudioServicesPlaySystemSound(1108) // Stamp Press Shutter Chime
                            #endif

                            if let img = selectedUIImage {
                                handleImageSelected(img)
                            } else {
                                isCapturing = true
                                captureTrigger = true

                                // Trigger Stamp Press Impact Animation & Flash
                                withAnimation(.easeInOut(duration: 0.15)) {
                                    pressOffset = 30
                                    showFlashOverlay = true
                                }

                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                    withAnimation(.easeInOut(duration: 0.15)) {
                                        pressOffset = 0
                                        showFlashOverlay = false
                                        showPunchReveal = true
                                    }
                                }

                                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                                    showPunchReveal = false
                                    isCapturing = false
                                    if let img = selectedUIImage {
                                        handleImageSelected(img)
                                    } else {
                                        onNavigateToNote(activePhotoUrl)
                                    }
                                }
                            }
                        }) {
                            ZStack {
                                Circle()
                                    .stroke(Color.white, lineWidth: 4)
                                    .frame(width: 76, height: 76)
                                Circle()
                                    .fill(isCapturing ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.white)
                                    .frame(width: 64, height: 64)
                                if selectedUIImage != nil {
                                    Image(systemName: "checkmark")
                                        .font(.title2.bold())
                                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                }
                            }
                        }

                        if selectedUIImage != nil {
                            Button(action: {
                                triggerHapticFeedback()
                                selectedUIImage = nil
                            }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.white.opacity(0.18))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .font(.title3)
                                        .foregroundColor(.white)
                                }
                            }
                        }

                        Spacer()

                        // Fine-Tune Controls Sheet Button
                        Button(action: { showTuneSheet = true }) {
                            ZStack {
                                Circle()
                                    .fill(Color.white.opacity(0.18))
                                    .frame(width: 48, height: 48)
                                Image(systemName: "slider.horizontal.3")
                                    .font(.title3)
                                    .foregroundColor(.white)
                            }
                        }
                    }
                    .padding(.horizontal, 34)
                    .padding(.bottom, 24)
                }
            }

            // Screen Flash Impact Overlay
            if showFlashOverlay {
                Color.white
                    .ignoresSafeArea()
                    .transition(.opacity)
            }

            // Stamp Reveal Die-Cut Punch Pop-Up & Banner Overlay
            if showPunchReveal {
                VStack(spacing: 18) {
                    Spacer()

                    #if canImport(UIKit)
                    if let img = selectedUIImage {
                        Image(uiImage: img)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 220, height: 289)
                            .clipShape(PerforatedStampShape(notchRatio: 0.025, spacingRatio: 0.075))
                            .overlay(
                                PerforatedStampShape(notchRatio: 0.025, spacingRatio: 0.075)
                                    .stroke(Color.white.opacity(0.85), lineWidth: 1.5)
                            )
                            .shadow(color: Color.black.opacity(0.65), radius: 18, x: 0, y: 10)
                            .transition(.scale(scale: 0.85).combined(with: .opacity))
                    }
                    #endif

                    HStack(spacing: 8) {
                        Image(systemName: "sparkles")
                            .foregroundColor(MSColors.gold)
                        Text("✦ STAMPED MEMORY ✦")
                            .font(.headline.bold())
                            .foregroundColor(MSColors.gold)
                        Image(systemName: "sparkles")
                            .foregroundColor(MSColors.gold)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.black.opacity(0.85))
                    .cornerRadius(24)
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .stroke(MSColors.gold, lineWidth: 1.5)
                    )
                    .shadow(color: MSColors.gold.opacity(0.4), radius: 10, x: 0, y: 4)
                    .padding(.bottom, 60)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .sheet(isPresented: $showImagePicker) {
            #if canImport(UIKit)
            SwiftUIImagePicker(sourceType: pickerSourceType) { pickedImage in
                if let image = pickedImage {
                    handleImageSelected(image)
                }
            }
            #endif
        }
        .sheet(isPresented: $showTuneSheet) {
            CameraTuneAdjustmentView(
                contrast: $contrast,
                brightness: $brightness,
                saturation: $saturation,
                grain: $grain
            )
        }
        #if canImport(UIKit)
        .sheet(isPresented: $showPhotoPicker) {
            ImagePickerView { pickedImage in
                handleImageSelected(pickedImage)
            }
        }
        #endif
        .onPreferenceChange(MoldFramePreferenceKey.self) { frame in
            if frame.width > 0 && frame.height > 0 {
                self.actualMoldFrame = frame
            }
        }
        .onAppear {
            checkCameraAvailability()
        }
    }

    private func checkCameraAvailability() {
        #if canImport(UIKit)
        if AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) == nil {
            isCameraAvailable = false
        }
        #else
        isCameraAvailable = false
        #endif
    }

    private func handleImageSelected(_ image: UIImage) {
        #if canImport(UIKit)
        let croppedImage = cropToStampAspectRatio(image)
        let filteredImage = applyFilterToImage(
            croppedImage,
            filterSpec: currentFilter,
            contrast: contrast,
            brightness: brightness,
            saturation: saturation,
            grain: grain
        )
        if let data = filteredImage.jpegData(compressionQuality: 0.90) {
            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent("stamp_photo_\(UUID().uuidString).jpg")
            try? data.write(to: fileURL)
            self.customCapturedImageUrl = fileURL.absoluteString
            onNavigateToNote(fileURL.absoluteString)
            return
        }
        #endif
        onNavigateToNote(activePhotoUrl)
    }

    private func applyFilterToImage(
        _ inputImage: UIImage,
        filterSpec: CameraFilterSpec,
        contrast: Double,
        brightness: Double,
        saturation: Double,
        grain: Double
    ) -> UIImage {
        #if canImport(UIKit)
        guard let ciImage = CIImage(image: inputImage) else { return inputImage }
        var outputCI = ciImage

        // 1. Core Image preset filters
        if filterSpec.id == "mono_film" || filterSpec.id == "bw" {
            if let noirFilter = CIFilter(name: "CIPhotoEffectNoir") {
                noirFilter.setValue(outputCI, forKey: kCIInputImageKey)
                if let result = noirFilter.outputImage { outputCI = result }
            }
        } else if filterSpec.id == "vintage_fade" || filterSpec.id == "film_35mm" {
            if let instantFilter = CIFilter(name: "CIPhotoEffectInstant") {
                instantFilter.setValue(outputCI, forKey: kCIInputImageKey)
                if let result = instantFilter.outputImage { outputCI = result }
            }
        } else if filterSpec.id == "warm" || filterSpec.id == "cafe_cozy" {
            if let processFilter = CIFilter(name: "CIPhotoEffectProcess") {
                processFilter.setValue(outputCI, forKey: kCIInputImageKey)
                if let result = processFilter.outputImage { outputCI = result }
            }
        }

        // 2. Fine-tune adjustment parameters
        if let colorControls = CIFilter(name: "CIColorControls") {
            colorControls.setValue(outputCI, forKey: kCIInputImageKey)
            colorControls.setValue(contrast * Double(filterSpec.contrast), forKey: kCIInputContrastKey)
            colorControls.setValue(brightness + Double(filterSpec.exposure) * 0.08, forKey: kCIInputBrightnessKey)
            colorControls.setValue(saturation * Double(filterSpec.saturation), forKey: kCIInputSaturationKey)
            if let result = colorControls.outputImage { outputCI = result }
        }

        // 3. Authentic Film Grain Generator (CIRandomGenerator + CIColorMatrix Alpha Blending)
        if grain > 0 {
            if let noiseFilter = CIFilter(name: "CIRandomGenerator"),
               let rawNoise = noiseFilter.outputImage {
                let croppedNoise = rawNoise.cropped(to: outputCI.extent)
                if let monoFilter = CIFilter(name: "CIColorMonochrome") {
                    monoFilter.setValue(croppedNoise, forKey: kCIInputImageKey)
                    monoFilter.setValue(CIColor(red: 0.5, green: 0.5, blue: 0.5), forKey: kCIInputColorKey)
                    monoFilter.setValue(1.0, forKey: kCIInputIntensityKey)
                    if let grayNoise = monoFilter.outputImage,
                       let matrixFilter = CIFilter(name: "CIColorMatrix") {
                        let gAlpha = CGFloat(0.18 * grain)
                        matrixFilter.setValue(grayNoise, forKey: kCIInputImageKey)
                        matrixFilter.setValue(CIVector(x: 1, y: 0, z: 0, w: 0), forKey: "inputRVector")
                        matrixFilter.setValue(CIVector(x: 0, y: 1, z: 0, w: 0), forKey: "inputGVector")
                        matrixFilter.setValue(CIVector(x: 0, y: 0, z: 1, w: 0), forKey: "inputBVector")
                        matrixFilter.setValue(CIVector(x: 0, y: 0, z: 0, w: gAlpha), forKey: "inputAVector")
                        if let transparentNoise = matrixFilter.outputImage,
                           let blendFilter = CIFilter(name: "CISourceOverCompositing") {
                            blendFilter.setValue(transparentNoise, forKey: kCIInputImageKey)
                            blendFilter.setValue(outputCI, forKey: kCIInputBackgroundImageKey)
                            if let blended = blendFilter.outputImage {
                                outputCI = blended
                            }
                        }
                    }
                }
            }
        }

        // 4. Lens Vignette
        if filterSpec.vignette > 0 {
            if let vignetteFilter = CIFilter(name: "CIVignette") {
                vignetteFilter.setValue(outputCI, forKey: kCIInputImageKey)
                vignetteFilter.setValue(Double(filterSpec.vignette + 0.3), forKey: kCIInputRadiusKey)
                vignetteFilter.setValue(0.6, forKey: kCIInputIntensityKey)
                if let result = vignetteFilter.outputImage { outputCI = result }
            }
        }

        let ciContext = CIContext(options: nil)
        if let cgImage = ciContext.createCGImage(outputCI, from: outputCI.extent) {
            return UIImage(cgImage: cgImage, scale: inputImage.scale, orientation: .up)
        }
        return inputImage
        #else
        return inputImage
        #endif
    }

    private func cropToStampAspectRatio(_ image: UIImage) -> UIImage {
        #if canImport(UIKit)
        // 1. Normalize image orientation to .up so CGImage pixel cropping matches visual screen orientation
        let normalizedImage: UIImage
        if image.imageOrientation == .up {
            normalizedImage = image
        } else {
            UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
            image.draw(in: CGRect(origin: .zero, size: image.size))
            normalizedImage = UIGraphicsGetImageFromCurrentImageContext() ?? image
            UIGraphicsEndImageContext()
        }

        let imgWidth = normalizedImage.size.width
        let imgHeight = normalizedImage.size.height

        // 2. Exact AVCaptureVideoPreviewLayer (.resizeAspectFill) Aspect Ratio Mapping
        let screenSize = UIScreen.main.bounds.size
        let sWidth = screenSize.width > 0 ? screenSize.width : 390.0
        let sHeight = screenSize.height > 0 ? screenSize.height : 844.0

        // Scale factor of AVCaptureVideoPreviewLayer aspectFill:
        let scale = max(sWidth / imgWidth, sHeight / imgHeight)
        let renderWidth = imgWidth * scale
        let renderHeight = imgHeight * scale
        let offsetX = (sWidth - renderWidth) / 2.0
        let offsetY = (sHeight - renderHeight) / 2.0

        // Measure actual SwiftUI rendered mold aperture coordinates:
        let moldWidth: CGFloat
        let moldHeight: CGFloat
        let moldLeft: CGFloat
        let moldTop: CGFloat

        if actualMoldFrame.width > 0 && actualMoldFrame.height > 0 {
            moldLeft = actualMoldFrame.minX
            moldTop = actualMoldFrame.minY
            moldWidth = actualMoldFrame.width
            moldHeight = actualMoldFrame.height
        } else {
            moldWidth = sWidth * StampGeometry.moldWidthRatio
            moldHeight = moldWidth * StampGeometry.moldAspectRatio
            moldLeft = (sWidth - moldWidth) / 2.0
            moldTop = (sHeight - moldHeight) / 2.0
        }

        let apLeft = moldLeft + moldWidth * StampGeometry.innerLeftRatio
        let apTop = moldTop + moldHeight * StampGeometry.innerTopRatio
        let apRight = moldLeft + moldWidth * StampGeometry.innerRightRatio
        let apBottom = moldTop + moldHeight * StampGeometry.innerBottomRatio
        let apWidth = apRight - apLeft
        let apHeight = apBottom - apTop

        // Transform screen aperture rectangle back into high-resolution photo pixels:
        var cropX = max(0, (apLeft - offsetX) / scale)
        let cropY = max(0, (apTop - offsetY) / scale)
        let cropW = min(imgWidth - cropX, apWidth / scale)
        let cropH = min(imgHeight - cropY, apHeight / scale)

        if cameraPosition == .front {
            cropX = max(0, imgWidth - cropX - cropW)
        }

        let cropRect = CGRect(x: cropX, y: cropY, width: cropW, height: cropH)

        if let cgImage = normalizedImage.cgImage?.cropping(to: cropRect) {
            return UIImage(cgImage: cgImage, scale: normalizedImage.scale, orientation: .up)
        }
        return normalizedImage
        #else
        return image
        #endif
    }

    private func showFilterToast(_ filterName: String) {
        toastMessage = "Preset: \(filterName)"
        withAnimation(.easeInOut(duration: 0.2)) {
            showToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation(.easeInOut(duration: 0.2)) {
                showToast = false
            }
        }
    }

    private func triggerHapticFeedback() {
        #if canImport(UIKit)
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        #endif
    }
}

// Subview: Filter Color Tint Overlay Renderer
struct FilterOverlayView: View {
    let filter: CameraFilterSpec

    var body: some View {
        Group {
            if filter.id == "warm" || filter.id == "cafe_cozy" {
                Color.orange.opacity(0.16)
            } else if filter.id == "soft" {
                Color.pink.opacity(0.08)
            } else if filter.id == "vintage_fade" {
                Color.yellow.opacity(0.12)
            } else if filter.id == "mono_film" {
                Color.black.opacity(0.45)
            } else if filter.id == "film_35mm" {
                Color.orange.opacity(0.10)
            } else {
                Color.clear
            }
        }
        .cornerRadius(24)
    }
}

// Subview: Camera Fine-Tune Adjustment Sheet
struct CameraTuneAdjustmentView: View {
    @Binding var contrast: Double
    @Binding var brightness: Double
    @Binding var saturation: Double
    @Binding var grain: Double

    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Contrast")
                            .font(.subheadline.bold())
                        Spacer()
                        Text("\(String(format: "%.2f", contrast))")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $contrast, in: 0.5...1.5)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Brightness")
                            .font(.subheadline.bold())
                        Spacer()
                        Text("\(String(format: "%.2f", brightness))")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $brightness, in: -0.5...0.5)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Saturation")
                            .font(.subheadline.bold())
                        Spacer()
                        Text("\(String(format: "%.2f", saturation))")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $saturation, in: 0.0...2.0)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Film Grain")
                            .font(.subheadline.bold())
                        Spacer()
                        Text("\(String(format: "%.2f", grain))")
                            .font(.caption.monospacedDigit())
                            .foregroundColor(.secondary)
                    }
                    Slider(value: $grain, in: 0.0...1.0)
                }

                Spacer()
            }
            .padding(24)
            .navigationTitle("Preset Tune Controls")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .font(.body.bold())
                }
            }
        }
    }
}

#if canImport(UIKit)
struct SwiftUIImagePicker: UIViewControllerRepresentable {
    var sourceType: UIImagePickerController.SourceType = .camera
    var onImagePicked: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        if UIImagePickerController.isSourceTypeAvailable(sourceType) {
            picker.sourceType = sourceType
        } else {
            picker.sourceType = .photoLibrary
        }
        picker.delegate = context.coordinator
        picker.allowsEditing = true
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: SwiftUIImagePicker

        init(_ parent: SwiftUIImagePicker) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            let image = (info[.editedImage] as? UIImage) ?? (info[.originalImage] as? UIImage)
            parent.onImagePicked(image)
            picker.dismiss(animated: true)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onImagePicked(nil)
            picker.dismiss(animated: true)
        }
    }
}

func saveImageToTmp(_ image: UIImage) -> String? {
    guard let data = image.jpegData(compressionQuality: 0.85) else { return nil }
    let filename = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
    do {
        try data.write(to: filename)
        return filename.absoluteString
    } catch {
        return nil
    }
}

struct MoldFramePreferenceKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        let next = nextValue()
        if next != .zero {
            value = next
        }
    }
}
#endif
