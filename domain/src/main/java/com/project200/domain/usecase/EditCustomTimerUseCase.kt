package com.project200.domain.usecase

import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class EditCustomTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(customTimerId: Long, title: String, steps: List<Step>) {
        timerRepository.editCustomTimer(customTimerId, title, steps)
    }
}