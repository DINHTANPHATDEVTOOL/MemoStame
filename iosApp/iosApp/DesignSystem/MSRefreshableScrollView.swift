import SwiftUI

struct MSRefreshableScrollView<Content: View>: View {
    let onRefresh: () -> Void
    let content: () -> Content

    init(onRefresh: @escaping () -> Void, @ViewBuilder content: @escaping () -> Content) {
        self.onRefresh = onRefresh
        self.content = content
    }

    var body: some View {
        ScrollView {
            content()
        }
        .refreshable {
            HapticFeedbackManager.shared.playImpact(style: .medium)
            onRefresh()
        }
    }
}
