package com.project200.undabang.fcm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FcmTokenSyncPolicyTest {
    @Test
    fun `첫 실행에서 실패하면 재시도한다`() {
        assertThat(FcmTokenSyncPolicy.shouldRetry(0)).isTrue()
    }

    @Test
    fun `상한 직전까지는 재시도한다`() {
        val lastRetryableAttempt = FcmTokenSyncPolicy.MAX_ATTEMPT_COUNT - 2

        assertThat(FcmTokenSyncPolicy.shouldRetry(lastRetryableAttempt)).isTrue()
    }

    @Test
    fun `상한에 닿으면 재시도하지 않는다`() {
        val lastAttempt = FcmTokenSyncPolicy.MAX_ATTEMPT_COUNT - 1

        assertThat(FcmTokenSyncPolicy.shouldRetry(lastAttempt)).isFalse()
    }

    @Test
    fun `상한을 넘긴 뒤에도 재시도하지 않는다`() {
        assertThat(FcmTokenSyncPolicy.shouldRetry(FcmTokenSyncPolicy.MAX_ATTEMPT_COUNT)).isFalse()
    }
}
