package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 운동 기록 수정 UseCase
 * 내용 수정과 이미지 작업은 병렬 처리됩니다
 * 이미지 작업은 삭제 완료 후에 업로드를 시작합니다
 * 변경 사항이 있을 시에만 api 호출, 없을 시에는 Success return
 */
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
    ): ExerciseEditResult = try {
        coroutineScope {
            // 내용 수정 작업
            val contentResultDeferred: Deferred<ExerciseEditResult> = async {
                // 내용 변경 없을 시 return
                if (!isContentChanges) return@async ExerciseEditResult.Success(recordId)

                when (val result = updateExerciseRecordUseCase(recordId, recordToUpdate)) {
                    is BaseResult.Success -> ExerciseEditResult.Success(recordId)
                    is BaseResult.Error -> ExerciseEditResult.ContentFailure(
                        recordId = recordId,
                        message = result.message ?: EDIT_CONTENT_FAIL,
                        cause = result.cause
                    )
                }
            }

            // 이미지 수정 작업 (삭제 후 업로드)
            val imageResultDeferred = async {
                // 이미지 변경 없을 시 return
                if (imagesToDelete.isEmpty() && newImages.isEmpty()) {
                    return@async ExerciseEditResult.Success(recordId)
                }

                // 이미지 삭제
                if(imagesToDelete.isNotEmpty()) {
                    val deleteResult = deleteExerciseRecordImagesUseCase(recordId, imagesToDelete)
                    if (deleteResult is BaseResult.Error) {
                        return@async ExerciseEditResult.ImageFailure(
                            recordId = recordId,
                            message = deleteResult.message ?: IMAGE_FAIL,
                            cause = deleteResult.cause
                        )
                    }
                }

                // 이미지 업로드
                if (newImages.isNotEmpty()) {
                    val uploadResult = uploadExerciseRecordImagesUseCase(recordId, newImages)
                    if (uploadResult is BaseResult.Error) {
                        return@async ExerciseEditResult.ImageFailure(
                            recordId = recordId,
                            message = uploadResult.message ?: IMAGE_FAIL,
                            cause = uploadResult.cause
                        )
                    }
                }
                return@async ExerciseEditResult.Success(recordId)
            }

            // 내용 수정, 이미지 삭제 및 업로드 작업 결과 수집
            val contentResult = contentResultDeferred.await()
            val imageResult = imageResultDeferred.await()

            // 최종 결과 처리
            return@coroutineScope when {
                contentResult is ExerciseEditResult.ContentFailure -> contentResult
                imageResult is ExerciseEditResult.ImageFailure -> imageResult
                contentResult is ExerciseEditResult.Success && imageResult is ExerciseEditResult.Success ->
                    ExerciseEditResult.Success(recordId)
                else -> ExerciseEditResult.Failure(UNEXPECTED_ERROR)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ExerciseEditResult.Failure("기록 수정 중 오류 발생: ${e.message}", e)
    }

    companion object {
        private const val IMAGE_FAIL = "이미지 수정 중 오류 발생"
        private const val EDIT_CONTENT_FAIL = "내용 수정에 실패했습니다"
        private const val UNEXPECTED_ERROR = "예상치 못한 실패 발생"
    }
}