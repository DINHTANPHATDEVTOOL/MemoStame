import SwiftUI
#if canImport(UIKit)
import UIKit
#endif
import shared

// MARK: - Comment Submission Error Classification

enum CommentSubmissionError: Equatable {
    case unauthenticated
    case accountMismatch
    case forbidden
    case rateLimited
    case networkUnavailable
    case serverFailure
    case validationFailed
    case unknown
}

// MARK: - Post Detail Comment View Model / State Controller

class PostDetailViewModel: ObservableObject {
    @Published var isSubmittingComment: Bool = false
    @Published var commentSubmissionError: CommentSubmissionError? = nil
    @Published var commentErrorMessage: String? = nil
    @Published var commentSuccessToken: String? = nil
    @Published var localComments: [FeedComment] = []
    @Published var deleteCommentError: String? = nil

    private var activeSubmissionUid: String? = nil

    func refreshComments(postId: String, repository: SharedMemoStampRepository) {
        let feedPosts = (repository.feedPosts.value as? [FeedPost]) ?? []
        if let repoPost = feedPosts.first(where: { $0.id == postId }) {
            self.localComments = repoPost.comments
        }
    }

    func addComment(
        postId: String,
        draft: String,
        authUid: String?,
        repository: SharedMemoStampRepository
    ) {
        // 1. Validation before network
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty && trimmed.count <= 500 else {
            self.commentSubmissionError = .validationFailed
            return
        }

        // 2. Rapid tap deduplication
        guard !isSubmittingComment else { return }

        // 3. Auth verification
        guard let currentAuthUid = authUid else {
            self.commentSubmissionError = .unauthenticated
            self.commentErrorMessage = AppLanguageManager.shared.localized("comment_error_unauthenticated")
            return
        }

        let repoUser = repository.currentUser.value as? UserProfile
        guard repoUser?.uid == currentAuthUid else {
            self.commentSubmissionError = .accountMismatch
            self.commentErrorMessage = AppLanguageManager.shared.localized("comment_error_account_mismatch")
            return
        }

        // 4. Enter SUBMITTING state
        isSubmittingComment = true
        commentSubmissionError = nil
        commentErrorMessage = nil
        activeSubmissionUid = currentAuthUid
        let submittedSnapshot = trimmed

        // 5. Dispatch mutation with explicit acknowledgement
        if IOSFeedRepository.shared.activeUserId != nil {
            IOSFeedRepository.shared.addComment(postId: postId, text: submittedSnapshot) { [weak self] result in
                guard let self = self else { return }
                // Account switch race check: drop stale completion if user changed
                guard self.activeSubmissionUid == currentAuthUid else {
                    self.isSubmittingComment = false
                    return
                }

                self.isSubmittingComment = false
                switch result {
                case .success:
                    // Success confirmed! Update repository and emit token
                    repository.addComment(postId: postId, content: submittedSnapshot)
                    self.refreshComments(postId: postId, repository: repository)
                    self.commentSuccessToken = submittedSnapshot
                    self.commentSubmissionError = nil
                    self.commentErrorMessage = nil
                case .failure(let error):
                    let classified = self.classifyError(error)
                    self.commentSubmissionError = classified
                    self.commentErrorMessage = self.localizedErrorMessage(for: classified)
                }
            }
        } else {
            // Local / mock fallback path
            repository.addComment(postId: postId, content: submittedSnapshot)
            self.refreshComments(postId: postId, repository: repository)
            self.isSubmittingComment = false
            self.commentSuccessToken = submittedSnapshot
            self.commentSubmissionError = nil
            self.commentErrorMessage = nil
        }
    }

    func deleteComment(
        postId: String,
        comment: FeedComment,
        authUid: String?,
        repository: SharedMemoStampRepository
    ) {
        guard let currentAuthUid = authUid, comment.authorId == currentAuthUid else {
            self.deleteCommentError = AppLanguageManager.shared.localized("comment_error_delete_forbidden")
            return
        }

        let repoUser = repository.currentUser.value as? UserProfile
        guard repoUser?.uid == currentAuthUid else {
            self.deleteCommentError = AppLanguageManager.shared.localized("comment_error_account_mismatch")
            return
        }

        if IOSFeedRepository.shared.activeUserId != nil {
            IOSFeedRepository.shared.deleteComment(postId: postId, commentId: comment.id) { [weak self] result in
                guard let self = self else { return }
                switch result {
                case .success:
                    repository.deleteComment(postId: postId, commentId: comment.id)
                    self.refreshComments(postId: postId, repository: repository)
                    self.deleteCommentError = nil
                case .failure:
                    self.deleteCommentError = AppLanguageManager.shared.localized("comment_error_delete_failed")
                }
            }
        } else {
            repository.deleteComment(postId: postId, commentId: comment.id)
            self.refreshComments(postId: postId, repository: repository)
            self.deleteCommentError = nil
        }
    }

    func consumeCommentSuccess() {
        self.commentSuccessToken = nil
    }

    func dismissCommentError() {
        self.commentSubmissionError = nil
        self.commentErrorMessage = nil
    }

    func dismissDeleteError() {
        self.deleteCommentError = nil
    }

    func classifyError(_ error: Error) -> CommentSubmissionError {
        let desc = error.localizedDescription.lowercased()
        if desc.contains("429") || desc.contains("rate") || desc.contains("too many") {
            return .rateLimited
        }
        if desc.contains("401") || desc.contains("unauthorized") || desc.contains("jwt") || desc.contains("token") {
            return .unauthenticated
        }
        if desc.contains("403") || desc.contains("forbidden") || desc.contains("permission") || desc.contains("rls") {
            return .forbidden
        }
        if desc.contains("network") || desc.contains("offline") || desc.contains("connect") || desc.contains("timed out") || desc.contains("hostname") {
            return .networkUnavailable
        }
        if desc.contains("500") || desc.contains("502") || desc.contains("503") || desc.contains("server") {
            return .serverFailure
        }
        return .unknown
    }

    func localizedErrorMessage(for error: CommentSubmissionError) -> String {
        let lang = AppLanguageManager.shared
        switch error {
        case .unauthenticated:
            return lang.localized("comment_error_unauthenticated")
        case .accountMismatch:
            return lang.localized("comment_error_account_mismatch")
        case .forbidden:
            return lang.localized("comment_error_forbidden")
        case .rateLimited:
            return lang.localized("comment_error_rate_limited")
        case .networkUnavailable:
            return lang.localized("comment_error_network")
        case .serverFailure:
            return lang.localized("comment_error_server")
        case .validationFailed:
            return lang.localized("comment_error_server")
        case .unknown:
            return lang.localized("comment_error_server")
        }
    }
}

// MARK: - Post Detail Screen View

struct PostDetailScreenView: View {
    let post: FeedPost
    let repository: SharedMemoStampRepository
    let onLike: () -> Void
    let onReply: () -> Void

    @Environment(\.presentationMode) var presentationMode
    @StateObject private var viewModel = PostDetailViewModel()
    @ObservedObject private var langManager = AppLanguageManager.shared

    @State private var commentText: String = ""
    @State private var activeLightboxReply: FeedReply? = nil
    @State private var showHeartAnimation: Bool = false

    private var authenticatedUid: String? {
        guard let raw = SupabaseAuthService.shared.currentUserId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty,
              raw.lowercased() != "user_me",
              !raw.lowercased().hasPrefix("guest_") else {
            return nil
        }
        return raw
    }

    private func canDeleteComment(_ c: FeedComment) -> Bool {
        guard let authUid = authenticatedUid else { return false }
        return c.authorId == authUid
    }

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
                .accessibilityLabel(langManager.localized("a11y_back"))

                Spacer()

                Text(langManager.localized("post_detail_header"))
                    .font(.headline.bold())
                    .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))

                Spacer()

                Button(action: onReply) {
                    HStack(spacing: 4) {
                        Image(systemName: "envelope.fill")
                            .font(.caption)
                        Text(langManager.localized("post_detail_reply"))
                            .font(.caption.bold())
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(red: 0.20, green: 0.45, blue: 0.75).opacity(0.12))
                    .foregroundColor(Color(red: 0.20, green: 0.45, blue: 0.75))
                    .cornerRadius(14)
                }
                .accessibilityLabel(langManager.localized("post_detail_reply"))
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
                            Text(String(format: langManager.localized("post_detail_stamp_replies"), post.replies.count))
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
                        Text(String(format: langManager.localized("post_comments_title"), viewModel.localComments.count))
                            .font(.subheadline.bold())
                            .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                        Spacer()
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        if viewModel.localComments.isEmpty {
                            Text(langManager.localized("post_detail_no_comments"))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 10)
                        } else {
                            ForEach(viewModel.localComments, id: \.id) { c in
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

                                    if canDeleteComment(c) {
                                        Button(action: {
                                            viewModel.deleteComment(
                                                postId: post.id,
                                                comment: c,
                                                authUid: authenticatedUid,
                                                repository: repository
                                            )
                                        }) {
                                            Image(systemName: "trash")
                                                .font(.caption)
                                                .foregroundColor(.gray)
                                        }
                                        .accessibilityLabel(langManager.localized("post_detail_delete"))
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }

            // Delete Comment Error Banner
            if let delErr = viewModel.deleteCommentError {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    Text(delErr)
                        .font(.caption)
                        .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    Spacer()
                    Button(action: { viewModel.dismissDeleteError() }) {
                        Image(systemName: "xmark")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel(langManager.localized("a11y_close"))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.1))
            }

            // Comment Submission Error Banner
            if let error = viewModel.commentErrorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.85, green: 0.25, blue: 0.20))
                    Text(error)
                        .font(.caption)
                        .foregroundColor(Color(red: 0.15, green: 0.15, blue: 0.18))
                    Spacer()
                    Button(action: { viewModel.dismissCommentError() }) {
                        Image(systemName: "xmark")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    .accessibilityLabel(langManager.localized("a11y_close"))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.1))
            }

            // Comment Input Row at bottom
            HStack(spacing: 10) {
                TextField(langManager.localized("post_detail_comment_placeholder"), text: $commentText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())

                Button(action: {
                    viewModel.addComment(
                        postId: post.id,
                        draft: commentText,
                        authUid: authenticatedUid,
                        repository: repository
                    )
                }) {
                    if viewModel.isSubmittingComment {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(width: 20, height: 20)
                            .padding(8)
                            .background(Color(red: 0.85, green: 0.25, blue: 0.20))
                            .clipShape(Circle())
                    } else {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20) : .gray)
                            .padding(8)
                            .background(isInputValid ? Color(red: 0.85, green: 0.25, blue: 0.20).opacity(0.12) : Color.gray.opacity(0.1))
                            .clipShape(Circle())
                    }
                }
                .disabled(!isInputValid || viewModel.isSubmittingComment)
                .accessibilityLabel(langManager.localized("post_detail_send"))
            }
            .padding(14)
            .background(Color.white)
            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: -2)
        }
        .onAppear {
            viewModel.refreshComments(postId: post.id, repository: repository)
            if viewModel.localComments.isEmpty && !post.comments.isEmpty {
                viewModel.localComments = post.comments
            }
        }
        .onChange(of: viewModel.commentSuccessToken) { token in
            if let token = token {
                if commentText.trimmingCharacters(in: .whitespacesAndNewlines) == token {
                    commentText = ""
                }
                viewModel.consumeCommentSuccess()
            }
        }
        .background(Color(red: 0.98, green: 0.96, blue: 0.92).ignoresSafeArea())
        .sheet(item: $activeLightboxReply) { reply in
            ReplyLightboxView(reply: reply)
        }
    }
}
