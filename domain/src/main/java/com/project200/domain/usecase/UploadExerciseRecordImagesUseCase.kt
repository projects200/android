package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class UploadExerciseRecordImagesUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(exerciseId: Long, images: List<String>): BaseResult<Long> {
        return exerciseRecordRepository.uploadExerciseRecordImages(exerciseId, images)
    }
}