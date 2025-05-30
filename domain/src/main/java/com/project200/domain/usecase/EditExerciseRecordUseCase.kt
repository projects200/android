package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class EditExerciseRecordUseCase @Inject constructor(
    private val updateExerciseRecordUseCase: UpdateExerciseRecordUseCase,
    private val deleteExerciseRecordImagesUseCase: DeleteExerciseRecordImagesUseCase,
    private val uploadExerciseRecordImagesUseCase: UploadExerciseRecordImagesUseCase
) {
    suspend operator fun invoke(
        recordId: Long,
        recordToUpdate: ExerciseRecord,
        isContentChanges: Boolean,
        imagesToDelete: List<Long>,
        newImages: List<String>
    ): SubmissionResult {
        return try {
            coroutineScope {
                // 운동 기록 내용 수정
                val contentOperation = if (isContentChanges) {
                    async { updateExerciseRecordUseCase(recordId, recordToUpdate) }
                } else {
                    null
                }

                // 이미지 작업 (삭제 후 업로드)
                val imageOperation = async {
                    var localDeleteResult: BaseResult<Any>? = null
                    var localUploadResult: BaseResult<Long>? = null

                    if (imagesToDelete.isNotEmpty()) {
                        localDeleteResult = deleteExerciseRecordImagesUseCase(recordId, imagesToDelete)
                    }
                    if (newImages.isNotEmpty()) {
                        localUploadResult = uploadExerciseRecordImagesUseCase(recordId, newImages)
                    }
                    Pair(localDeleteResult, localUploadResult)
                }

                val contentResult = contentOperation?.await()
                val (deleteResult, uploadResult) = imageOperation.await()
                // 이미지 실패 여부
                val isAnyImageFailed = deleteResult is BaseResult.Error || uploadResult is BaseResult.Error

                // 최종 SubmissionResult 결정
                if (isContentChanges) { // 내용 수정이 시도
                    when (contentResult) {
                        is BaseResult.Success -> {
                            val updatedContentId = contentResult.data
                            if (isAnyImageFailed) { // 내용 성공, 이미지 실패
                                SubmissionResult.PartialSuccess(updatedContentId, IMAGE_FAIL)
                            } else { // 내용 성공, 이미지 모두 성공 (또는 시도 안 함)
                                SubmissionResult.Success(updatedContentId)
                            }
                        }
                        is BaseResult.Error -> {
                            if (isAnyImageFailed) { // 내용 실패, 이미지 실패
                                SubmissionResult.Failure(IMAGE_FAIL, contentResult.cause)
                            } else { // 내용 실패, 이미지 모두 성공 (또는 시도 안 함)
                                SubmissionResult.PartialSuccess(recordId, contentResult.message ?: EDIT_CONTENT_FAIL)
                            }
                        }
                        null -> { // isContentChanges == true 인데 결과가 null인 비정상 상황
                            SubmissionResult.Failure(EDIT_CONTENT_FAIL_UNEXPECTED_NULL, IllegalStateException("Content update result missing unexpectedly."))
                        }
                    }
                } else { // 내용 변경 없음. 이미지 변경만 시도
                    if (isAnyImageFailed) {
                        SubmissionResult.PartialSuccess(recordId, IMAGE_FAIL)
                    } else {
                        // 시도된 이미지 작업 모두 성공
                        SubmissionResult.Success(recordId)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SubmissionResult.Failure("기록 수정 처리 중 예기치 않은 전체 오류 발생: ${e.message}", e)
        }
    }

    companion object {
        private const val IMAGE_FAIL = "이미지 수정 중 오류 발생"
        private const val EDIT_CONTENT_FAIL = "내용 수정에 실패했습니다"
        private const val EDIT_CONTENT_FAIL_UNEXPECTED_NULL = "내용 수정 결과를 알 수 없습니다."
    }
}