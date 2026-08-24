import SwiftUI

public struct MSChipView: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    public init(title: String, isSelected: Bool, action: @escaping () -> Void) {
        self.title = title
        self.isSelected = isSelected
        self.action = action
    }

    public var body: some View {
        Button(action: {
            HapticFeedbackManager.shared.playImpact(style: .light)
            action()
        }) {
            Text(title)
                .font(.caption.bold())
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? MSTheme.Colors.primaryRed : Color.white)
                .foregroundColor(isSelected ? Color.white : MSTheme.Colors.textPrimary)
                .cornerRadius(MSTheme.Radii.pill)
                .overlay(
                    RoundedRectangle(cornerRadius: MSTheme.Radii.pill)
                        .stroke(isSelected ? Color.clear : MSTheme.Colors.borderOutline, lineWidth: 1)
                )
                .shadow(color: isSelected ? MSTheme.Colors.primaryRed.opacity(0.25) : Color.black.opacity(0.04), radius: 3, x: 0, y: 1)
        }
    }
}
