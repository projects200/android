package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class AddSimpleTimerUseCase
    @Inject
    constructor(
        private val timerRepository: TimerRepository,
    ) {
        /** 새로 만든 행의 localId를 돌려줍니다 */
        suspend operator fun invoke(time: Int): BaseResult<String> {
            return timerRepository.addSimpleTimer(time)
        }
    }
