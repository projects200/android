package com.project200.domain.model

sealed interface TimerSyncResult {
    data object Success : TimerSyncResult
    data object Skipped : TimerSyncResult
    data object Retry : TimerSyncResult
    data object Failure : TimerSyncResult
}
