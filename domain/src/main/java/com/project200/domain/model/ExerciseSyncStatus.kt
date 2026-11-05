package com.project200.domain.model

/** 본문 스냅샷과 분리한 상태입니다. 관찰자는 표시 중인 본문을 다시 읽지 않습니다. */
data class ExerciseSyncStatus(
    val recordId: String,
    val canShowBodyRetry: Boolean = false,
    val remoteDeleted: Boolean = false,
    val complete: Boolean = false,
    val remoteDeletionToken: String? = null,
)

fun canShowExerciseBodyRetry(
    validatedInternet: Boolean,
    authenticatedAccount: Boolean,
    retryableBodyFailure: Boolean,
    workerBusy: Boolean,
): Boolean = validatedInternet && authenticatedAccount && retryableBodyFailure && !workerBusy

data class ExercisePointNotice(
    val recordId: String,
    val earnedPoints: Int,
)
