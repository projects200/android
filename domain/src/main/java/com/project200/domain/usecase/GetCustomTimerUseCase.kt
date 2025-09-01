package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class GetCustomTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(customTimerId: Long): BaseResult<CustomTimer> {
        return timerRepository.getCustomTimer(customTimerId)
    }
}