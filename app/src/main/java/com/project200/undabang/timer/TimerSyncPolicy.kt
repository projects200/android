package com.project200.undabang.timer

/** TimerSyncWorker의 재시도 횟수를 제한합니다 */
object TimerSyncPolicy {
    const val MAX_ATTEMPT_COUNT = 10

    fun shouldRetry(runAttemptCount: Int): Boolean = runAttemptCount + 1 < MAX_ATTEMPT_COUNT
}
