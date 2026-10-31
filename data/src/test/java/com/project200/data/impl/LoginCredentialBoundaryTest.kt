package com.project200.data.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthResultCallback
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginCredentialBoundaryTest {
    @Test
    fun `취소 또는 새 로그인으로 대체된 토큰 응답은 자격 증명을 저장하거나 가입 확인을 시작하지 않는다`() {
        val service = mockk<AuthorizationService>()
        val state = mockk<AuthStateManager>(relaxed = true)
        val manager = AuthManager(service, state, mockk())
        val callback = mockk<AuthResultCallback>(relaxed = true)
        var active = true
        every { callback.isActive() } answers { active }
        val exchange = slot<AuthorizationService.TokenResponseCallback>()
        every { service.performTokenRequest(any(), capture(exchange)) } just Runs
        val response = authorizationResponse()
        manager.handleAuthorizationResponse(service, response.toIntent(), callback)

        active = false
        exchange.captured.onTokenRequestCompleted(mockk<TokenResponse>(), null)

        verify(exactly = 0) { state.update(any<TokenResponse>(), any()) }
        verify(exactly = 0) { callback.onSuccess(any()) }
    }

    @Test
    fun `오래된 인증 콜백은 토큰 교환 전에도 거부한다`() {
        val service = mockk<AuthorizationService>()
        val state = mockk<AuthStateManager>(relaxed = true)
        val callback = mockk<AuthResultCallback>()
        every { callback.isActive() } returns false

        AuthManager(service, state, mockk()).handleAuthorizationResponse(service, android.content.Intent(), callback)

        verify(exactly = 0) { state.update(any<AuthorizationResponse>(), any()) }
        verify(exactly = 0) { service.performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) }
    }

    @Test
    fun `영속 토큰 삭제 실패도 강제 이탈 이벤트와 갱신 실패 결과를 전달한다`() = runTest {
        val service = mockk<AuthorizationService>()
        val state = mockk<AuthStateManager>(relaxed = true)
        val current = mockk<AuthState>()
        every { state.getCurrent() } returns current
        every { current.refreshToken } returns "test-refresh"
        every { current.lastAuthorizationResponse } returns authorizationResponse()
        every { current.createTokenRefreshRequest() } returns mockk()
        every { state.clearAuthState() } throws IllegalStateException("credential-storage")
        every { service.performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) } answers {
            secondArg<AuthorizationService.TokenResponseCallback>().onTokenRequestCompleted(
                null, AuthorizationException.TokenRequestErrors.INVALID_GRANT,
            )
        }
        val manager = AuthManager(service, state, mockk())
        val forced = async(start = CoroutineStart.UNDISPATCHED) { manager.forceLogoutFlow.first() }

        val result = manager.refreshAccessToken()
        forced.await()

        assertThat(result).isInstanceOf(TokenRefreshResult.Error::class.java)
        verify(exactly = 1) { state.clearAuthState() }
    }

    private fun authorizationResponse(): AuthorizationResponse {
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse("https://issuer.example/authorize"), Uri.parse("https://issuer.example/token"),
        )
        val request = AuthorizationRequest.Builder(
            configuration, "client", ResponseTypeValues.CODE, Uri.parse("com.project200.undabang://callback"),
        ).build()
        return AuthorizationResponse.Builder(request).setAuthorizationCode("test-code").build()
    }
}
