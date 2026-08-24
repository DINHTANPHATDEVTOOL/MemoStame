import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct EnvelopeShareModalView: View {
    let stampTitle: String
    let stampUrl: String
    let onDismiss: () -> Void
    
    @State private var copied: Bool = false
    @State private var selectedRecipient: String = "All Friends"
    
    let recipients = ["All Friends", "Huy Tran", "Linh Pham", "Phat Le", "Class 22DTHB3"]

    var body: some View {
        VStack(spacing: 20) {
            // Header handle & title
            Capsule()
                .fill(Color.gray.opacity(0.4))
                .frame(width: 40, height: 5)
                .padding(.top, 10)

            HStack {
                Image(systemName: "envelope.badge.shield.halfopen.fill")
                    .font(.title2)
                    .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                Text("Share Vintage Envelope")
                    .font(.title3.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundColor(.gray)
                }
            }
            .padding(.horizontal)

            // Envelope Preview Box
            VStack(spacing: 12) {
                Image(systemName: "paperplane.circle.fill")
                    .font(.system(size: 48))
                    .foregroundColor(Color(red: 0.82, green: 0.65, blue: 0.35))
                
                Text(stampTitle.isEmpty ? "Memory Stamp" : stampTitle)
                    .font(.headline.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                
                Text("Sealed with MemoStamp Wax #2026")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(Color(red: 0.98, green: 0.96, blue: 0.92))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(red: 0.82, green: 0.65, blue: 0.35), lineWidth: 1.5)
            )
            .padding(.horizontal)

            // Recipient Selector
            VStack(alignment: .leading, spacing: 8) {
                Text("SEND DIRECT ENVELOPE TO:")
                    .font(.caption2.bold())
                    .foregroundColor(.secondary)
                    .padding(.horizontal)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(recipients, id: \.self) { item in
                            Button(action: { selectedRecipient = item }) {
                                Text(item)
                                    .font(.subheadline.bold())
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(selectedRecipient == item ? Color(red: 0.85, green: 0.25, blue: 0.20) : Color.gray.opacity(0.12))
                                    .foregroundColor(selectedRecipient == item ? .white : .primary)
                                    .cornerRadius(20)
                            }
                        }
                    }
                    .padding(.horizontal)
                }
            }

            // Action Buttons
            VStack(spacing: 12) {
                // Button 1: Copy Shareable Link (UIKit UIPasteboard)
                Button(action: {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = "Check out my MemoStamp '\(stampTitle)': \(stampUrl)"
                    #endif
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        copied = false
                    }
                }) {
                    HStack {
                        Image(systemName: copied ? "checkmark.circle.fill" : "doc.on.doc.fill")
                        Text(copied ? "Stamp Link Copied!" : "Copy Envelope Link")
                            .font(.body.bold())
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color(red: 0.15, green: 0.15, blue: 0.18))
                    .foregroundColor(.white)
                    .cornerRadius(14)
                }

                // Button 2: Native iOS Activity Share Sheet
                Button(action: {
                    #if canImport(UIKit)
                    let text = "Check out my MemoStamp '\(stampTitle)' on MemoStamp!"
                    let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                    if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                       let rootVC = windowScene.windows.first?.rootViewController {
                        rootVC.present(av, animated: true)
                    }
                    #endif
                }) {
                    HStack {
                        Image(systemName: "square.and.arrow.up.fill")
                        Text("Share via System Apps")
                            .font(.body.bold())
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.gray.opacity(0.15))
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    .cornerRadius(14)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 20)
        }
        .background(Color.white)
        .cornerRadius(24, corners: [.topLeft, .topRight])
    }
}

#if canImport(UIKit)
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}
#endif
