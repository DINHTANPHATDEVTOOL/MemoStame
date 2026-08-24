import SwiftUI

/// Centralized Design System Tokens for MemoStamp iOS application matching Android Material 3 specs.
public enum MSTheme {
    public enum Colors {
        public static let paperBackground = Color(red: 0.98, green: 0.96, blue: 0.92)
        public static let primaryRed = Color(red: 0.85, green: 0.25, blue: 0.20)
        public static let vintageGold = Color(red: 0.82, green: 0.65, blue: 0.35)
        public static let airforceBlue = Color(red: 0.20, green: 0.45, blue: 0.75)
        public static let textPrimary = Color(red: 0.15, green: 0.15, blue: 0.18)
        public static let textSecondary = Color(red: 0.45, green: 0.45, blue: 0.50)
        public static let cardSurface = Color.white
        public static let borderOutline = Color.gray.opacity(0.2)
    }

    public enum Typography {
        public static let titleLarge = Font.title2.bold()
        public static let titleMedium = Font.headline.bold()
        public static let bodyMedium = Font.subheadline
        public static let captionSmall = Font.caption.monospacedDigit()
        public static let stampLabel = Font.system(size: 11, weight: .bold, design: .serif)
    }

    public enum Radii {
        public static let small: CGFloat = 8
        public static let medium: CGFloat = 12
        public static let large: CGFloat = 16
        public static let stampCorner: CGFloat = 20
        public static let pill: CGFloat = 24
    }

    public enum Shadows {
        public static let card = Color.black.opacity(0.06)
        public static let stampHover = Color.black.opacity(0.15)
    }
}
