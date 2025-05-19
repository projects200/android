package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import javax.inject.Inject

class GetExerciseRecordDetailUseCase @Inject constructor(
    private val getExerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(recordId: Int): BaseResult<ExerciseRecord> {
        return getExerciseRecordRepository.getExerciseDetail(recordId)
    }
}