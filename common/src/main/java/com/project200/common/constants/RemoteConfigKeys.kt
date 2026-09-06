package com.project200.common.constants

object RemoteConfigKeys {
    const val LATEST_VERSION_CODE = "latest_version_code"
    const val MIN_REQUIRED_VERSION_CODE = "min_required_version_code"

    // 오프라인 캐시 경로 on/off. 문제 시 앱 배포 없이 기존 네트워크 경로로 되돌리는 수단
    const val OFFLINE_CACHE_ENABLED = "offline_cache_enabled"
}
