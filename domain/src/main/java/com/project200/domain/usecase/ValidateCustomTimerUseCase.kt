package com.project200.domain.usecase

import com.project200.domain.model.CustomTimerValidationResult
import com.project200.domain.model.Step
import javax.inject.Inject

/**
 * 커스텀 타이머 생성 전 유효성을 검사하는 유스케이스
 */
class ValidateCustomTimerUseCase @Inject constructor() {
    operator fun invoke(title: String, steps: List<Step>): CustomTimerValidationResult {
        if (title.isBlank()) {
            return CustomTimerValidationResult.EmptyTitle
        }
        if (steps.isEmpty()) {
            return CustomTimerValidationResult.NoSteps
        }
        if (steps.any { it.time < 5 }) { // 5초 미만의 스텝이 있는지 검사
            return CustomTimerValidationResult.InvalidStepTime
        }
        if (steps.any() { it.name.isBlank() }) {
            return CustomTimerValidationResult.EmptyStepName
        }
        return CustomTimerValidationResult.Success
    }
}