package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordCreationResult
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class CreateExerciseRecordUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(record: ExerciseRecord): BaseResult<ExerciseRecordCreationResult> {
        return exerciseRecordRepository.createExerciseRecord(record)
    }
}