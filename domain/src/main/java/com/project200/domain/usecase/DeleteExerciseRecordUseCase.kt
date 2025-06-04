package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class DeleteExerciseRecordUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(recordId: Long): BaseResult<Unit> {
        return exerciseRecordRepository.deleteExerciseRecord(recordId)
    }
}