package com.project200.domain.manager

/**
 * FCM 토큰 등록 작업의 예약과 취소를 맡습니다.
 * 구현은 WorkManager를 쓰는 app 모듈에 있습니다.
 */
interface FcmTokenSyncScheduler {
    fun schedule()

    fun cancel()
}
