package com.project200.undabang.fcm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.project200.domain.model.FcmTokenSyncResult
import com.project200.domain.usecase.SyncFcmTokenUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 갱신된 FCM 토큰을 서버에 반영하는 워커입니다
 * onNewToken()은 백그라운드나 프로세스 기동 직후에도 불려 네트워크와 로그인 상태를 확신할 수 없습니다
 * 전송을 워커로 미뤄 연결이 잡힌 뒤에 보내고 실패하면 다시 시도합니다
 */
@HiltWorker
class FcmTokenSyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val syncFcmTokenUseCase: SyncFcmTokenUseCase,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val syncResult = syncFcmTokenUseCase()
            Timber.tag(TAG).d("FCM 토큰 전송 결과: $syncResult (시도 ${runAttemptCount + 1}회)")

            return when (syncResult) {
                FcmTokenSyncResult.SUCCESS -> Result.success()
                // 로그인 전이거나 보낼 토큰이 없는 상태 - 재시도해도 같으므로 성공 처리
                FcmTokenSyncResult.SKIPPED -> Result.success()
                FcmTokenSyncResult.FAILURE ->
                    if (FcmTokenSyncPolicy.shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
            }
        }

        companion object {
            private const val TAG = "FcmTokenSyncWorker"
            const val WORK_NAME = "fcm_token_sync"
            private const val BACKOFF_DELAY_SECONDS = 30L

            /**
             * 토큰 등록을 예약합니다
             * 대기 중에 토큰이 다시 갱신되면 새 요청으로 바꿔 백오프를 처음부터 다시 셉니다
             * 워커는 실행 시점에 저장소에서 토큰을 읽으므로 마지막 값이 올라갑니다
             */
            fun enqueue(context: Context) {
                val constraints =
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<FcmTokenSyncWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            BACKOFF_DELAY_SECONDS,
                            TimeUnit.SECONDS,
                        )
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
