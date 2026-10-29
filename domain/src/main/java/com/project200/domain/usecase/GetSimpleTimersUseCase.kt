package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class GetSimpleTimersUseCase
    @Inject
    constructor(
        private val timerRepository: TimerRepository,
    ) {
        /** 로컬 스냅샷을 우선 읽고 명시적 refresh만 서버 갱신을 기다립니다 */
        suspend operator fun invoke(refresh: Boolean = false): BaseResult<List<SimpleTimer>> {
            return timerRepository.getSimpleTimers(refresh)
        }
    }
