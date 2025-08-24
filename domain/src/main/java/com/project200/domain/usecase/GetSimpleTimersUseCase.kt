package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class GetSimpleTimersUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(): BaseResult<List<SimpleTimer>> {
        return timerRepository.getSimpleTimers()
    }
}