import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Authentic postage stamp scallop perforation shape matching Android PerforatedStampShape
struct PerforatedStampShape: Shape {
    var notchRatio: CGFloat = 0.025
    var spacingRatio: CGFloat = 0.072

    func path(in rect: CGRect) -> Path {
        let w = rect.width
        let h = rect.height
        let minDim = min(w, h)
        let r = minDim * notchRatio
        let spacing = minDim * spacingRatio

        var path = Path()

        // TOP Edge (inward notches)
        path.move(to: CGPoint(x: 0, y: 0))
        var x = spacing / 2.0
        while x < w - spacing / 2.0 {
            path.addLine(to: CGPoint(x: x - r, y: 0))
            path.addQuadCurve(to: CGPoint(x: x + r, y: 0), control: CGPoint(x: x, y: r * 1.8))
            x += spacing
        }
        path.addLine(to: CGPoint(x: w, y: 0))

        // RIGHT Edge
        var y = spacing / 2.0
        while y < h - spacing / 2.0 {
            path.addLine(to: CGPoint(x: w, y: y - r))
            path.addQuadCurve(to: CGPoint(x: w, y: y + r), control: CGPoint(x: w - r * 1.8, y: y))
            y += spacing
        }
        path.addLine(to: CGPoint(x: w, y: h))

        // BOTTOM Edge
        x = w - spacing / 2.0
        while x > spacing / 2.0 {
            path.addLine(to: CGPoint(x: x + r, y: h))
            path.addQuadCurve(to: CGPoint(x: x - r, y: h), control: CGPoint(x: x, y: h - r * 1.8))
            x -= spacing
        }
        path.addLine(to: CGPoint(x: 0, y: h))

        // LEFT Edge
        y = h - spacing / 2.0
        while y > spacing / 2.0 {
            path.addLine(to: CGPoint(x: 0, y: y + r))
            path.addQuadCurve(to: CGPoint(x: 0, y: y - r), control: CGPoint(x: r * 1.8, y: y))
            y -= spacing
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
            return effectiveLandscape ? 120 : 200
        } else {
            return effectiveLandscape ? 230 : 350
        }
    }

    var body: some View {
        ZStack {
            if !isFlipped {
                // FRONT SIDE: Authentic Die-Cut Photo Stamp (NO EXTRA OUTER PAPER BORDER!)
                ZStack {
                    // Layer 1: Captured Photo filling 100% of the stamp viewport
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
                                    Image(systemName: "photo.artframe")
                                        .font(.system(size: fittedInGrid ? 18 : 28))
                                        .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
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

                    // Layer 2: Subtle Top/Bottom Gradient Shadows for High-Contrast Overlay Legibility
                    VStack {
                        LinearGradient(colors: [Color.black.opacity(0.65), Color.clear], startPoint: .top, endPoint: .bottom)
                            .frame(height: fittedInGrid ? 32 : 55)
                        Spacer()
                        LinearGradient(colors: [Color.clear, Color.black.opacity(0.70)], startPoint: .top, endPoint: .bottom)
                            .frame(height: fittedInGrid ? 40 : 65)
                    }
                    .allowsHitTesting(false)

                    // Layer 3: Stamp Information & Postmark Overlays directly ON top of photo
                    VStack {
                        // Stamp Header: Location & Date Badges
                        HStack {
                            if let loc = location, !loc.isEmpty {
                                HStack(spacing: 3) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.system(size: fittedInGrid ? 8 : 10))
                                        .foregroundColor(Color(red: 0.95, green: 0.35, blue: 0.30))
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
                            Text(dateStr)
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
                                        Text("★ AIR ★")
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
                            .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                            .foregroundColor(.white)
                            .cornerRadius(3)
                        }
                        .padding(.horizontal, fittedInGrid ? 8 : 12)
                        .padding(.bottom, fittedInGrid ? 8 : 12)
                    }

                    // Authentic Metal Mold Overlay (Camera / Editor preview mode)
                    if showMoldOverlay {
                        Image("khuon_tem_template")
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .allowsHitTesting(false)
                    }
                }
                .frame(width: cardWidth, height: cardHeight)
                .clipShape(PerforatedStampShape(notchRatio: 0.024, spacingRatio: 0.068))
                .overlay(
                    PerforatedStampShape(notchRatio: 0.024, spacingRatio: 0.068)
                        .stroke(Color.white.opacity(0.85), lineWidth: 1.5)
                )
                .shadow(color: Color.black.opacity(0.25), radius: fittedInGrid ? 4 : 8, x: 0, y: fittedInGrid ? 2 : 4)
            } else {
                // BACK SIDE: Postcard Card matching EXACT FRONT ORIENTATION & ASPECT RATIO
                VStack(spacing: fittedInGrid ? 4 : (effectiveLandscape ? 6 : 10)) {
                    // Airmail Postcard Header
                    HStack {
                        Image(systemName: "envelope.badge.fill")
                            .foregroundColor(MSColors.stamp)
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

                    // Central Postmark Seal
                    VStack(spacing: 2) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: fittedInGrid ? 16 : (effectiveLandscape ? 20 : 26)))
                            .foregroundColor(MSColors.stamp)
                        Text("MỘC BƯU CHÍNH")
                            .font(.system(size: fittedInGrid ? 9 : 12, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.ink)
                        Text(dateStr)
                            .font(.system(size: fittedInGrid ? 7 : 9, weight: .semibold, design: .monospaced))
                            .foregroundColor(MSColors.grey)
                    }
                    .padding(.top, 2)

                    Divider()

                    // Memory Quote Content
                    let memoryContent: String = {
                        if let n = note, !n.isEmpty { return n }
                        if !title.isEmpty { return title }
                        return "Một buổi sáng nhiều mây tuyệt đẹp tại Hồ Xuân Hương."
                    }()

                    Text("“" + memoryContent + "”")
                        .font(.system(size: fittedInGrid ? 9 : 12, weight: .medium, design: .serif))
                        .italic()
                        .foregroundColor(MSColors.ink)
                        .multilineTextAlignment(.center)
                        .lineLimit(fittedInGrid ? 2 : (effectiveLandscape ? 3 : 5))
                        .padding(.vertical, 2)

                    Spacer()

                    // Footer: Location & Stamp Authenticity Code
                    HStack {
                        if let loc = location {
                            Text("📍 " + loc)
                                .font(.system(size: fittedInGrid ? 7 : 9, weight: .medium))
                                .foregroundColor(MSColors.grey)
                                .lineLimit(1)
                        }
                        Spacer()
                        Text("AUTHENTIC #2026")
                            .font(.system(size: fittedInGrid ? 6 : 8, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.stamp)
                    }
                }
                .padding(fittedInGrid ? 8 : 12)
                .frame(width: cardWidth, height: cardHeight)
                .clipped()
                .background(MSColors.creamCard)
                .clipShape(PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065))
                .overlay(
                    PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065)
                        .stroke(MSColors.gold.opacity(0.8), lineWidth: 1.5)
                )
                .shadow(color: Color.black.opacity(0.12), radius: fittedInGrid ? 3 : 6, x: 0, y: fittedInGrid ? 2 : 3)
                .rotation3DEffect(.degrees(180), axis: (x: 0.0, y: 1.0, z: 0.0))
            }
        }
        .frame(width: cardWidth, height: cardHeight)
        .rotation3DEffect(
            .degrees(isFlipped ? 180 : 0),
            axis: (x: 0.0, y: 1.0, z: 0.0)
        )
        .onTapGesture(count: 2) {
            if let onDoubleTap = onDoubleTap {
                onDoubleTap()
            }
        }
        .onTapGesture(count: 1) {
            if isInteractive {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.75)) {
                    isFlipped.toggle()
                }
            }
        }
    }
}
