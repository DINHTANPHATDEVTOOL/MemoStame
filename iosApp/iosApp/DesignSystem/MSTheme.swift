import SwiftUI

/// Centralized Design System Tokens for MemoStamp iOS application matching Android Material 3 specs.
enum MSTheme {
    enum Colors {
        static let paperBackground = Color(red: 0.98, green: 0.96, blue: 0.92)
        static let primaryRed = Color(red: 0.85, green: 0.25, blue: 0.20)
        static let vintageGold = Color(red: 0.82, green: 0.65, blue: 0.35)
        static let airforceBlue = Color(red: 0.20, green: 0.45, blue: 0.75)
        static let textPrimary = Color(red: 0.15, green: 0.15, blue: 0.18)
        static let textSecondary = Color(red: 0.45, green: 0.45, blue: 0.50)
        static let cardSurface = Color.white
        static let borderOutline = Color.gray.opacity(0.2)
    }

    enum Typography {
        static let titleLarge = Font.title2.bold()
        static let titleMedium = Font.headline.bold()
        static let bodyMedium = Font.subheadline
        static let captionSmall = Font.caption.monospacedDigit()
        static let stampLabel = Font.system(size: 11, weight: .bold, design: .serif)
    }

    enum Radii {
        static let small: CGFloat = 8
        static let medium: CGFloat = 12
        static let large: CGFloat = 16
        static let stampCorner: CGFloat = 20
        static let pill: CGFloat = 24
    }

    enum Shadows {
        static let card = Color.black.opacity(0.06)
        static let stampHover = Color.black.opacity(0.15)
    }
}

