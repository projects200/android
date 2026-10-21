package com.project200.data.dto

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test

class TimerDTOTest {
    @Test
    fun `타이머 요청 식별자는 UUID 문자열로 직렬화된다`() {
        val request =
            SimpleTimerRequest(
                time = 60,
                createRequestId = "00000000-0000-0000-0000-000000000001",
                editId = "00000000-0000-0000-0000-000000000002",
                editedAt = 123L,
            )
        val adapter =
            Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
                .adapter(SimpleTimerRequest::class.java)

        val json = adapter.toJson(request)

        assertThat(json).contains("\"createRequestId\":\"00000000-0000-0000-0000-000000000001\"")
        assertThat(json).contains("\"editId\":\"00000000-0000-0000-0000-000000000002\"")
    }
}
