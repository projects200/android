package com.project200.domain.usecase

import com.project200.domain.model.Step
import javax.inject.Inject
import com.project200.domain.model.ValidationResult

/**
 * 커스텀 타이머 생성 전 유효성을 검사하는 유스케이스
 */
class ValidateCustomTimerUseCase @Inject constructor() {
    operator fun invoke(title: String, steps: List<Step>): ValidationResult {
        if (title.isBlank()) {
            return ValidationResult.EmptyTitle
        }
        if (steps.isEmpty()) {
            return ValidationResult.NoSteps
        }
        if (steps.any { it.time <= 0 }) {
            return ValidationResult.InvalidStepTime
        }
        return ValidationResult.Success
    }
}