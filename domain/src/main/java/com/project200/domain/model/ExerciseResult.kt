package com.project200.domain.model

sealed class SubmissionResult {
    data class LocalSaved(val recordId: String) : SubmissionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : SubmissionResult()
}
