package com.project200.undabang.exercise

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.ExerciseSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseSyncSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: PreferenceManager,
    ) : ExerciseSyncScheduler {
        override fun schedule() {
            val memberId = preferences.getMemberId() ?: return
            WorkManager.getInstance(context).enqueueUniqueWork(
                ExerciseSyncWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request(memberId),
            )
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(ExerciseSyncWorker.WORK_NAME)
        }

        companion object {
            internal fun request(memberId: String): OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<ExerciseSyncWorker>()
                    .setInputData(Data.Builder().putString(ExerciseSyncWorker.KEY_MEMBER_ID, memberId).build())
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                    .build()
        }
    }
