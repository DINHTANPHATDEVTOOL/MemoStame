import SwiftUI
#if canImport(UIKit)
import UIKit
import AVFoundation
#endif
import shared

struct CameraScreenView: View {
    let replyToPostId: String?
    var onNavigateToNote: (String) -> Void
    var onCancel: () -> Void

    @State private var selectedFilterIndex: Int = 5 // Film 35mm
    @State private var zoomScale: CGFloat = 1.0
    @State private var selectedZoomPill: String = "1x"
    @State private var contrast: Double = 1.0
    @State private var brightness: Double = 0.0
    @State private var saturation: Double = 1.0
    @State private var grain: Double = 0.2
    @State private var showTuneSheet: Bool = false
    @State private var flashOn: Bool = false
    @State private var isCapturing: Bool = false
    @State private var toastMessage: String? = nil
    @State private var showToast: Bool = false

    let filters = FilterPresets.shared.ALL
    let zoomOptions = ["1x", "2x", "3x", "5x"]

    let samplePhotos = [
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
        "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
        "https://images.unsplash.com/photo-1528127269322-539801943592?w=600"
    ]
    @State private var selectedImageIndex: Int = 0

    var currentFilter: CameraFilterSpec {
        if selectedFilterIndex >= 0 && selectedFilterIndex < filters.count {
            return filters[selectedFilterIndex]
        }
        return FilterPresets.shared.ORIGINAL
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Camera Controls Bar
                HStack {
                    Button(action: onCancel) {
                        Image(systemName: "xmark")
                            .font(.title2.bold())
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.white.opacity(0.15))
                            .clipShape(Circle())
                    }

                    Spacer()

                    if let replyId = replyToPostId {
                        HStack(spacing: 6) {
                            Text("📮")
                            Text("REPLYING TO POST #\(String(replyId.prefix(4)))")
                                .font(.caption.bold())
                                .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.white.opacity(0.18))
                        .cornerRadius(14)

                        Spacer()
                    }

                    Button(action: {
                        flashOn.toggle()
                        triggerHapticFeedback()
                    }) {
                        Image(systemName: flashOn ? "bolt.fill" : "bolt.slash.fill")
                            .font(.title2)
                            .foregroundColor(flashOn ? .yellow : .white)
                            .frame(width: 40, height: 40)
                            .background(Color.white.opacity(0.15))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 6)

                // Live Preview Box with Swipe & Pinch Gestures
                ZStack {
                    AsyncImage(url: URL(string: samplePhotos[selectedImageIndex])) { phase in
                        if let img = phase.image {
                            img.resizable()
                                .aspectRatio(contentMode: .fill)
                                .scaleEffect(zoomScale)
                        } else {
                            Color.gray.opacity(0.4)
                        }
                    }
                    .frame(height: 390)
                    .clipped()
                    .cornerRadius(24)
                    .overlay(
                        RoundedRectangle(cornerRadius: 24)
                            .stroke(Color(red: 0.82, green: 0.65, blue: 0.35), lineWidth: 2)
                    )

                    // Stamp Die-Cut Golden Frame Guidelines
                    PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065)
                        .stroke(Color(red: 0.82, green: 0.65, blue: 0.35), lineWidth: 2)
                        .padding(20)

                    // Stamp mold texture overlay
                    Image("stamp_press_mold")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .opacity(0.12)
                        .padding(24)
                        .allowsHitTesting(false)

                    // Filter Color Grading Overlay Tint
                    FilterOverlayView(filter: currentFilter)
                        .allowsHitTesting(false)

                    // Toast Overlay Confirmation when swiping filters
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
                .padding(.horizontal, 16)
                .gesture(
                    DragGesture(minimumDistance: 30)
                        .onEnded { value in
                            if value.translation.width < -40 {
                                // Swipe Left -> Next Filter
                                selectedFilterIndex = (selectedFilterIndex + 1) % filters.count
                                showFilterToast(currentFilter.name)
                            } else if value.translation.width > 40 {
                                // Swipe Right -> Previous Filter
                                selectedFilterIndex = (selectedFilterIndex - 1 + filters.count) % filters.count
                                showFilterToast(currentFilter.name)
                            }
                        }
                )
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            let newScale = zoomScale * value
                            zoomScale = min(max(newScale, 1.0), 5.0)
                        }
                )

                // Focal Zoom Pill Selector (1x, 2x, 3x, 5x)
                HStack(spacing: 8) {
                    ForEach(zoomOptions, id: \.self) { pill in
                        Button(action: {
                            selectedZoomPill = pill
                            triggerHapticFeedback()
                            switch pill {
                            case "1x": zoomScale = 1.0
                            case "2x": zoomScale = 1.8
                            case "3x": zoomScale = 2.8
                            case "5x": zoomScale = 4.2
                            default: zoomScale = 1.0
                            }
                        }) {
                            Text(pill)
                                .font(.caption2.bold())
                                .foregroundColor(selectedZoomPill == pill ? .black : .white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(selectedZoomPill == pill ? Color.white : Color.white.opacity(0.2))
                                .cornerRadius(14)
                        }
                    }
                }
                .padding(.vertical, 10)

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
                        Button(action: {
                            selectedImageIndex = (selectedImageIndex + 1) % samplePhotos.count
                            triggerHapticFeedback()
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

                        // Master Shutter Button
                        Button(action: {
                            triggerHapticFeedback()
                            isCapturing = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                isCapturing = false
                                onNavigateToNote(samplePhotos[selectedImageIndex])
                            }
                        }) {
                            ZStack {
                                Circle()
                                    .stroke(Color.white, lineWidth: 4)
                                    .frame(width: 76, height: 76)
                                Circle()
                                    .fill(isCapturing ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.white)
                                    .frame(width: 64, height: 64)
                            }
                        }

                        Spacer()

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
        }
        .sheet(isPresented: $showTuneSheet) {
            CameraTuneAdjustmentView(
                contrast: $contrast,
                brightness: $brightness,
                saturation: $saturation,
                grain: $grain
            )
        }
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
