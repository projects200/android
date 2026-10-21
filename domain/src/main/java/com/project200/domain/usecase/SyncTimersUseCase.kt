package com.project200.domain.usecase

import com.project200.domain.model.TimerSyncResult
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class SyncTimersUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
) {
    suspend operator fun invoke(memberId: String): TimerSyncResult =
        timerRepository.syncPendingTimers(memberId)
}
