package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class CreateExerciseRecordUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(input: ExerciseWriteInput): BaseResult<LocalExerciseSaveResult> {
        return exerciseRecordRepository.createExerciseRecord(input)
    }
}
