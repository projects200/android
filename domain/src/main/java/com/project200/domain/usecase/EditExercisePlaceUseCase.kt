package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class EditExercisePlaceUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(placeInfo: ExercisePlace): BaseResult<Unit> {
        return matchingRepository.editExercisePlace(placeInfo)
    }
}