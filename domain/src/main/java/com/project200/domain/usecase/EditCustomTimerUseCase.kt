package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class EditCustomTimerUseCase
    @Inject
    constructor(
        private val timerRepository: TimerRepository,
    ) {
        /**
         * 이름과 스텝을 함께 저장합니다.
         *
         * 로컬이 원본이라 이름만 바뀐 경우와 스텝이 바뀐 경우를 나누지 않습니다.
         * 무엇을 서버에 어떻게 보낼지는 전송 워커가 정합니다
         */
        suspend operator fun invoke(
            localId: String,
            title: String,
            steps: List<Step>,
        ): BaseResult<Unit> {
            return timerRepository.editCustomTimer(localId, title, steps)
        }
    }
