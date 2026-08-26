import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct StampMoldTemplate: Identifiable {
    let id: String
    let name: String
    let iconName: String
    let subtitle: String
}

struct StampEditorScreenView: View {
    @Environment(\.presentationMode) var presentationMode

    var initialImageUrl: String? = nil
    var onStampSaved: ((URL) -> Void)? = nil
    var onContinue: ((String, String, String) -> Void)? = nil
    var onCancel: (() -> Void)? = nil

    @State private var selectedMoldId: String = "classic_perforated"
    @State private var selectedColorHex: String = "#D32F2F"
    @State private var stampTitle: String = ""
    @State private var stampLocation: String = ""
    @State private var stampDate: String = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: Date())
    }()
    @State private var showMoldOverlay: Bool = true

    let moldTemplates: [StampMoldTemplate] = [
        StampMoldTemplate(id: "classic_perforated", name: "Khuôn Bưu Chính", iconName: "seal.fill", subtitle: "Răng cưa chuẩn"),
        StampMoldTemplate(id: "vintage_press", name: "Khuôn Dập 35mm", iconName: "camera.filters", subtitle: "Film hoài niệm"),
        StampMoldTemplate(id: "postmark_airmail", name: "Air Mail Express", iconName: "airplane", subtitle: "Dấu bưu điện"),
        StampMoldTemplate(id: "royal_gold", name: "Hoàng Gia Gold", iconName: "crown.fill", subtitle: "Khung chỉ vàng"),
        StampMoldTemplate(id: "heart_love", name: "Trái Tim Yêu", iconName: "heart.fill", subtitle: "Ngọt ngào")
    ]

    let paletteColors: [Color] = [
        Color(red: 0.85, green: 0.25, blue: 0.20), // Retro Red
        Color(red: 0.82, green: 0.65, blue: 0.35), // Vintage Gold
        Color(red: 0.18, green: 0.35, blue: 0.58), // Air Force Blue
        Color(red: 0.22, green: 0.45, blue: 0.30), // Forest Pine
        Color(red: 0.20, green: 0.20, blue: 0.25)  // Noir Classic
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack(spacing: 12) {
                Button(action: {
                    if let onCancel = onCancel {
                        onCancel()
                    } else {
                        presentationMode.wrappedValue.dismiss()
                    }
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.40))
                        .padding(8)
                        .background(Color.white)
                        .clipShape(Circle())
                }

                Spacer()

                HStack(spacing: 6) {
                    Image("app_logo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 24, height: 24)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    Text("Khuôn Dập Tem")
                        .font(.headline.bold())
                        .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                }

                Spacer()

                Button(action: {
                    HapticFeedbackManager.shared.playSuccess()
                    SoundEffectsManager.shared.playStampPressSound()
                    if let onContinue = onContinue {
                        onContinue(initialImageUrl ?? "", selectedMoldId, selectedColorHex)
                    } else if let callback = onStampSaved, let urlStr = initialImageUrl, let url = URL(string: urlStr) {
                        callback(url)
                    } else {
                        presentationMode.wrappedValue.dismiss()
                    }
                }) {
                    Text("Tiếp tục")
                        .font(.subheadline.bold())
                        .foregroundColor(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                        .cornerRadius(16)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.white)

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Live Die-Cut Stamp Preview
                    VStack(spacing: 8) {
                        DieCutStampView(
                            title: stampTitle,
                            imageUrl: initialImageUrl ?? "",
                            location: stampLocation,
                            dateStr: stampDate,
                            shape: selectedMoldId,
                            isInteractive: true,
                            stampColorHex: selectedColorHex,
                            showMoldOverlay: showMoldOverlay
                        )
                        .frame(maxWidth: 320)
                        .padding(.top, 12)

                        Text("Chạm vào tem để lật xem mặt sau bưu thiếp ↺")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(red: 0.55, green: 0.50, blue: 0.45))
                    }
                    .padding(.horizontal)

                    // Controls Panel
                    VStack(alignment: .leading, spacing: 16) {
                        // Section 1: Die-Cut Mold Selector
                        VStack(alignment: .leading, spacing: 8) {
                            Text("CHỌN KHUÔN DẬP TEM (DIE-CUT MOLD)")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(.secondary)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    ForEach(moldTemplates) { mold in
                                        Button(action: {
                                            selectedMoldId = mold.id
                                        }) {
                                            VStack(alignment: .leading, spacing: 4) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: mold.iconName)
                                                        .font(.system(size: 14))
                                                    Text(mold.name)
                                                        .font(.system(size: 12, weight: .bold))
                                                }
                                                Text(mold.subtitle)
                                                    .font(.system(size: 10))
                                                    .foregroundColor(selectedMoldId == mold.id ? .white.opacity(0.85) : .secondary)
                                            }
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 10)
                                            .background(selectedMoldId == mold.id ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.white)
                                            .foregroundColor(selectedMoldId == mold.id ? .white : .primary)
                                            .cornerRadius(12)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 12)
                                                    .stroke(selectedMoldId == mold.id ? Color.clear : Color.gray.opacity(0.2), lineWidth: 1)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Stamp Ink Color
                        VStack(alignment: .leading, spacing: 8) {
                            Text("TÔNG MÀU DẤU BƯU ĐIỆN")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(.secondary)

                            HStack(spacing: 14) {
                                ForEach(0..<paletteColors.count, id: \.self) { idx in
                                    let c = paletteColors[idx]
                                    let hexes = ["#D85C4A", "#F4C95D", "#1E3A8A", "#224530", "#202025"]
                                    let hex = idx < hexes.count ? hexes[idx] : "#D85C4A"
                                    Button(action: {
                                        selectedColorHex = hex
                                    }) {
                                        Circle()
                                            .fill(c)
                                            .frame(width: 32, height: 32)
                                            .overlay(
                                                Circle()
                                                    .stroke(selectedColorHex == hex ? Color.black : Color.white, lineWidth: 2)
                                            )
                                            .shadow(color: Color.black.opacity(0.15), radius: 3)
                                    }
                                }
                                Spacer()
                                Toggle("Vân khuôn dập", isOn: $showMoldOverlay)
                                    .font(.caption.bold())
                                    .toggleStyle(SwitchToggleStyle(tint: Color(red: 0.85, green: 0.25, blue: 0.20)))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 32)
                }
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
    }
}

