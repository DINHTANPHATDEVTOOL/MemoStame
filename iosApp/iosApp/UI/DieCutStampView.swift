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
    var showMoldOverlay: Bool = true
    var onDoubleTap: (() -> Void)? = nil
    
    @State private var isFlipped: Bool = false

    var body: some View {
        ZStack {
            if !isFlipped {
                // Front Side: Die-Cut Stamp with Authentic Perforated Edge & Mold Frame
                ZStack {
                    // Vintage paper base
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color(red: 0.98, green: 0.96, blue: 0.92))
                    
                    VStack(spacing: 6) {
                        // Stamp Header / Location
                        HStack {
                            if let loc = location, !loc.isEmpty {
                                HStack(spacing: 4) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                    Text(loc.uppercased())
                                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                                        .foregroundColor(Color(red: 0.35, green: 0.30, blue: 0.25))
                                        .lineLimit(1)
                                }
                            }
                            Spacer()
                            Text(dateStr)
                                .font(.system(size: 10, weight: .semibold, design: .monospaced))
                                .foregroundColor(Color(red: 0.55, green: 0.50, blue: 0.45))
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 10)

                        // Central Photo in Die-Cut Perforated Window
                        ZStack {
                            AsyncImage(url: URL(string: imageUrl)) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                case .failure(_), .empty:
                                    ZStack {
                                        Color(red: 0.94, green: 0.91, blue: 0.84)
                                        VStack(spacing: 6) {
                                            Image(systemName: "photo.artframe")
                                                .font(.system(size: 32))
                                                .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                                            Text("MEMOSTAMP")
                                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                                .foregroundColor(Color(red: 0.65, green: 0.55, blue: 0.45))
                                        }
                                    }
                                @unknown default:
                                    Color.gray.opacity(0.2)
                                }
                            }
                            .frame(height: 180)
                            .cornerRadius(6)
                            .overlay(
                                RoundedRectangle(cornerRadius: 6)
                                    .stroke(Color(red: 0.85, green: 0.80, blue: 0.70), lineWidth: 1.5)
                            )
                            .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)

                            // Mold overlay strictly constrained to image box
                            if showMoldOverlay {
                                Image("stamp_press_mold")
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(height: 180)
                                    .cornerRadius(6)
                                    .opacity(0.15)
                                    .allowsHitTesting(false)
                            }

                            // Postmark Cancel Stamp
                            VStack {
                                HStack {
                                    Spacer()
                                    ZStack {
                                        Circle()
                                            .stroke(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.75), lineWidth: 1.5)
                                            .frame(width: 44, height: 44)
                                        Circle()
                                            .stroke(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.5), lineWidth: 1.0)
                                            .frame(width: 36, height: 36)
                                        VStack(spacing: 0) {
                                            Text("MEMO")
                                                .font(.system(size: 8, weight: .black))
                                            Text("★ AIR ★")
                                                .font(.system(size: 6, weight: .bold))
                                            Text("POST")
                                                .font(.system(size: 7, weight: .black))
                                        }
                                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.8))
                                    }
                                    .rotationEffect(.degrees(-15))
                                    .padding(8)
                                }
                                Spacer()
                            }
                        }
                        .frame(height: 180)
                        .clipped()
                        .padding(.horizontal, 10)

                        // Stamp Footer Information
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(title.isEmpty ? "Untitled Memory" : title)
                                    .font(.system(size: 13, weight: .bold, design: .serif))
                                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                                    .lineLimit(1)
                                Text("OFFICIAL DIE-CUT STAMP")
                                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                                    .foregroundColor(Color(red: 0.65, green: 0.55, blue: 0.45))
                            }
                            Spacer()

                            // Vintage Value / Denomination badge
                            HStack(spacing: 2) {
                                Text("₫")
                                    .font(.system(size: 10, weight: .bold))
                                Text("2026")
                                    .font(.system(size: 11, weight: .heavy, design: .monospaced))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.12))
                            .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                            .cornerRadius(6)
                        }
                        .padding(.horizontal, 14)
                        .padding(.bottom, 10)
                    }
                }
                .frame(height: 270)
                .clipped()
                .clipShape(PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065))
                .overlay(
                    ZStack {
                        PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065)
                            .stroke(Color(red: 0.80, green: 0.74, blue: 0.65), lineWidth: 1.5)
                        Image("stamp_press_mold")
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .opacity(0.12)
                            .allowsHitTesting(false)
                    }
                )
                .shadow(color: Color.black.opacity(0.15), radius: 8, x: 0, y: 4)
            } else {
                // Back Side: Vintage Postcard with Notes & Airmail Stamp (Counter-rotated for 3D flip)
                VStack(spacing: 12) {
                    HStack {
                        Image(systemName: "envelope.badge.fill")
                            .foregroundColor(MSColors.stamp)
                        Text("MEMOSTAMP AIRMAIL POSTCARD")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.grey)
                        Spacer()
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.caption)
                            .foregroundColor(MSColors.grey)
                    }

                    Divider()

                    VStack(spacing: 6) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 32))
                            .foregroundColor(MSColors.stamp)
                        Text("MỘC BƯU CHÍNH")
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.ink)
                        Text(dateStr)
                            .font(.system(size: 11, weight: .semibold, design: .monospaced))
                            .foregroundColor(MSColors.grey)
                    }
                    .padding(.top, 4)

                    Divider()

                    let memoryContent: String = {
                        if let n = note, !n.isEmpty { return n }
                        if !title.isEmpty { return title }
                        return "Một buổi sáng nhiều mây tuyệt đẹp tại Hồ Xuân Hương."
                    }()

                    Text("“" + memoryContent + "”")
                        .font(.system(size: 14, weight: .medium, design: .serif))
                        .italic()
                        .foregroundColor(MSColors.ink)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, 4)

                    Spacer()

                    HStack {
                        if let loc = location {
                            Text("📍 " + loc)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(MSColors.grey)
                        }
                        Spacer()
                        Text("AUTHENTIC MEMOSTAMP #2026")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(MSColors.stamp)
                    }
                }
                .padding(16)
                .frame(height: 270)
                .clipped()
                .background(MSColors.creamCard)
                .clipShape(PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065))
                .overlay(
                    PerforatedStampShape(notchRatio: 0.022, spacingRatio: 0.065)
                        .stroke(MSColors.gold.opacity(0.8), lineWidth: 1.5)
                )
                .shadow(color: Color.black.opacity(0.12), radius: 6, x: 0, y: 3)
                .rotation3DEffect(.degrees(180), axis: (x: 0.0, y: 1.0, z: 0.0))
            }
        }
        .frame(height: 270)
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

