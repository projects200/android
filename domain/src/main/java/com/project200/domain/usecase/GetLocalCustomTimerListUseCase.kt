package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class GetLocalCustomTimerListUseCase
    @Inject
    constructor(
        private val timerRepository: TimerRepository,
    ) {
        /** 내 쓰기 직후와 화면 복귀용입니다 */
        suspend operator fun invoke(): BaseResult<List<CustomTimer>> {
            return timerRepository.getLocalCustomTimerList()
        }
    }
