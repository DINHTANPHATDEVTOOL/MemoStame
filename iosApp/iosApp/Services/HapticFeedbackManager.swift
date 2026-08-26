import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// Native Swift Haptic Feedback Manager wrapping UIImpactFeedbackGenerator & UINotificationFeedbackGenerator
final class HapticFeedbackManager {
    static let shared = HapticFeedbackManager()

    private init() {}

    enum ImpactStyle {
        case light
        case medium
        case heavy
        case soft
        case rigid
    }

    enum NotificationType {
        case success
        case warning
        case error
    }

    func playImpact(style: ImpactStyle) {
        #if canImport(UIKit)
        let uiStyle: UIImpactFeedbackGenerator.FeedbackStyle
        switch style {
        case .light: uiStyle = .light
        case .medium: uiStyle = .medium
        case .heavy: uiStyle = .heavy
        case .soft:
            if #available(iOS 13.0, *) { uiStyle = .soft } else { uiStyle = .light }
        case .rigid:
            if #available(iOS 13.0, *) { uiStyle = .rigid } else { uiStyle = .heavy }
        }
        let generator = UIImpactFeedbackGenerator(style: uiStyle)
        generator.prepare()
        generator.impactOccurred()
        #endif
    }

    func playNotification(type: NotificationType) {
        #if canImport(UIKit)
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        switch type {
        case .success: generator.notificationOccurred(.success)
        case .warning: generator.notificationOccurred(.warning)
        case .error: generator.notificationOccurred(.error)
        }
        #endif
    }

    func playSelection() {
        #if canImport(UIKit)
        let generator = UISelectionFeedbackGenerator()
        generator.prepare()
        generator.selectionChanged()
        #endif
    }

    func playSuccess() {
        playNotification(type: .success)
    }

    func playWarning() {
        playNotification(type: .warning)
    }

    func playError() {
        playNotification(type: .error)
    }
}
