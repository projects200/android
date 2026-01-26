package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import javax.inject.Inject

class ObserveOpponentStatusUseCase @Inject constructor(
    private val repository: ChatSocketRepository
) {
    operator fun invoke() = repository.opponentStatusChanges
}
