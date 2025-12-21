package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.repository.MatchingRepository
import java.time.LocalDate
import javax.inject.Inject

class GetMatchingMemberExerciseUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(
        memberId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BaseResult<List<ExerciseCount>> {
        return matchingRepository.getMemberExerciseDates(memberId, startDate, endDate)
    }

}