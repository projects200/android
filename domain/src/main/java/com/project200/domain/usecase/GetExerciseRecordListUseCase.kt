package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.repository.ExerciseRecordRepository
import java.time.LocalDate
import javax.inject.Inject

class GetExerciseRecordListUseCase @Inject constructor(
    private val exerciseRecordRepository: ExerciseRecordRepository
) {
    suspend operator fun invoke(date: LocalDate, refresh: Boolean = false, localOnly: Boolean = false): BaseResult<List<ExerciseListItem>> {
        return exerciseRecordRepository.getExerciseRecordList(date, refresh, localOnly)
    }
}