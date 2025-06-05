package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class UpdateExerciseRecordUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(recordId: Long, record: ExerciseRecord): BaseResult<Long> {
        return exerciseRecordRepository.updateExerciseRecord(record, recordId)
    }
}