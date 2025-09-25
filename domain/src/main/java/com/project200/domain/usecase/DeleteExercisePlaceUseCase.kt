package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class DeleteExercisePlaceUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(placeId: Long): BaseResult<Unit> {
        return matchingRepository.deleteExercisePlace(placeId)
    }
}