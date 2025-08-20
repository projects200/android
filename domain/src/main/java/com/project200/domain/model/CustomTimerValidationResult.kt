package com.project200.domain.model

/**
 * 유효성 검사 결과를 나타내는 Sealed Class
 */
sealed class CustomTimerValidationResult {
    object Success : CustomTimerValidationResult()
    object EmptyTitle : CustomTimerValidationResult()
    object NoSteps : CustomTimerValidationResult()
    object InvalidStepTime : CustomTimerValidationResult()
}