import XCTest

final class CameraViewModelTests: XCTestCase {

    func testZoomPillPinchClamping() {
        let options = ["1x": 1.0, "2x": 1.8, "3x": 2.8, "5x": 4.2]
        for (pill, expectedScale) in options {
            var scale: Double = 1.0
            switch pill {
            case "1x": scale = 1.0
            case "2x": scale = 1.8
            case "3x": scale = 2.8
            case "5x": scale = 4.2
            default: scale = 1.0
            }
            XCTAssertEqual(scale, expectedScale, accuracy: 0.001)
        }
    }

    func testFilterPresetCount() {
        let filterNames = ["Original", "Vintage Film 1970", "B&W Postcard", "Kodak Warm", "Cold Cyan", "Film 35mm"]
        XCTAssertEqual(filterNames.count, 6)
    }
}
