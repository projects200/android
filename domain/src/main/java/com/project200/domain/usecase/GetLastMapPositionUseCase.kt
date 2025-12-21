package com.project200.domain.usecase

import com.project200.domain.model.MapPosition
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class GetLastMapPositionUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(): MapPosition? {
        return matchingRepository.getLastMapPosition()
    }
}