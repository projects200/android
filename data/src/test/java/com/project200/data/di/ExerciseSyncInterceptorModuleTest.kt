package com.project200.data.di

import com.google.common.truth.Truth.assertThat
import com.project200.data.utils.TokenAuthenticator
import com.project200.data.utils.TokenInterceptor
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Test

class ExerciseSyncInterceptorModuleTest {
    private val authenticator = mockk<TokenAuthenticator>()
    private val token = mockk<TokenInterceptor>()
    private val logging = HttpLoggingInterceptor()

    @Test
    fun `일반 빌드의 빈 추가 바인딩은 기존 클라이언트 순서와 timeout을 유지한다`() {
        val client = NetworkModule.provideOkHttpClient(authenticator, logging, token, emptySet())

        assertThat(client.interceptors).hasSize(2)
        assertThat(client.interceptors.last()).isSameInstanceAs(token)
        assertThat(client.authenticator).isSameInstanceAs(authenticator)
        assertThat(client.connectTimeoutMillis).isEqualTo(30_000)
        assertThat(client.readTimeoutMillis).isEqualTo(30_000)
        assertThat(client.writeTimeoutMillis).isEqualTo(30_000)
        assertThat(client.pingIntervalMillis).isEqualTo(30_000)
        assertThat(client.networkInterceptors).isEmpty()
    }

    @Test
    fun `명시적으로 기여한 인터셉터만 토큰 검사 뒤에 설치한다`() {
        val contribution = mockk<Interceptor>()
        val client = NetworkModule.provideOkHttpClient(authenticator, logging, token, setOf(contribution))

        assertThat(client.interceptors).hasSize(3)
        assertThat(client.interceptors[1]).isSameInstanceAs(token)
        assertThat(client.interceptors[2]).isSameInstanceAs(contribution)
        assertThat(client.networkInterceptors).isEmpty()
    }
}
