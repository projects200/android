package com.project200.undabang

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kakao.vectormap.KakaoMapSdk
import com.project200.common.di.IoDispatcher
import com.project200.domain.manager.ExerciseCacheCleaner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ApplicationClass : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var exerciseCacheCleaner: ExerciseCacheCleaner

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val applicationScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    /** WorkManager가 Hilt로 Worker를 생성하도록 팩토리를 넘깁니다 */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // E2E 세션의 인증 코드와 토큰이 기존 디버그 로그에 남지 않도록 합니다
        if (BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "e2e") Timber.plant(Timber.DebugTree())
        Timber.d("Timber")

        createNotificationChannel()

        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // Crashlytics 디버그 모드에서는 비활성화
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        applicationScope.launchExerciseCacheCleanup(exerciseCacheCleaner)
    }

    /**
     * 기본 알림 채널을 생성합니다.
     */
    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                getString(R.string.fcm_channel_id),
                getString(R.string.fcm_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }
}

/** 앱 초기화는 정리를 기다리지 않습니다. 일반 실패는 격리하고 취소는 작업의 취소 상태로 남깁니다 */
internal fun CoroutineScope.launchExerciseCacheCleanup(cleaner: ExerciseCacheCleaner): Job =
    launch {
        try {
            cleaner.cleanExpiredCache()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "오래된 운동 캐시 정리 실패")
        }
    }
