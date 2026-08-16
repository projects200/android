package com.project200.undabang.fcm

import android.content.Context
import com.project200.domain.manager.FcmTokenSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenSyncSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FcmTokenSyncScheduler {
        override fun schedule() = FcmTokenSyncWorker.enqueue(context)

        override fun cancel() = FcmTokenSyncWorker.cancel(context)
    }
