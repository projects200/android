package com.project200.undabang.exercise

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.project200.domain.model.ExerciseSyncResult
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.SyncExercisesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ExerciseSyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val getMemberIdUseCase: GetMemberIdUseCase,
        private val syncExercisesUseCase: SyncExercisesUseCase,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            val member = inputData.getString(KEY_MEMBER_ID) ?: return Result.success()
            if (getMemberIdUseCase() != member) return Result.success()
            return when (syncExercisesUseCase(member)) {
                ExerciseSyncResult.Success, ExerciseSyncResult.Skipped -> Result.success()
                ExerciseSyncResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
                ExerciseSyncResult.Failure -> Result.failure()
            }
        }

        companion object {
            const val WORK_NAME = "exercise_sync"
            const val KEY_MEMBER_ID = "member_id"
            const val MAX_ATTEMPTS = 5
        }
    }
