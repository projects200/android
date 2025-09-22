package com.project200.domain.usecase

import com.project200.domain.model.MapPosition
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class SaveLastMapPositionUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(mapPosition: MapPosition) {
        matchingRepository.saveLastMapPosition(mapPosition)
    }
}