import SwiftUI
import CoreGraphics

/// Native Swift Stamp Rendering Engine calculating perforation geometry, notch ratios, and die-cut paths.
public final class StampRenderEngine {
    public static let shared = StampRenderEngine()

    private init() {}

    public struct StampNotchSpecs {
        public let notchRadius: CGFloat
        public let spacing: CGFloat
        public let notchCountHorizontal: Int
        public let notchCountVertical: Int
    }

    public func calculateNotchSpecs(for size: CGSize, notchRatio: CGFloat = 0.025, spacingRatio: CGFloat = 0.07) -> StampNotchSpecs {
        let minDim = min(size.width, size.height)
        let radius = minDim * notchRatio
        let spacing = minDim * spacingRatio

        let countH = max(3, Int((size.width - radius * 2) / (spacing + radius * 2)))
        let countV = max(3, Int((size.height - radius * 2) / (spacing + radius * 2)))

        return StampNotchSpecs(
            notchRadius: radius,
            spacing: spacing,
            notchCountHorizontal: countH,
            notchCountVertical: countV
        )
    }

    public func colorFromHex(_ hex: String) -> Color {
        var cleanHex = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cleanHex.hasPrefix("#") {
            cleanHex.removeFirst()
        }
        guard cleanHex.count == 6, let rgbValue = UInt64(cleanHex, radix: 16) else {
            return Color(red: 0.85, green: 0.25, blue: 0.20)
        }
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0
        return Color(red: r, green: g, blue: b)
    }
}
