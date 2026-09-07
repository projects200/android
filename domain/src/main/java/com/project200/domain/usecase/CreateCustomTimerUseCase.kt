package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class CreateCustomTimerUseCase
    @Inject
    constructor(
        private val timerRepository: TimerRepository,
    ) {
        /** 새로 만든 행의 localId를 돌려줍니다 */
        suspend operator fun invoke(
            title: String,
            steps: List<Step>,
        ): BaseResult<String> {
            return timerRepository.createCustomTimer(title, steps)
        }
    }
