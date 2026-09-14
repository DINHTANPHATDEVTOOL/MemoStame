package com.mipastudio.memostamp.domain.model

enum class CommentSubmissionError {
    UNAUTHENTICATED,
    FORBIDDEN,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    SERVER_FAILURE,
    VALIDATION_FAILED,
    UNKNOWN
}
