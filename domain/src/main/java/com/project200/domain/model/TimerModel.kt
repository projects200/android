package com.project200.domain.model

data class CustomTimer(
    val id: Long,
    val name: String
)

data class SimpleTimer(
    val id: String,
    val order: Int,
    val time: Int
)

data class Step(
    val id: Long = -1,
    val order: Int,
    val time: Int,
    val name: String
)

/**
 * 유효성 검사 결과를 나타내는 Sealed Class
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    object EmptyTitle : ValidationResult()
    object NoSteps : ValidationResult()
    object InvalidStepTime : ValidationResult()
}