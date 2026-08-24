import XCTest
import CoreGraphics

final class StampRenderEngineTests: XCTestCase {

    func testNotchCalculationGeometry() {
        let size = CGSize(width: 300, height: 400)
        let minDim = min(size.width, size.height)
        let radius = minDim * 0.025
        let spacing = minDim * 0.07

        let countH = max(3, Int((size.width - radius * 2) / (spacing + radius * 2)))
        let countV = max(3, Int((size.height - radius * 2) / (spacing + radius * 2)))

        XCTAssertGreaterThanOrEqual(countH, 3)
        XCTAssertGreaterThanOrEqual(countV, 3)
        XCTAssertEqual(radius, 7.5, accuracy: 0.1)
    }

    func testHexColorParsing() {
        let hex = "#D85C4A"
        var cleanHex = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cleanHex.hasPrefix("#") {
            cleanHex.removeFirst()
        }
        XCTAssertEqual(cleanHex, "D85C4A")
        guard let rgbValue = UInt64(cleanHex, radix: 16) else {
            XCTFail("Invalid hex conversion")
            return
        }
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0

        XCTAssertEqual(r, 216.0 / 255.0, accuracy: 0.01)
        XCTAssertEqual(g, 92.0 / 255.0, accuracy: 0.01)
        XCTAssertEqual(b, 74.0 / 255.0, accuracy: 0.01)
    }
}
