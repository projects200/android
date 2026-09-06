package com.project200.data.datasource

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.project200.common.constants.RemoteConfigKeys
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote Config로 켜고 끄는 기능 플래그를 읽습니다.
 *
 * fetch는 앱 진입의 업데이트 확인(`AppUpdateRepositoryImpl.getUpdateInfo`)이 이미 수행합니다.
 * 여기서는 활성화된 값만 읽습니다
 *
 * 진입 전이거나 fetch가 실패한 기기에서는 `remote_config_defaults.xml`의 기본값이 읽힙니다.
 * 기본값이 곧 최악 상황의 동작이라 꺼짐으로 두었습니다
 */
@Singleton
class FeatureFlagDataSource
    @Inject
    constructor(
        private val firebaseRemoteConfig: FirebaseRemoteConfig,
    ) {
        fun isOfflineCacheEnabled(): Boolean = firebaseRemoteConfig.getBoolean(RemoteConfigKeys.OFFLINE_CACHE_ENABLED)
    }
