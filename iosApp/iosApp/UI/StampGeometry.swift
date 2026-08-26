import Foundation
import CoreGraphics

/// Shared Stamp Geometry specs matching Android StampGeometry 1:1.
public enum StampGeometry {
    public static let outputWidth: CGFloat = 1200
    public static let outputHeight: CGFloat = 1500

    public static let aspectRatio: CGFloat = 4.0 / 5.0 // 0.8

    // Perforation tooth & notch ratios
    public static let notchRadiusRatio: CGFloat = 0.025
    public static let notchSpacingRatio: CGFloat = 0.072

    // Standard stamp mold aspect ratio & width scale
    public static let moldWidthRatio: CGFloat = 0.72
    public static let moldAspectRatio: CGFloat = 1159.0 / 881.0 // ~1.3155

    // Inner photo window ratios
    public static let innerLeftRatio: CGFloat = 228.0 / 881.0
    public static let innerTopRatio: CGFloat = 316.125 / 1159.0
    public static let innerRightRatio: CGFloat = 651.0 / 881.0
    public static let innerBottomRatio: CGFloat = 844.875 / 1159.0
}
