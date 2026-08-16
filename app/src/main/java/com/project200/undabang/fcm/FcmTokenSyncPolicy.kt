package com.project200.undabang.fcm

/** FcmTokenSyncWorker의 재시도 판단. 안드로이드 의존성이 없어 단위 테스트로 검증합니다 */
object FcmTokenSyncPolicy {
    /** 전송 실패 시 총 시도 횟수 상한 */
    const val MAX_ATTEMPT_COUNT = 10

    /** runAttemptCount는 WorkManager가 주는 값으로 첫 실행이 0입니다 */
    fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount + 1 < MAX_ATTEMPT_COUNT

    /**
     * 토큰 갱신 실패 시 재시도 여부입니다.
     * invalid_grant는 리프레시 토큰이 무효한 상태라 재시도해도 결과가 같습니다
     */
    fun shouldRetryAfterRefreshFailure(
        invalidGrant: Boolean,
        runAttemptCount: Int,
    ): Boolean = !invalidGrant && shouldRetry(runAttemptCount)
}
