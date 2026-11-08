package com.project200.domain.manager

import kotlinx.coroutines.flow.Flow

interface ExerciseSyncScheduler {
    fun schedule()
    fun cancel()
    fun observeBusy(): Flow<Boolean>
    suspend fun isBusy(): Boolean
    suspend fun retry(memberId: String): Boolean
}
