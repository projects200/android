package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import javax.inject.Inject

class EditSimpleTimerUseCase @Inject constructor(
    private val timerRepository: com.project200.domain.repository.TimerRepository
) {
    suspend operator fun invoke(
        simpleTimer: SimpleTimer
    ): BaseResult<Unit> {
        return timerRepository.editSimpleTimer(
            simpleTimer= simpleTimer
        )
    }
}