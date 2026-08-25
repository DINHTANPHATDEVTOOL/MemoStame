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
                // FRONT SIDE: Perforated Postage Stamp Card
                ZStack {
                    // Vintage paper card background
                    RoundedRectangle(cornerRadius: fittedInGrid ? 4 : 6)
                        .fill(Color(red: 0.98, green: 0.96, blue: 0.92))
                    
                    VStack(spacing: fittedInGrid ? 2 : 4) {
                        // Stamp Header: Location & Date
                        HStack {
                            if let loc = location, !loc.isEmpty {
                                HStack(spacing: 3) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.system(size: fittedInGrid ? 8 : 10))
                                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                    Text(loc.uppercased())
                                        .font(.system(size: fittedInGrid ? 7 : 9, weight: .bold, design: .monospaced))
                                        .foregroundColor(Color(red: 0.35, green: 0.30, blue: 0.25))
                                        .lineLimit(1)
                                }
                            }
                            Spacer()
                            Text(dateStr)
                                .font(.system(size: fittedInGrid ? 7 : 9, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color(red: 0.55, green: 0.50, blue: 0.45))
                        }
                        .padding(.horizontal, fittedInGrid ? 8 : 12)
                        .padding(.top, fittedInGrid ? 4 : 8)

                        // Central Photo Window
                        ZStack {
                            // Photo Layer
                            AsyncImage(url: URL(string: imageUrl)) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                case .failure(_), .empty:
                                    ZStack {
                                        Color(red: 0.94, green: 0.91, blue: 0.84)
                                        VStack(spacing: 2) {
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
                            .frame(
                                width: cardWidth - (fittedInGrid ? 16 : 24),
                                height: cardHeight - (fittedInGrid ? 48 : 80)
                            )
                            .clipped()

                            // Authentic Metal Mold Overlay (Camera / Editor preview only)
                            if showMoldOverlay {
                                Image("khuon_tem_template")
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(
                                        width: cardWidth - (fittedInGrid ? 10 : 16),
                                        height: cardHeight - (fittedInGrid ? 40 : 60)
                                    )
                                    .allowsHitTesting(false)
                            }

                            // Postmark Cancel Stamp (Corner Overlay)
                            if !fittedInGrid {
                                VStack {
                                    HStack {
                                        Spacer()
                                        ZStack {
                                            Circle()
                                                .stroke(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.85), lineWidth: 1.5)
                                                .frame(width: 40, height: 40)
                                            Circle()
                                                .stroke(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.5), lineWidth: 1.0)
                                                .frame(width: 32, height: 32)
                                            VStack(spacing: 0) {
                                                Text("MEMO")
                                                    .font(.system(size: 7, weight: .black))
                                                Text("★ AIR ★")
                                                    .font(.system(size: 5, weight: .bold))
                                                Text("POST")
                                                    .font(.system(size: 6, weight: .black))
                                            }
                                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.9))
                                        }
                                        .rotationEffect(.degrees(-15))
                                        .padding(6)
                                    }
                                    Spacer()
                                }
                            }
                        }
                        .frame(
                            width: cardWidth - (fittedInGrid ? 16 : 24),
                            height: cardHeight - (fittedInGrid ? 48 : 80)
                        )
                        .clipped()

                        // Stamp Footer: Title & Value Badge
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(title.isEmpty ? "Untitled Memory" : title)
                                    .font(.system(size: fittedInGrid ? 9 : 12, weight: .bold, design: .serif))
                                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                                    .lineLimit(1)
                                Text("OFFICIAL DIE-CUT STAMP")
                                    .font(.system(size: fittedInGrid ? 5 : 7, weight: .bold, design: .monospaced))
                                    .foregroundColor(Color(red: 0.65, green: 0.55, blue: 0.45))
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
                            .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.12))
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                            .cornerRadius(3)
                        }
                        .padding(.horizontal, fittedInGrid ? 8 : 12)
                        .padding(.bottom, fittedInGrid ? 4 : 8)
                    }
                }
                .frame(width: cardWidth, height: cardHeight)
                .clipped()
                .clipShape(PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065))
                .overlay(
                    PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065)
                        .stroke(Color(red: 0.80, green: 0.74, blue: 0.65), lineWidth: 1.5)
                )
                .shadow(color: Color.black.opacity(0.15), radius: fittedInGrid ? 3 : 6, x: 0, y: fittedInGrid ? 2 : 3)
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
