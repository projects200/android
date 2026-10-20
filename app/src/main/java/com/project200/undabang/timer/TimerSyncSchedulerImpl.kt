package com.project200.undabang.timer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.TimerSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerSyncSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferenceManager: PreferenceManager,
    ) : TimerSyncScheduler {
        override fun schedule() {
            val memberId = preferenceManager.getMemberId() ?: return
            val input = Data.Builder().putString(TimerSyncWorker.KEY_MEMBER_ID, memberId).build()
            val request =
                OneTimeWorkRequestBuilder<TimerSyncWorker>()
                    .setInputData(input)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private companion object {
            const val WORK_NAME = TimerSyncWorker.WORK_NAME
            const val BACKOFF_DELAY_SECONDS = 30L
        }
    }
