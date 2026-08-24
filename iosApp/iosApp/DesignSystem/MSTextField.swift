import SwiftUI

public struct MSTextField: View {
    let placeholder: String
    @Binding text: String
    let iconSystemName: String?
    let isSecure: Bool

    public init(
        _ placeholder: String,
        text: Binding<String>,
        iconSystemName: String? = nil,
        isSecure: Bool = false
    ) {
        self.placeholder = placeholder
        self._text = text
        self.iconSystemName = iconSystemName
        self.isSecure = isSecure
    }

    public var body: some View {
        HStack(spacing: 12) {
            if let icon = iconSystemName {
                Image(systemName: icon)
                    .foregroundColor(MSTheme.Colors.textSecondary)
                    .font(.body)
            }
            if isSecure {
                SecureField(placeholder, text: $text)
                    .font(.body)
            } else {
                TextField(placeholder, text: $text)
                    .font(.body)
            }
            if !text.isEmpty {
                Button(action: {
                    text = ""
                    HapticFeedbackManager.shared.playImpact(style: .light)
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(Color.gray.opacity(0.6))
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.white)
        .cornerRadius(MSTheme.Radii.medium)
        .overlay(
            RoundedRectangle(cornerRadius: MSTheme.Radii.medium)
                .stroke(MSTheme.Colors.borderOutline, lineWidth: 1)
        )
    }
}
