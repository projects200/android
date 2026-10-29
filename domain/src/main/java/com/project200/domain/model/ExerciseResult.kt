package com.project200.domain.model

sealed class SubmissionResult {
    data class LocalSaved(val recordId: String, val refreshError: BaseResult.Error? = null) : SubmissionResult()
    data class PartialSuccess(val recordId: String, val messageId: Int, val refreshError: BaseResult.Error? = null) : SubmissionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : SubmissionResult()
}


sealed class ExerciseEditResult {
    open val refreshError: BaseResult.Error? get() = null
    // 모든 작업이 성공적으로 완료
    data class Success(val recordId: String, override val refreshError: BaseResult.Error? = null) : ExerciseEditResult()

    // 내용 생성(또는 수정) 실패
    data class ContentFailure(
        val recordId: String,
        val message: String,
        val cause: Throwable? = null,
        override val refreshError: BaseResult.Error? = null
    ) : ExerciseEditResult()

    // 본문 또는 사진 일부는 저장됐지만 이후 이미지 처리 실패
    data class ImageFailure(
        val recordId: String,
        val message: String,
        val cause: Throwable? = null,
        override val refreshError: BaseResult.Error? = null
    ) : ExerciseEditResult()

    // 저장된 작업이 없는 실패
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ExerciseEditResult()
}
