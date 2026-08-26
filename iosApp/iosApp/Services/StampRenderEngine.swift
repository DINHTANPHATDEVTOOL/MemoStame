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

            let s = shape.lowercased()
            let themeColor: UIColor
            if s.contains("gold") || s.contains("royal") {
                themeColor = UIColor(red: 0.82, green: 0.65, blue: 0.35, alpha: 1.0)
            } else if s.contains("airmail") || s.contains("postmark") {
                themeColor = UIColor(red: 0.18, green: 0.35, blue: 0.58, alpha: 1.0)
            } else if s.contains("heart") || s.contains("love") {
                themeColor = UIColor(red: 0.90, green: 0.30, blue: 0.45, alpha: 1.0)
            } else if s.contains("vintage") || s.contains("35mm") {
                themeColor = UIColor(red: 0.75, green: 0.45, blue: 0.25, alpha: 1.0)
            } else {
                themeColor = UIColor(red: 0.85, green: 0.25, blue: 0.20, alpha: 1.0)
            }

            // 1. Calculate perforated stamp path
            let shapeObj = PerforatedStampShape(notchRatio: 0.025, spacingRatio: 0.072)
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

            // 4. Draw Top & Bottom Gradient Overlays for Readability
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let topColors = [UIColor.black.withAlphaComponent(0.65).cgColor, UIColor.clear.cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: colorSpace, colors: topColors, locations: [0.0, 1.0]) {
                cgContext.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: 140), options: [])
            }

            let bottomColors = [UIColor.clear.cgColor, UIColor.black.withAlphaComponent(0.70).cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: colorSpace, colors: bottomColors, locations: [0.0, 1.0]) {
                cgContext.drawLinearGradient(gradient, start: CGPoint(x: 0, y: targetSize.height - 180), end: CGPoint(x: 0, y: targetSize.height), options: [])
            }

            // 5. Draw Header Badges (Location Pill & Date Pill)
            let badgeFont = UIFont.monospacedSystemFont(ofSize: 18, weight: .bold)
            let pillBgColor = UIColor.black.withAlphaComponent(0.45)

            if let loc = location, !loc.isEmpty {
                let locText = "📍 \(loc.uppercased())"
                let locAttr: [NSAttributedString.Key: Any] = [
                    .font: badgeFont,
                    .foregroundColor: UIColor.white
                ]
                let locSize = (locText as NSString).size(withAttributes: locAttr)
                let locPillRect = CGRect(x: 32, y: 32, width: locSize.width + 24, height: locSize.height + 12)
                let locPillPath = UIBezierPath(roundedRect: locPillRect, cornerRadius: 10)
                pillBgColor.setFill()
                locPillPath.fill()
                (locText as NSString).draw(at: CGPoint(x: 44, y: 38), withAttributes: locAttr)
            }

            let dateAttr: [NSAttributedString.Key: Any] = [
                .font: badgeFont,
                .foregroundColor: UIColor.white.withAlphaComponent(0.9)
            ]
            let dateSize = (dateStr as NSString).size(withAttributes: dateAttr)
            let datePillRect = CGRect(x: targetSize.width - dateSize.width - 56, y: 32, width: dateSize.width + 24, height: dateSize.height + 12)
            let datePillPath = UIBezierPath(roundedRect: datePillRect, cornerRadius: 10)
            pillBgColor.setFill()
            datePillPath.fill()
            (dateStr as NSString).draw(at: CGPoint(x: targetSize.width - dateSize.width - 44, y: 38), withAttributes: dateAttr)

            // 6. Draw Circular Postmark Cancellation Seal
            cgContext.saveGState()
            let postmarkCenter = CGPoint(x: targetSize.width - 110, y: 160)
            cgContext.translateBy(x: postmarkCenter.x, y: postmarkCenter.y)
            cgContext.rotate(by: -15 * .pi / 180.0)

            let outerCircle = UIBezierPath(arcCenter: .zero, radius: 52, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            UIColor.white.withAlphaComponent(0.85).setStroke()
            outerCircle.lineWidth = 2.5
            outerCircle.stroke()

            let innerCircle = UIBezierPath(arcCenter: .zero, radius: 42, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            UIColor.white.withAlphaComponent(0.50).setStroke()
            innerCircle.lineWidth = 1.5
            innerCircle.stroke()

            let pmHeaderAttr: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 12, weight: .black), .foregroundColor: UIColor.white.withAlphaComponent(0.95)]
            let pmCenterAttr: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10, weight: .bold), .foregroundColor: UIColor.white.withAlphaComponent(0.95)]
            let pmFooterAttr: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 11, weight: .black), .foregroundColor: UIColor.white.withAlphaComponent(0.95)]

            let pmCenterText = s.contains("royal") ? "★ ROYAL ★" : (s.contains("heart") ? "♥ LOVE ♥" : "★ AIR ★")

            ("MEMO" as NSString).draw(at: CGPoint(x: -18, y: -26), withAttributes: pmHeaderAttr)
            (pmCenterText as NSString).draw(at: CGPoint(x: -24, y: -6), withAttributes: pmCenterAttr)
            ("POST" as NSString).draw(at: CGPoint(x: -16, y: 14), withAttributes: pmFooterAttr)

            cgContext.restoreGState()

            // 7. Draw Footer: Title, Subtitle, and Denomination Badge
            let titleFont = UIFont.italicSystemFont(ofSize: 28)
            let titleAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 28),
                .foregroundColor: UIColor.white
            ]
            let effectiveTitle = title.isEmpty ? "Untitled Memory" : title
            (effectiveTitle as NSString).draw(at: CGPoint(x: 32, y: targetSize.height - 95), withAttributes: titleAttr)

            let subtitleAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedSystemFont(ofSize: 14, weight: .bold),
                .foregroundColor: UIColor(red: 0.85, green: 0.75, blue: 0.65, alpha: 1.0)
            ]
            ("OFFICIAL DIE-CUT STAMP" as NSString).draw(at: CGPoint(x: 32, y: targetSize.height - 58), withAttributes: subtitleAttr)

            // Vintage Denomination Badge ("₫ 2026")
            let denomText = "₫ 2026"
            let denomAttr: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedSystemFont(ofSize: 20, weight: .heavy),
                .foregroundColor: UIColor.white
            ]
            let denomSize = (denomText as NSString).size(withAttributes: denomAttr)
            let denomRect = CGRect(x: targetSize.width - denomSize.width - 56, y: targetSize.height - 82, width: denomSize.width + 24, height: denomSize.height + 12)
            let denomPath = UIBezierPath(roundedRect: denomRect, cornerRadius: 6)
            themeColor.setFill()
            denomPath.fill()
            (denomText as NSString).draw(at: CGPoint(x: targetSize.width - denomSize.width - 44, y: targetSize.height - 76), withAttributes: denomAttr)

            // 8. Draw Perforated White Stroke Border
            cgContext.addPath(path)
            cgContext.setLineWidth(4.0)
            cgContext.setStrokeColor(themeColor.withAlphaComponent(0.85).cgColor)
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
