package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class DeleteExerciseRecordImagesUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(exerciseId: Long, imageIds: List<Long>): BaseResult<Unit> {
        return exerciseRecordRepository.deleteExerciseRecordImages(exerciseId, imageIds)
    }
}