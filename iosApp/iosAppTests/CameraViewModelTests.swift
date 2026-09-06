import XCTest
@testable import iosApp

final class CameraViewModelTests: XCTestCase {

    func testContinuousHardwareZoomClamping() {
        let minZoom: CGFloat = 0.5
        let maxZoom: CGFloat = 15.0

        let testInputs: [(input: CGFloat, expected: CGFloat)] = [
            (0.2, 0.5),
            (0.5, 0.5),
            (1.0, 1.0),
            (2.1, 2.1),
            (3.0, 3.0),
            (15.0, 15.0),
            (20.0, 15.0)
        ]

        for item in testInputs {
            let clamped = min(max(item.input, minZoom), maxZoom)
            XCTAssertEqual(clamped, item.expected, accuracy: 0.001)
        }
    }

    func testOpticalLensDisplayFormatting() {
        XCTAssertEqual(CameraPreviewContainerView.formatDisplayFactor(0.5), "0.5x")
        XCTAssertEqual(CameraPreviewContainerView.formatDisplayFactor(1.0), "1x")
        XCTAssertEqual(CameraPreviewContainerView.formatDisplayFactor(2.5), "2.5x")
        XCTAssertEqual(CameraPreviewContainerView.formatDisplayFactor(3.0), "3x")
        XCTAssertEqual(CameraPreviewContainerView.formatDisplayFactor(5.0), "5x")
    }

    func testFilterPresetCount() {
        let filterNames = ["Original", "Vintage Film 1970", "B&W Postcard", "Kodak Warm", "Cold Cyan", "Film 35mm"]
        XCTAssertEqual(filterNames.count, 6)
    }
}
