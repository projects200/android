package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseWriteInput
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** 본문만 변경하면 로컬 저장, 사진이 있으면 기존 온라인 경로를 사용합니다 */
class EditExerciseRecordUseCase @Inject constructor(
    private val updateExerciseRecordUseCase: UpdateExerciseRecordUseCase,
    private val deleteExerciseRecordImagesUseCase: DeleteExerciseRecordImagesUseCase,
    private val uploadExerciseRecordImagesUseCase: UploadExerciseRecordImagesUseCase
) {
    suspend operator fun invoke(
        recordId: String,
        input: ExerciseWriteInput,
        isContentChanges: Boolean,
    ): ExerciseEditResult {
        var hasSuccessfulOperation = false
        var refreshError: BaseResult.Error? = null
        return try {
            // 잘못된 삭제 의도는 본문이나 사진 요청을 보내기 전에 거절합니다
            if (input.removedImageIds.any { it <= 0 }) {
                return ExerciseEditResult.Failure("삭제할 사진의 서버 ID가 올바르지 않습니다")
            }
            if (isContentChanges) {
                when (val result = updateExerciseRecordUseCase(recordId, input)) {
                    is BaseResult.Error ->
                        return ExerciseEditResult.ContentFailure(
                            recordId = recordId,
                            message = result.message ?: EDIT_CONTENT_FAIL,
                            cause = result.cause,
                        )
                    is BaseResult.Success -> {
                        hasSuccessfulOperation = true
                        refreshError = result.refreshError
                    }
                }
            }
            if (input.removedImageIds.isNotEmpty()) {
                when (val result = deleteExerciseRecordImagesUseCase(recordId, input.removedImageIds.toList())) {
                    is BaseResult.Error ->
                        return if (hasSuccessfulOperation) {
                            ExerciseEditResult.ImageFailure(recordId, result.message ?: IMAGE_FAIL, result.cause, refreshError)
                        } else {
                            ExerciseEditResult.Failure(result.message ?: IMAGE_FAIL, result.cause)
                        }
                    is BaseResult.Success -> {
                        hasSuccessfulOperation = true
                        refreshError = refreshError ?: result.refreshError
                    }
                }
            }
            if (input.addedImageUris.isNotEmpty()) {
                when (val result = uploadExerciseRecordImagesUseCase(recordId, input.addedImageUris)) {
                    is BaseResult.Error ->
                        return if (hasSuccessfulOperation) {
                            ExerciseEditResult.ImageFailure(recordId, result.message ?: IMAGE_FAIL, result.cause, refreshError)
                        } else {
                            ExerciseEditResult.Failure(result.message ?: IMAGE_FAIL, result.cause)
                        }
                    is BaseResult.Success -> refreshError = refreshError ?: result.refreshError
                }
            }
            ExerciseEditResult.Success(recordId, refreshError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hasSuccessfulOperation) {
                ExerciseEditResult.ImageFailure(recordId, "기록 수정 중 오류 발생: ${e.message}", e, refreshError)
            } else {
                ExerciseEditResult.Failure("기록 수정 중 오류 발생: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val IMAGE_FAIL = "이미지 수정 중 오류 발생"
        private const val EDIT_CONTENT_FAIL = "내용 수정에 실패했습니다"
    }
}
