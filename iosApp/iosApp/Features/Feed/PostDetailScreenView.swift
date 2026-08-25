import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

struct PostDetailScreenView: View {
    let post: FeedPost
    let repository: SharedMemoStampRepository
    let onLike: () -> Void
    let onReply: () -> Void

    @Environment(\.presentationMode) var presentationMode
    @State private var commentText: String = ""
    @State private var localComments: [FeedComment] = []
    @State private var activeLightboxReply: FeedReply? = nil
    @State private var showHeartAnimation: Bool = false

    private var formattedDate: String {
        guard post.createdAt > 0 else {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy.MM.dd"
            return formatter.string(from: Date())
        }
        let date = Date(timeIntervalSince1970: TimeInterval(post.createdAt) / 1000.0)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy.MM.dd"
        return formatter.string(from: date)
    }

    var isInputValid: Bool {
        let trimmed = commentText.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 500
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack {
                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.title3.bold())
                        .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        .frame(width: 36, height: 36)
                        .background(Color.gray.opacity(0.12))
                        .clipShape(Circle())
                }

                Spacer()

                Text("Memory Detail")
                    .font(.headline.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))

                Spacer()

                Button(action: onReply) {
                    HStack(spacing: 4) {
                        Image(systemName: "envelope.fill")
                            .font(.caption)
                        Text("Reply")
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.12))
                    .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                    .cornerRadius(14)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Author Information Header
                    HStack(spacing: 12) {
                        AsyncImage(url: URL(string: post.authorAvatar)) { phase in
                            if let img = phase.image {
                                img.resizable().aspectRatio(contentMode: .fill)
                            } else {
                                Circle().fill(Color.gray.opacity(0.2))
                            }
                        }
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 2) {
                            Text(post.authorName)
                                .font(.subheadline.bold())
                                .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                            if let loc = post.location, !loc.isEmpty {
                                HStack(spacing: 4) {
                                    Image(systemName: "mappin.and.ellipse")
                                        .font(.caption)
                                    Text(loc)
                                        .font(.caption.bold())
                                }
                                .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                            }
                        }
                        Spacer()
                    }

                    // Die Cut Stamp Component with Double-Tap Like
                    ZStack {
                        DieCutStampView(
                            title: post.stampTitle,
                            imageUrl: post.stampUrl,
                            location: post.location,
                            dateStr: formattedDate,
                            note: post.caption,
                            shape: post.shape,
                            isInteractive: true,
                            showMoldOverlay: false
                        )
                        .onTapGesture(count: 2) {
                            if !post.isLikedByMe {
                                onLike()
                            }
                            withAnimation(.spring()) {
                                showHeartAnimation = true
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                                showHeartAnimation = false
                            }
                        }

                        if showHeartAnimation {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 80))
                                .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                                .shadow(color: Color.black.opacity(0.2), radius: 8)
                                .transition(.scale.combined(with: .opacity))
                        }
                    }

                    // Caption Text
                    if let cap = post.caption, !cap.isEmpty {
                        Text(cap)
                            .font(.body)
                            .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                            .padding(.vertical, 4)
                    }

                    // Inline Stamp Replies Section
                    if !post.replies.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Stamp Replies (\(post.replies.count))")
                                .font(.subheadline.bold())
                                .foregroundColor(.secondary)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(post.replies, id: \.id) { reply in
                                        MiniStampReplyCardView(reply: reply, onClick: {
                                            activeLightboxReply = reply
                                        })
                                    }
                                }
                            }
                        }
                        .padding(.top, 4)
                    }

                    Divider()

                    // Comments Section Header
                    HStack {
                        Text("Comments (\(localComments.count))")
                            .font(.subheadline.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Spacer()
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        if localComments.isEmpty {
                            Text("No comments yet. Start the conversation!")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 10)
                        } else {
                            ForEach(localComments, id: \.id) { c in
                                HStack(alignment: .top, spacing: 10) {
                                    Circle()
                                        .fill(Color.gray.opacity(0.2))
                                        .frame(width: 34, height: 34)
                                        .overlay(Text(String(c.authorName.prefix(1))).font(.caption.bold()))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.authorName)
                                            .font(.caption.bold())
                                        Text(c.content)
                                            .font(.subheadline)
                                            .foregroundColor(Color(red: 0.20, green: 0.20, blue: 0.25))
                                    }

                                    Spacer()

                                    if c.authorId == "user_me" {
                                        Button(action: {
                                            localComments.removeAll { $0.id == c.id }
                                        }) {
                                            Image(systemName: "trash")
                                                .font(.caption)
                                                .foregroundColor(.gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }

            // Comment Input Row at bottom
            HStack(spacing: 10) {
                TextField("Write a comment...", text: $commentText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                Button(action: {
                    if isInputValid {
                        let currentUser = repository.currentUser.value as? UserProfile
                        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                        let c = FeedComment(
                            id: "c_\(nowMs)",
                            postId: post.id,
                            authorId: currentUser?.uid ?? "user_me",
                            authorName: currentUser?.displayName ?? "User",
                            authorAvatar: currentUser?.avatarUrl ?? "",
                            content: commentText.trimmingCharacters(in: .whitespacesAndNewlines),
                            createdAt: nowMs
                        )
                        localComments.append(c)
                        commentText = ""
                    }
                }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20) : .gray)
                        .padding(8)
                        .background(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.12) : Color.gray.opacity(0.1))
                        .clipShape(Circle())
                }
                .disabled(!isInputValid)
            }
            .padding(14)
            .background(Color.white)
            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: -2)
        }
        .onAppear {
            self.localComments = post.comments
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
        .sheet(item: $activeLightboxReply) { reply in
            ReplyLightboxView(reply: reply)
        }
    }
}
