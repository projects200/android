package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class GetCustomTimerListUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(): BaseResult<List<CustomTimer>> {
        return timerRepository.getCustomTimerList()
    }
}