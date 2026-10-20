package com.project200.undabang.timer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.project200.domain.model.TimerSyncResult
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.SyncTimersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class TimerSyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val getMemberIdUseCase: GetMemberIdUseCase,
        private val syncTimersUseCase: SyncTimersUseCase,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val requestedMemberId = inputData.getString(KEY_MEMBER_ID) ?: return Result.success()
            val currentMemberId = getMemberIdUseCase() ?: return Result.success()
            if (currentMemberId != requestedMemberId) return Result.success()

            return when (syncTimersUseCase(requestedMemberId)) {
                TimerSyncResult.Success,
                TimerSyncResult.Skipped,
                -> Result.success()
                TimerSyncResult.Retry -> {
                    Timber.tag(TAG).w("타이머 동기화 재시도: 시도 ${runAttemptCount + 1}회")
                    if (TimerSyncPolicy.shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
                }
                TimerSyncResult.Failure -> Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "timer_sync"
            const val KEY_MEMBER_ID = "member_id"
            private const val TAG = "TimerSyncWorker"
        }
    }
