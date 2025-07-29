package com.project200.domain.model

sealed class SubmissionResult {
    data class Success(val recordId: Long, val earnedPoints: Int) : SubmissionResult()
    data class PartialSuccess(val recordId: Long, val message: String) : SubmissionResult()
    data class Failure(val message: String, val cause: Throwable? = null) : SubmissionResult()
}


sealed class ExerciseEditResult {
    // 모든 작업이 성공적으로 완료
    data class Success(val recordId: Long) : ExerciseEditResult()

    // 내용 생성(또는 수정) 실패
    data class ContentFailure(
        val recordId: Long,
        val message: String,
        val cause: Throwable? = null
    ) : ExerciseEditResult()

    // 내용 수정은 성공, 이미지 처리(삭제 또는 업로드) 중 하나 이상 실패
    data class ImageFailure(
        val recordId: Long,
        val message: String,
        val cause: Throwable? = null
    ) : ExerciseEditResult()

    // 그 외 예측하지 못한 전체적인 실패
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : ExerciseEditResult()
}