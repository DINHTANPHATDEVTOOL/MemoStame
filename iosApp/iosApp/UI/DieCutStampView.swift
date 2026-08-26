import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Authentic postage stamp scallop perforation shape matching Android PerforatedStampShape with mathematically centered notches.
struct PerforatedStampShape: Shape {
    var notchRatio: CGFloat = 0.025
    var spacingRatio: CGFloat = 0.072

    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        let minDim = min(w, h)
        let r = max(1.5, minDim * notchRatio)

        var path = Path()

        // TOP Edge - Center-aligned notches
        let topCount = max(3, Int((w / (minDim * spacingRatio)).rounded()))
        let topSpacing = w / CGFloat(topCount)
        path.move(to: .zero)
        for i in 0..<topCount {
            let cx = topSpacing * (CGFloat(i) + 0.5)
            path.addLine(to: CGPoint(x: cx - r, y: 0))
            path.addQuadCurve(to: CGPoint(x: cx + r, y: 0), control: CGPoint(x: cx, y: r * 1.8))
        }
        path.addLine(to: CGPoint(x: w, y: 0))

        // RIGHT Edge - Center-aligned notches
        let rightCount = max(3, Int((h / (minDim * spacingRatio)).rounded()))
        let rightSpacing = h / CGFloat(rightCount)
        for i in 0..<rightCount {
            let cy = rightSpacing * (CGFloat(i) + 0.5)
            path.addLine(to: CGPoint(x: w, y: cy - r))
            path.addQuadCurve(to: CGPoint(x: w, y: cy + r), control: CGPoint(x: w - r * 1.8, y: cy))
        }
        path.addLine(to: CGPoint(x: w, y: h))

        // BOTTOM Edge - Center-aligned notches
        for i in (0..<topCount).reversed() {
            let cx = topSpacing * (CGFloat(i) + 0.5)
            path.addLine(to: CGPoint(x: cx + r, y: h))
            path.addQuadCurve(to: CGPoint(x: cx - r, y: h), control: CGPoint(x: cx, y: h - r * 1.8))
        }
        path.addLine(to: CGPoint(x: 0, y: h))

        // LEFT Edge - Center-aligned notches
        for i in (0..<rightCount).reversed() {
            let cy = rightSpacing * (CGFloat(i) + 0.5)
            path.addLine(to: CGPoint(x: 0, y: cy + r))
            path.addQuadCurve(to: CGPoint(x: 0, y: cy - r), control: CGPoint(x: r * 1.8, y: cy))
        }
        path.addLine(to: CGPoint(x: 0, y: 0))
        path.closeSubpath()

        return path
    }
}

struct DieCutStampView: View {
    let title: String
    let imageUrl: String
    let location: String?
    let dateStr: String
    var note: String? = nil
    var shape: String = "classic"
    var isInteractive: Bool = true
    var stampColorHex: String = "#D32F2F"
    var showMoldOverlay: Bool = false
    var isLandscape: Bool = false
    var fittedInGrid: Bool = false
    var onDoubleTap: (() -> Void)? = nil
    
    @State private var isFlipped: Bool = false

    private var effectiveLandscape: Bool {
        return isLandscape || shape.lowercased().contains("landscape") || shape.lowercased().contains("horizontal")
    }

    // Card dimensions based on orientation & grid fitting
    private var cardWidth: CGFloat {
        if fittedInGrid {
            return effectiveLandscape ? 165 : 155
        } else {
            return effectiveLandscape ? 330 : 270
        }
    }

    private var cardHeight: CGFloat {
        if fittedInGrid {
            return effectiveLandscape ? 120 : 193.75
        } else {
            return effectiveLandscape ? 230 : 337.5
        }
    }

    private var effectiveDateStr: String {
        if dateStr.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy.MM.dd"
            return formatter.string(from: Date())
        }
        return dateStr
    }

    // Dynamic Mold Overlay asset switching based on selected shape
    private var moldAssetImageName: String {
        return "stamp_press_mold"
    }

    private var shapeThemeColor: Color {
        let s = shape.lowercased()
        if s.contains("gold") || s.contains("royal") {
            return Color(red: 0.82, green: 0.65, blue: 0.35)
        } else if s.contains("airmail") || s.contains("postmark") {
            return Color(red: 0.18, green: 0.35, blue: 0.58)
        } else if s.contains("heart") || s.contains("love") {
            return Color(red: 0.90, green: 0.30, blue: 0.45)
        } else if s.contains("vintage") || s.contains("35mm") {
            return Color(red: 0.75, green: 0.45, blue: 0.25)
        }
        return StampRenderEngine.shared.colorFromHex(stampColorHex)
    }

    var body: some View {
        ZStack {
            // LAYER 1: STAMP CONTENT (Clipped strictly inside PerforatedStampShape)
            ZStack {
                if !isFlipped {
                    // FRONT SIDE: Authentic Die-Cut Photo Stamp
                    ZStack {
                        // Layer 1.1: Captured Photo filling 100% of the stamp viewport
                        AsyncImage(url: URL(string: imageUrl)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            case .failure(_), .empty:
                                ZStack {
                                    Color(red: 0.94, green: 0.91, blue: 0.84)
                                    VStack(spacing: 4) {
                                        Image(systemName: shape.contains("heart") ? "heart.fill" : (shape.contains("royal") ? "crown.fill" : "photo.artframe"))
                                            .font(.system(size: fittedInGrid ? 18 : 28))
                                            .foregroundColor(shapeThemeColor)
                                        Text("MEMOSTAMP")
                                            .font(.system(size: fittedInGrid ? 6 : 8, weight: .bold, design: .monospaced))
                                            .foregroundColor(Color(red: 0.65, green: 0.55, blue: 0.45))
                                    }
                                }
                            @unknown default:
                                Color.gray.opacity(0.2)
                            }
                        }
                        .frame(width: cardWidth, height: cardHeight)

                        // Layer 1.2: Top/Bottom Gradient Shadows for Contrast
                        VStack {
                            LinearGradient(colors: [Color.black.opacity(0.65), Color.clear], startPoint: .top, endPoint: .bottom)
                                .frame(height: fittedInGrid ? 32 : 55)
                            Spacer()
                            LinearGradient(colors: [Color.clear, Color.black.opacity(0.70)], startPoint: .top, endPoint: .bottom)
                                .frame(height: fittedInGrid ? 40 : 65)
                        }
                        .allowsHitTesting(false)

                        // Layer 1.3: Stamp Information & Postmark Overlays
                        VStack {
                            // Stamp Header: Location & Date Badges
                            HStack {
                                if let loc = location, !loc.isEmpty {
                                    HStack(spacing: 3) {
                                        Image(systemName: "mappin.circle.fill")
                                            .font(.system(size: fittedInGrid ? 8 : 10))
                                            .foregroundColor(shapeThemeColor)
                                        Text(loc.uppercased())
                                            .font(.system(size: fittedInGrid ? 7 : 9, weight: .bold, design: .monospaced))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                    }
                                    .padding(.horizontal, fittedInGrid ? 6 : 8)
                                    .padding(.vertical, fittedInGrid ? 3 : 4)
                                    .background(Color.black.opacity(0.45))
                                    .cornerRadius(fittedInGrid ? 6 : 8)
                                }
                                Spacer()
                                Text(effectiveDateStr)
                                    .font(.system(size: fittedInGrid ? 7 : 9, weight: .semibold, design: .monospaced))
                                    .foregroundColor(.white.opacity(0.9))
                                    .padding(.horizontal, fittedInGrid ? 6 : 8)
                                    .padding(.vertical, fittedInGrid ? 3 : 4)
                                    .background(Color.black.opacity(0.45))
                                    .cornerRadius(fittedInGrid ? 6 : 8)
                            }
                            .padding(.horizontal, fittedInGrid ? 8 : 12)
                            .padding(.top, fittedInGrid ? 8 : 12)

                            Spacer()

                            // Postmark Cancel Stamp (Corner Overlay)
                            if !fittedInGrid {
                                HStack {
                                    Spacer()
                                    ZStack {
                                        Circle()
                                            .stroke(Color.white.opacity(0.85), lineWidth: 1.5)
                                            .frame(width: 42, height: 42)
                                        Circle()
                                            .stroke(Color.white.opacity(0.5), lineWidth: 1.0)
                                            .frame(width: 34, height: 34)
                                        VStack(spacing: 0) {
                                            Text("MEMO")
                                                .font(.system(size: 7, weight: .black))
                                            Text(shape.contains("royal") ? "★ ROYAL ★" : (shape.contains("heart") ? "♥ LOVE ♥" : "★ AIR ★"))
                                                .font(.system(size: 5, weight: .bold))
                                            Text("POST")
                                                .font(.system(size: 6, weight: .black))
                                        }
                                        .foregroundColor(Color.white.opacity(0.95))
                                    }
                                    .rotationEffect(.degrees(-15))
                                    .padding(.trailing, 10)
                                }
                            }

                            // Stamp Footer: Title & Value Denomination Badge
                            HStack(alignment: .center) {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(title.isEmpty ? "Untitled Memory" : title)
                                        .font(.system(size: fittedInGrid ? 9 : 12, weight: .bold, design: .serif))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                    Text("OFFICIAL DIE-CUT STAMP")
                                        .font(.system(size: fittedInGrid ? 5 : 7, weight: .bold, design: .monospaced))
                                        .foregroundColor(Color(red: 0.85, green: 0.75, blue: 0.65))
                                }
                                Spacer()

                                // Vintage Value Denomination Badge
                                HStack(spacing: 1) {
                                    Text("₫")
                                        .font(.system(size: fittedInGrid ? 7 : 9, weight: .bold))
                                    Text("2026")
                                        .font(.system(size: fittedInGrid ? 8 : 10, weight: .heavy, design: .monospaced))
                                }
                                .padding(.horizontal, fittedInGrid ? 4 : 6)
                                .padding(.vertical, 2)
                                .background(shapeThemeColor)
                                .foregroundColor(.white)
                                .cornerRadius(3)
                            }
                            .padding(.horizontal, fittedInGrid ? 8 : 12)
                            .padding(.bottom, fittedInGrid ? 8 : 12)
                        }
                    }
                } else {
                    // BACK SIDE: Postcard Card matching EXACT FRONT ORIENTATION & ASPECT RATIO
                    VStack(spacing: fittedInGrid ? 4 : (effectiveLandscape ? 6 : 10)) {
                        HStack {
                            Image(systemName: "envelope.badge.fill")
                                .foregroundColor(shapeThemeColor)
                                .font(.system(size: fittedInGrid ? 8 : 10))
                            Text("MEMOSTAMP AIRMAIL POSTCARD")
                                .font(.system(size: fittedInGrid ? 7 : 9, weight: .bold, design: .monospaced))
                                .foregroundColor(MSColors.grey)
                            Spacer()
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: fittedInGrid ? 8 : 10))
                                .foregroundColor(MSColors.grey)
                        }

                        Divider()

                        VStack(spacing: 2) {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.system(size: fittedInGrid ? 16 : (effectiveLandscape ? 20 : 26)))
                                .foregroundColor(shapeThemeColor)
                            Text("MỘC BƯU CHÍNH")
                                .font(.system(size: fittedInGrid ? 9 : 12, weight: .bold, design: .monospaced))
                                .foregroundColor(MSColors.ink)
                            Text(dateStr)
                                .font(.system(size: fittedInGrid ? 7 : 9, weight: .semibold, design: .monospaced))
                                .foregroundColor(MSColors.grey)
                        }

                        if let noteText = note, !noteText.isEmpty {
                            Text(noteText)
                                .font(.system(size: fittedInGrid ? 8 : 10, weight: .medium, design: .serif))
                                .foregroundColor(MSColors.ink)
                                .italic()
                                .lineLimit(fittedInGrid ? 2 : 4)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 8)
                        }

                        Spacer()
                    }
                    .padding(fittedInGrid ? 8 : 14)
                    .background(MSColors.paper)
                }
            }
            .frame(width: cardWidth, height: cardHeight)
            .clipShape(PerforatedStampShape())
            .overlay(
                PerforatedStampShape()
                    .stroke(shapeThemeColor.opacity(0.85), lineWidth: 1.5)
            )
            .shadow(color: Color.black.opacity(0.25), radius: fittedInGrid ? 4 : 8, x: 0, y: fittedInGrid ? 2 : 4)
            .onTapGesture {
                if isInteractive {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                        isFlipped.toggle()
                    }
                }
            }

            // LAYER 2: CAMERA / EDITOR MOLD OVERLAY (Placed OUTSIDE clipShape & scaled to fit)
            if showMoldOverlay {
                ZStack {
                    Image(moldAssetImageName)
                        .resizable()
                        .scaledToFit()
                        .frame(width: cardWidth * 1.35, height: cardHeight * 1.35)

                    // Template-specific visual accents over the metal mold
                    let s = shape.lowercased()
                    if s.contains("vintage") || s.contains("35mm") {
                        // Film 35mm Sprocket Holes Side Accents
                        HStack {
                            VStack(spacing: 8) {
                                ForEach(0..<8, id: \.self) { _ in
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(Color.white.opacity(0.8))
                                        .frame(width: 6, height: 9)
                                }
                            }
                            Spacer()
                            VStack(spacing: 8) {
                                ForEach(0..<8, id: \.self) { _ in
                                    RoundedRectangle(cornerRadius: 2)
                                        .fill(Color.white.opacity(0.8))
                                        .frame(width: 6, height: 9)
                                }
                            }
                        }
                        .frame(width: cardWidth * 1.15, height: cardHeight * 0.95)
                    } else if s.contains("royal") || s.contains("gold") {
                        // Royal Gold Crown filigree corner accents
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(red: 0.82, green: 0.65, blue: 0.35), lineWidth: 2.5)
                                .frame(width: cardWidth * 1.08, height: cardHeight * 1.08)
                            Image(systemName: "crown.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                                .offset(y: -cardHeight * 0.56)
                        }
                    } else if s.contains("heart") || s.contains("love") {
                        // Heart Love corner accents
                        ZStack {
                            HStack {
                                Image(systemName: "heart.fill").foregroundColor(Color(red: 0.90, green: 0.30, blue: 0.45)).font(.system(size: 14))
                                Spacer()
                                Image(systemName: "heart.fill").foregroundColor(Color(red: 0.90, green: 0.30, blue: 0.45)).font(.system(size: 14))
                            }
                            .frame(width: cardWidth * 1.12)
                            .offset(y: -cardHeight * 0.54)
                        }
                    }
                }
                .allowsHitTesting(false)
            }
        }
    }
}
