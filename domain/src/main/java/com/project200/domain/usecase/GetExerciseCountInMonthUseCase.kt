package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.repository.ExerciseRecordRepository
import java.time.LocalDate
import javax.inject.Inject

class GetExerciseCountInMonthUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(
        startDate: LocalDate,
        endDate: LocalDate
    ): BaseResult<List<ExerciseCount>> {
        return exerciseRecordRepository.getExerciseCountByRange(startDate, endDate)
    }
}