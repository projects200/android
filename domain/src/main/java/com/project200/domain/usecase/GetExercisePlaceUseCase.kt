package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class GetExercisePlaceUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(): BaseResult<List<ExercisePlace>> {
        return matchingRepository.getExercisePlaces()
    }
}