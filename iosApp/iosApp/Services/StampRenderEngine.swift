import SwiftUI
import CoreGraphics
#if canImport(UIKit)
import UIKit
#endif

/// Native Swift Stamp Rendering Engine calculating perforation geometry, notch ratios, and rendering transparent PNG die-cut stamps.
final class StampRenderEngine {
    static let shared = StampRenderEngine()

    private init() {}

    struct StampNotchSpecs {
        let notchRadius: CGFloat
        let spacing: CGFloat
        let notchCountHorizontal: Int
        let notchCountVertical: Int
    }

    func calculateNotchSpecs(for size: CGSize, notchRatio: CGFloat = 0.024, spacingRatio: CGFloat = 0.068) -> StampNotchSpecs {
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

    func colorFromHex(_ hex: String) -> Color {
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

    #if canImport(UIKit)
    /// Renders a full high-fidelity transparent PNG die-cut stamp image matching WYSIWYG specs.
    @MainActor
    func renderStampToImage(
        photo: UIImage?,
        title: String,
        location: String?,
        dateStr: String,
        stampColorHex: String = "#D32F2F",
        shape: String = "classic",
        targetSize: CGSize = CGSize(width: 800, height: 1000)
    ) -> UIImage? {
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: targetSize)
            let cgContext = context.cgContext

            // 1. Calculate perforated stamp path
            let shapeObj = PerforatedStampShape(notchRatio: 0.024, spacingRatio: 0.068)
            let path = shapeObj.path(in: rect).cgPath

            // 2. Clip context to perforated stamp shape
            cgContext.addPath(path)
            cgContext.clip()

            // 3. Draw Photo filling the aperture
            if let photo = photo {
                let imgAspect = photo.size.width / photo.size.height
                let rectAspect = targetSize.width / targetSize.height
                var drawRect = rect
                if imgAspect > rectAspect {
                    let drawWidth = targetSize.height * imgAspect
                    drawRect = CGRect(x: (targetSize.width - drawWidth) / 2.0, y: 0, width: drawWidth, height: targetSize.height)
                } else {
                    let drawHeight = targetSize.width / imgAspect
                    drawRect = CGRect(x: 0, y: (targetSize.height - drawHeight) / 2.0, width: targetSize.width, height: drawHeight)
                }
                photo.draw(in: drawRect)
            } else {
                // Background Fallback Paper Color
                UIColor(red: 0.94, green: 0.91, blue: 0.84, alpha: 1.0).setFill()
                cgContext.fill(rect)
            }

            // 4. Draw Gradient Overlays for Readability
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let colors = [
                UIColor.black.withAlphaComponent(0.65).cgColor,
                UIColor.clear.cgColor
            ] as CFArray
            if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors, locations: [0.0, 1.0]) {
                cgContext.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: 120), options: [])
            }

            // 5. Draw Header Date & Location Badges
            let font = UIFont.systemFont(ofSize: 22, weight: .bold)
            let textAttributes: [NSAttributedString.Key: Any] = [
                .font: font,
                .foregroundColor: UIColor.white
            ]

            let effectiveTitle = title.isEmpty ? "Untitled Memory" : title
            let titleString = NSAttributedString(string: effectiveTitle.uppercased(), attributes: textAttributes)
            titleString.draw(at: CGPoint(x: 32, y: targetSize.height - 70))

            if let loc = location, !loc.isEmpty {
                let locAttr: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 18, weight: .semibold),
                    .foregroundColor: UIColor(red: 0.95, green: 0.85, blue: 0.70, alpha: 1.0)
                ]
                let locString = NSAttributedString(string: "📍 \(loc.uppercased()) • \(dateStr)", attributes: locAttr)
                locString.draw(at: CGPoint(x: 32, y: 32))
            }

            // 6. Draw Perforated White Stroke Border
            cgContext.addPath(path)
            cgContext.setLineWidth(4.0)
            cgContext.setStrokeColor(UIColor.white.withAlphaComponent(0.85).cgColor)
            cgContext.strokePath()
        }
    }

    /// Renders and saves a full transparent PNG stamp file to disk, returning its file URL.
    @MainActor
    func saveStampPng(
        photo: UIImage?,
        title: String,
        location: String?,
        dateStr: String,
        stampColorHex: String = "#D32F2F",
        shape: String = "classic",
        outputFileName: String = "stamp_\(Int(Date().timeIntervalSince1970)).png"
    ) -> URL? {
        guard let image = renderStampToImage(
            photo: photo,
            title: title,
            location: location,
            dateStr: dateStr,
            stampColorHex: stampColorHex,
            shape: shape
        ), let pngData = image.pngData() else {
            return nil
        }

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let stampsDir = docs.appendingPathComponent("stamps")
        try? FileManager.default.createDirectory(at: stampsDir, withIntermediateDirectories: true)

        let fileURL = stampsDir.appendingPathComponent(outputFileName)
        do {
            try pngData.write(to: fileURL)
            return fileURL
        } catch {
            print("Error saving PNG die-cut stamp: \(error)")
            return nil
        }
    }
    #endif
}
