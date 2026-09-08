#!/usr/bin/env bash
set -e

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

echo "=========================================================="
echo "  Task #82: Post Detail Comment Reliability Readiness     "
echo "=========================================================="

FAILED_CHECKS=0

check_step() {
    local step_num="$1"
    local desc="$2"
    local cmd="$3"
    printf "[%02d/35] %s... " "$step_num" "$desc"
    if eval "$cmd"; then
        echo "PASS"
    else
        echo "FAIL"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

# 1. CommentSubmissionError domain model in shared
check_step "1" "Checking CommentSubmissionError in shared" '
    test -f shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt && \
    grep -q "enum class CommentSubmissionError" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt && \
    grep -q "RATE_LIMITED" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt && \
    grep -q "UNAUTHENTICATED" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt && \
    grep -q "NETWORK_UNAVAILABLE" shared/src/commonMain/kotlin/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt
'

# 2. CommentSubmissionError domain model in androidApp
check_step "2" "Checking CommentSubmissionError in androidApp" '
    test -f androidApp/src/main/java/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt && \
    grep -q "enum class CommentSubmissionError" androidApp/src/main/java/com/mipastudio/memostamp/domain/model/CommentSubmissionError.kt
'

# 3. PostDetailUiState properties
check_step "3" "Checking PostDetailUiState tracking fields" '
    grep -q "val isSubmittingComment: Boolean" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "val commentSubmissionError: CommentSubmissionError?" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "val commentSuccessToken: String?" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "val postUnavailable: Boolean" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt
'

# 4. Android submit lock & deduplication
check_step "4" "Checking Android submit lock prevents rapid tap duplicates" '
    grep -q "if (_uiState.value.isSubmittingComment)" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt
'

# 5. Android error classification
check_step "5" "Checking Android error classification for 429, auth, network, server" '
    grep -q "CommentSubmissionError.RATE_LIMITED" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "CommentSubmissionError.UNAUTHENTICATED" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "CommentSubmissionError.FORBIDDEN" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "CommentSubmissionError.NETWORK_UNAVAILABLE" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "CommentSubmissionError.SERVER_FAILURE" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt
'

# 6. Android account-switch race guard
check_step "6" "Checking Android account-switch race safety" '
    grep -q "lastSubmissionAuthUid" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "currentActiveUid != lastSubmissionAuthUid" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt
'

# 7. Android post load unavailable terminal state
check_step "7" "Checking Android post load failure state (no infinite spinner)" '
    grep -q "postUnavailable = true" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailViewModel.kt && \
    grep -q "uiState.postUnavailable" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt
'

# 8. Android safe draft clearing (only clears matching snapshot, preserves newer edits)
check_step "8" "Checking Android draft clearing preserves newer edits" '
    grep -q "commentText.trim() == token" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt && \
    grep -q "viewModel.consumeCommentSuccess()" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt
'

# 9. Android comment error banner & submit progress
check_step "9" "Checking Android comment submission error banner and progress indicator" '
    grep -q "uiState.commentSubmissionError != null" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt && \
    grep -q "uiState.isSubmittingComment" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt
'

# 10. Android removed unused SampleDataRepository import
check_step "10" "Checking removal of unused SampleDataRepository import" '
    ! grep -q "SampleDataRepository" androidApp/src/main/java/com/mipastudio/memostamp/feature/feed/PostDetailScreen.kt
'

# 11. Android strings.xml contains audited keys
check_step "11" "Checking Android base strings.xml contains post detail & comment keys" '
    grep -q "post_detail_title" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_options" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_remove_feed" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_delete_memory" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_reply_chain_title" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_delete_comment" androidApp/src/main/res/values/strings.xml && \
    grep -q "post_detail_unavailable" androidApp/src/main/res/values/strings.xml && \
    grep -q "comment_error_rate_limited" androidApp/src/main/res/values/strings.xml && \
    grep -q "comment_error_unauthenticated" androidApp/src/main/res/values/strings.xml && \
    grep -q "comment_error_network" androidApp/src/main/res/values/strings.xml
'

# 12. Android values-vi/strings.xml key parity
check_step "12" "Checking Android values-vi strings parity" '
    grep -q "post_detail_title" androidApp/src/main/res/values-vi/strings.xml && \
    grep -q "comment_error_rate_limited" androidApp/src/main/res/values-vi/strings.xml
'

# 13. iOS PostDetailViewModel exists with CommentSubmissionError
check_step "13" "Checking iOS PostDetailViewModel & CommentSubmissionError" '
    grep -q "enum CommentSubmissionError" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "class PostDetailViewModel: ObservableObject" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 14. iOS submit lock & deduplication
check_step "14" "Checking iOS submit lock" '
    grep -q "guard \!isSubmittingComment else { return }" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 15. iOS account-switch race safety
check_step "15" "Checking iOS account-switch race safety" '
    grep -q "activeSubmissionUid" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "self.activeSubmissionUid == currentAuthUid" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 16. iOS draft clearing preserves newer edits
check_step "16" "Checking iOS draft clearing preserves newer edits" '
    grep -q "commentText.trimmingCharacters(in: .whitespacesAndNewlines) == token" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "viewModel.consumeCommentSuccess()" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 17. iOS loading indicator during comment submission
check_step "17" "Checking iOS submission progress indicator" '
    grep -q "if viewModel.isSubmittingComment" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "ProgressView()" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 18. iOS comment error banner
check_step "18" "Checking iOS comment error banner" '
    grep -q "if let error = viewModel.commentErrorMessage" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 19. iOS absence of hardcoded Vietnamese strings in PostDetailScreenView
check_step "19" "Checking absence of hardcoded Vietnamese in PostDetailScreenView" '
    ! grep -q "Bạn cần đăng nhập" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    ! grep -q "Không thể xác minh tài khoản" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    ! grep -q "Không có quyền xóa" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 20. iOS audited copy uses AppLanguageManager
check_step "20" "Checking iOS audited copy uses langManager.localized" '
    grep -q "langManager.localized(\"post_detail_header\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "langManager.localized(\"post_detail_reply\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "langManager.localized(\"post_detail_comment_placeholder\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "langManager.localized(\"post_detail_send\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "langManager.localized(\"post_detail_delete\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift && \
    grep -q "langManager.localized(\"a11y_back\")" iosApp/iosApp/Features/Feed/PostDetailScreenView.swift
'

# 21. iOS Localizable.xcstrings contains post detail keys
check_step "21" "Checking iOS Localizable.xcstrings contains post detail keys" '
    grep -q "post_detail_header" iosApp/iosApp/Localizable.xcstrings && \
    grep -q "comment_error_rate_limited" iosApp/iosApp/Localizable.xcstrings && \
    grep -q "comment_error_unauthenticated" iosApp/iosApp/Localizable.xcstrings
'

# 22. Android Unit Tests exist
check_step "22" "Checking Android PostDetailCommentReliabilityTest exists" '
    test -f androidApp/src/test/java/com/mipastudio/memostamp/feature/feed/PostDetailCommentReliabilityTest.kt && \
    grep -q "class PostDetailCommentReliabilityTest" androidApp/src/test/java/com/mipastudio/memostamp/feature/feed/PostDetailCommentReliabilityTest.kt
'

# 23. iOS Unit Tests exist
check_step "23" "Checking iOS PostDetailCommentReliabilityTests exists" '
    test -f iosApp/iosAppTests/PostDetailCommentReliabilityTests.swift && \
    grep -q "class PostDetailCommentReliabilityTests" iosApp/iosAppTests/PostDetailCommentReliabilityTests.swift
'

# 24. Android Unit Tests pass
check_step "24" "Running Android PostDetailCommentReliabilityTest" '
    export JAVA_HOME=/snap/android-studio/current/jbr
    export PATH=$JAVA_HOME/bin:$PATH
    export ANDROID_HOME=/home/rd/Android/Sdk
    ./gradlew -Dorg.gradle.java.home=/snap/android-studio/current/jbr :androidApp:testDebugUnitTest --tests "com.mipastudio.memostamp.feature.feed.PostDetailCommentReliabilityTest" > /dev/null 2>&1
'

# 25. Shared module builds
check_step "25" "Building KMP shared module" '
    export JAVA_HOME=/snap/android-studio/current/jbr
    export PATH=$JAVA_HOME/bin:$PATH
    export ANDROID_HOME=/home/rd/Android/Sdk
    ./gradlew -Dorg.gradle.java.home=/snap/android-studio/current/jbr :shared:build > /dev/null 2>&1
'

# 26. #80 Album page lifecycle regression check
check_step "26" "Checking #80 Album Page Lifecycle regression" '
    ./scripts/check-album-page-lifecycle-readiness.sh > /dev/null 2>&1
'

# 27. #78 Album editor regression check
check_step "27" "Checking #78 Album Editor regression" '
    ./scripts/check-album-editor-readiness.sh > /dev/null 2>&1
'

# 28. #76 Album placement regression check
check_step "28" "Checking #76 Album Placement regression" '
    ./scripts/check-album-placement-readiness.sh > /dev/null 2>&1
'

# 29. #74 3D Book renderer regression check
check_step "29" "Checking #74 3D Book regression" '
    ./scripts/check-3d-stamp-book-readiness.sh > /dev/null 2>&1
'

# 30. #72 Icon system regression check
check_step "30" "Checking #72 Icon system regression" '
    ./scripts/check-icon-system-readiness.sh > /dev/null 2>&1
'

# 31. #70 Localization regression check
check_step "31" "Checking #70 Localization regression" '
    ./scripts/check-localization-readiness.sh > /dev/null 2>&1
'

# 32. #68 Camera hardware zoom regression check
check_step "32" "Checking #68 Camera hardware zoom regression" '
    ./scripts/check-camera-hardware-zoom-readiness.sh > /dev/null 2>&1
'

# 33. #66 Location grounding regression check
check_step "33" "Checking #66 Location grounding regression" '
    ./scripts/check-ios-location-grounding-readiness.sh > /dev/null 2>&1
'

# 34. #64 Store privacy compliance check
check_step "34" "Checking #64 Store privacy compliance regression" '
    ./scripts/check-store-privacy-readiness.sh > /dev/null 2>&1
'

# 35. #62 Release candidate regression check
check_step "35" "Checking #62 Release candidate regression" '
    ./scripts/check-release-candidate-readiness.sh > /dev/null 2>&1
'

echo "----------------------------------------------------------"
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo "  ALL 35 CHECKS PASSED SUCCESSFULLY! (READY FOR AUTO-MERGE)"
    echo "=========================================================="
    exit 0
else
    echo "  FAILED $FAILED_CHECKS CHECKS. PLEASE REVIEW."
    echo "=========================================================="
    exit 1
fi
