package com.project200.undabang.timer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimerSyncPolicyTest {
    @Test
    fun `마지막 시도 전까지 재시도한다`() {
        assertThat(TimerSyncPolicy.shouldRetry(TimerSyncPolicy.MAX_ATTEMPT_COUNT - 2)).isTrue()
        assertThat(TimerSyncPolicy.shouldRetry(TimerSyncPolicy.MAX_ATTEMPT_COUNT - 1)).isFalse()
    }
}
