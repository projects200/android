package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class AddSimpleTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(time: Int): BaseResult<Long> {
        return timerRepository.addSimpleTimer(time)
    }
}