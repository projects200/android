package com.project200.domain.model

sealed class SubmissionResult {
    data class Success(val recordId: Long) : SubmissionResult()
    data class PartialSuccess(val recordId: Long, val message: String) : SubmissionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : SubmissionResult()
}