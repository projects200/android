package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
import javax.inject.Inject

class EditCustomTimerUseCase @Inject constructor(
    private val timerRepository: TimerRepository,
    private val editCustomTimerNameUseCase: EditCustomTimerNameUseCase
) {
    suspend operator fun invoke(
        hasTitleChanged: Boolean,
        hasStepsChanged: Boolean,
        customTimerId: Long,
        title: String,
        steps: List<Step>
    ): BaseResult<Unit> {
        return when {
            hasStepsChanged -> timerRepository.editCustomTimer(customTimerId, title, steps)
            hasTitleChanged -> editCustomTimerNameUseCase(customTimerId, title)
            else -> BaseResult.Error(message = null)
        }
    }
}