package com.project200.domain.model

sealed interface ExerciseSyncResult {
    data object Success : ExerciseSyncResult
    data object Retry : ExerciseSyncResult
    data object Failure : ExerciseSyncResult
    data object Skipped : ExerciseSyncResult
}
