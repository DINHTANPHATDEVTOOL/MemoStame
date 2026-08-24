import SwiftUI

struct MSButton: View {
    enum Variant {
        case primary
        case secondary
        case gold
        case outline
    }

    let title: String
    let iconSystemName: String?
    let variant: Variant
    let action: () -> Void

    init(
        title: String,
        iconSystemName: String? = nil,
        variant: Variant = .primary,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.iconSystemName = iconSystemName
        self.variant = variant
        self.action = action
    }

    var body: some View {
        Button(action: {
            HapticFeedbackManager.shared.playImpact(style: .medium)
            action()
        }) {
            HStack(spacing: 8) {
                if let icon = iconSystemName {
                    Image(systemName: icon)
                        .font(.subheadline.bold())
                }
                Text(title)
                    .font(.body.bold())
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(backgroundColor)
            .foregroundColor(foregroundColor)
            .cornerRadius(MSTheme.Radii.pill)
            .overlay(
                RoundedRectangle(cornerRadius: MSTheme.Radii.pill)
                    .stroke(borderColor, lineWidth: variant == .outline ? 1.5 : 0)
            )
            .shadow(color: shadowColor, radius: 4, x: 0, y: 2)
        }
    }

    private var backgroundColor: Color {
        switch variant {
        case .primary: return MSTheme.Colors.primaryRed
        case .secondary: return MSTheme.Colors.textPrimary
        case .gold: return MSTheme.Colors.vintageGold
        case .outline: return Color.white
        }
    }

    private var foregroundColor: Color {
        switch variant {
        case .outline: return MSTheme.Colors.primaryRed
        default: return Color.white
        }
    }

    private var borderColor: Color {
        switch variant {
        case .outline: return MSTheme.Colors.primaryRed
        default: return Color.clear
        }
    }

    private var shadowColor: Color {
        switch variant {
        case .primary: return MSTheme.Colors.primaryRed.opacity(0.3)
        case .gold: return MSTheme.Colors.vintageGold.opacity(0.3)
        default: return Color.black.opacity(0.05)
        }
    }
}
