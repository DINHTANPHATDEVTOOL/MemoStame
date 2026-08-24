import SwiftUI

public struct MSRefreshableScrollView<Content: View>: View {
    let onRefresh: () -> Void
    let content: () -> Content

    public init(onRefresh: @escaping () -> Void, @ViewBuilder content: @escaping () -> Content) {
        self.onRefresh = onRefresh
        self.content = content
    }

    public var body: some View {
        ScrollView {
            content()
        }
        .refreshable {
            HapticFeedbackManager.shared.playImpact(style: .medium)
            onRefresh()
        }
    }
}
