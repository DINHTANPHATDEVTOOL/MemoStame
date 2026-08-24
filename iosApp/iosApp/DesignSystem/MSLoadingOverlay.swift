import SwiftUI

struct MSLoadingOverlay: View {
    let message: String
    @State private var rotation: Double = 0.0

    init(message: String = "Stamping Memory...") {
        self.message = message
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ZStack {
                    Circle()
                        .stroke(MSTheme.Colors.vintageGold.opacity(0.3), lineWidth: 4)
                        .frame(width: 50, height: 50)

                    Circle()
                        .trim(from: 0, to: 0.7)
                        .stroke(MSTheme.Colors.primaryRed, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                        .frame(width: 50, height: 50)
                        .rotationEffect(.degrees(rotation))
                        .onAppear {
                            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                                rotation = 360
                            }
                        }

                    Image(systemName: "seal.fill")
                        .foregroundColor(MSTheme.Colors.primaryRed)
                        .font(.system(size: 20))
                }

                Text(message)
                    .font(MSTheme.Typography.bodyMedium.bold())
                    .foregroundColor(MSTheme.Colors.textPrimary)
            }
            .padding(24)
            .background(Color.white)
            .cornerRadius(MSTheme.Radii.large)
            .shadow(color: Color.black.opacity(0.2), radius: 10, x: 0, y: 5)
        }
    }
}
