package com.project200.data.impl

import android.content.Context
import android.os.Build
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.project200.common.constants.RemoteConfigKeys
import com.project200.domain.model.UpdateInfo
import com.project200.domain.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class AppUpdateRepositoryImpl
    @Inject
    constructor(
        private val firebaseRemoteConfig: FirebaseRemoteConfig,
        @ApplicationContext private val appContext: Context,
    ) : AppUpdateRepository {
        override suspend fun getUpdateInfo(): Result<UpdateInfo> {
            return runCatching {
                // Remote Config 값 가져오기 및 활성화
                // 오프라인 등으로 fetch 실패 시 로컬 캐시/기본값(remote_config_defaults.xml)으로 계속 진행
                try {
                    val activated = firebaseRemoteConfig.fetchAndActivate().await()
                    if (activated) {
                        Timber.tag("RemoteConfig").d("RemoteConfig 가져오기 성공 및 활성화 완료")
                    } else {
                        Timber.tag("RemoteConfig").d("RemoteConfig 가져오기 실패 또는 변경 없음")
                    }
                } catch (e: Exception) {
                    Timber.tag("RemoteConfig").w(e, "fetchAndActivate 실패 - 캐시/기본값 사용")
                }

                // 값 읽어오기 (fetch 실패 시 캐시 또는 remote_config_defaults.xml 반환)
                val latestVersion = firebaseRemoteConfig.getLong(RemoteConfigKeys.LATEST_VERSION_CODE)
                val minRequiredVersion = firebaseRemoteConfig.getLong(RemoteConfigKeys.MIN_REQUIRED_VERSION_CODE)
                Timber.tag("RemoteConfig")
                    .d("$latestVersion, $minRequiredVersion")
                UpdateInfo(latestVersion, minRequiredVersion)
            }
        }

        override fun getCurrentVersionCode(): Long {
            return runCatching {
                val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            }.getOrElse { e ->
                Timber.e(e, "현재 앱 버전 코드를 가져오기 실패")
                0L
            }
        }
    }
