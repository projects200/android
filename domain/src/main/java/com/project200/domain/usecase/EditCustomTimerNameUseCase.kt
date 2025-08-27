package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class EditCustomTimerNameUseCase @Inject constructor(
    private val timerRepository: TimerRepository
) {
    suspend operator fun invoke(customTimerId: Long, title: String): BaseResult<Unit> {
        return timerRepository.editCustomTimerTitle(customTimerId, title)
    }
}