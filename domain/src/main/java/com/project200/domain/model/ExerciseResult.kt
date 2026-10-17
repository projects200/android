package com.project200.domain.model

sealed class SubmissionResult {
    data class Success(val recordId: String, val earnedPoints: Int, val refreshError: BaseResult.Error? = null) : SubmissionResult()
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

    // 내용 수정은 성공, 이미지 처리(삭제 또는 업로드) 중 하나 이상 실패
    data class ImageFailure(
        val recordId: String,
        val message: String,
        val cause: Throwable? = null,
        override val refreshError: BaseResult.Error? = null
    ) : ExerciseEditResult()

    // 그 외 예측하지 못한 전체적인 실패
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ExerciseEditResult()
}