package com.project200.data.utils

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.PreferenceManager
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.openid.appauth.AuthState
import net.openid.appauth.TokenResponse
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExerciseSyncAccountGuardTest {
    private val member = "00000000-0000-0000-0000-000000000652"
    private val preferences = PreferenceManager(RuntimeEnvironment.getApplication())
    private val files = ExerciseImageFileStore(RuntimeEnvironment.getApplication())
    private val auth = mockk<AuthStateManager>()
    private val state = mockk<AuthState>()
    private val authManager = mockk<AuthManager>()
    private val chain = mockk<Interceptor.Chain>()
    private lateinit var guard: ExerciseSyncAccountGuard
    private lateinit var account: ExerciseSyncAccount
    private var token: String? = null

    @Before
    fun setUp() {
        preferences.saveMemberId(member, member)
        token = jwt(member, "old")
        every { auth.getCurrent() } returns state
        every { state.idToken } answers { token }
        guard = ExerciseSyncAccountGuard(preferences, files, auth)
        account = requireNotNull(guard.capture(member))
    }

    private fun jwt(subject: String, signature: String = "new") = "e30." + Base64.encodeToString(
        "{\"sub\":\"$subject\"}".toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    ) + ".$signature"

    private fun request(tagged: Boolean = true): Request = Request.Builder().url("https://example.test/api/v1/exercises")
        .header("Authorization", "Bearer ${jwt(member, "old")}")
        .apply { if (tagged) tag(ExerciseSyncAccount::class.java, account) }.build()

    private fun response(request: Request): Response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(401).message("Unauthorized").build()

    private fun intercept(request: Request) {
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers { response(firstArg()) }
        TokenInterceptor(auth, mockk(relaxed = true), guard).intercept(chain)
    }

    private fun assertBlocked() {
        try {
            intercept(request())
            throw AssertionError("다른 세션의 토큰을 전송했습니다")
        } catch (_: ExerciseSyncAuthenticationException) {
            verify(exactly = 0) { chain.proceed(any()) }
        }
    }

    @Test
    fun `현재 회원이 바뀌면 실제 interceptor는 proceed를 호출하지 않는다`() {
        preferences.saveMemberId("other", "other")
        token = jwt("other")
        assertBlocked()
    }

    @Test
    fun `회원 preference가 늦게 바뀌어도 새 토큰의 subject가 다르면 전송하지 않는다`() {
        token = jwt("other")
        assertBlocked()
    }

    @Test
    fun `이관된 회원은 회원ID와 달라도 저장된 subject의 토큰을 전송한다`() {
        preferences.saveMemberId(member, "migrated-subject")
        token = jwt("migrated-subject")
        account = requireNotNull(guard.capture(member))

        intercept(request())

        verify(exactly = 1) { chain.proceed(match { it.header("Authorization") == "Bearer $token" }) }
    }

    @Test
    fun `이관된 회원의 preference가 남아 있으면 다른 계정과 회원ID subject를 모두 거부한다`() {
        preferences.saveMemberId(member, "migrated-subject")
        token = jwt("migrated-subject")
        account = requireNotNull(guard.capture(member))

        for (otherSubject in listOf("other", member)) {
            token = jwt(otherSubject)
            assertBlocked()
        }
    }

    @Test
    fun `저장된 subject가 없는 기존 설치는 회원ID와 같은 subject만 허용한다`() {
        preferences.saveMemberId(member, null)
        assertThat(preferences.getMemberSubject()).isNull()
        token = jwt(member)
        account = requireNotNull(guard.capture(member))

        assertThat(guard.accepts(account, jwt("migrated-subject"))).isFalse()
        assertThat(guard.accepts(account, jwt("other"))).isFalse()
        intercept(request())

        verify(exactly = 1) { chain.proceed(match { it.header("Authorization") == "Bearer $token" }) }
    }

    @Test
    fun `회원 subject는 preference를 다시 열어도 남고 회원ID를 지울 때 함께 지워진다`() {
        preferences.saveMemberId(member, "migrated-subject")
        val reopened = PreferenceManager(RuntimeEnvironment.getApplication())
        assertThat(reopened.getMemberSubject()).isEqualTo("migrated-subject")

        preferences.clearMemberId()

        assertThat(reopened.getMemberId()).isNull()
        assertThat(reopened.getMemberSubject()).isNull()
        assertThat(guard.isCurrent(account)).isFalse()
    }

    @Test
    fun `인증 누락과 잘못된 JWT는 이전 Authorization 헤더로 폴백하지 않는다`() {
        for (invalid in listOf(null, "broken", "e30.e30.signature")) {
            token = invalid
            assertBlocked()
        }
    }

    @Test
    fun `같은 계정이라도 강제 이탈이나 파일 세션 초기화 뒤에는 오래된 태그를 전송하지 않는다`() {
        preferences.invalidateExerciseSession()
        assertBlocked()
        account = requireNotNull(guard.capture(member))
        files.invalidateSession()
        assertBlocked()
    }

    @Test
    fun `같은 계정의 토큰을 한 번 읽어 검사한 값 그대로 전송한다`() {
        token = jwt(member)
        intercept(request())
        verify(exactly = 1) { chain.proceed(match { it.header("Authorization") == "Bearer $token" }) }
    }

    @Test
    fun `태그 없는 기존 API는 현재 토큰을 사용하는 기존 동작을 유지한다`() {
        token = jwt("other")
        preferences.saveMemberId("other", "other")
        intercept(request(tagged = false))
        verify(exactly = 1) { chain.proceed(match { it.header("Authorization") == "Bearer $token" }) }
    }

    @Test
    fun `authenticator의 토큰 변경 빠른 경로도 다른 계정이면 재시도하지 않는다`() {
        token = jwt("other")
        val retried = TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))
        assertThat(retried).isNull()
        coVerify(exactly = 0) { authManager.refreshAccessToken() }
    }

    @Test
    fun `같은 계정에서 이미 갱신된 토큰은 authenticator가 재사용할 수 있다`() {
        token = jwt(member, "refreshed")
        val retried = TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))
        assertThat(retried?.header("Authorization")).isEqualTo("Bearer $token")
        coVerify(exactly = 0) { authManager.refreshAccessToken() }
    }

    @Test
    fun `refresh 결과가 다른 계정이면 preference가 그대로여도 재전송하지 않는다`() {
        val refreshed = mockk<TokenResponse>()
        every { refreshed.idToken } returns jwt("other")
        coEvery { authManager.refreshAccessToken() } coAnswers {
            token = jwt("other")
            TokenRefreshResult.Success(refreshed)
        }
        assertThat(TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))).isNull()
    }

    @Test
    fun `refresh 도중 세션 revision이 바뀌면 같은 계정 토큰도 재시도하지 않는다`() {
        val refreshed = mockk<TokenResponse>()
        every { refreshed.idToken } returns jwt(member, "refreshed")
        coEvery { authManager.refreshAccessToken() } coAnswers {
            preferences.invalidateExerciseSession()
            token = refreshed.idToken
            TokenRefreshResult.Success(refreshed)
        }
        assertThat(TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))).isNull()
    }

    @Test
    fun `같은 계정의 정상 refresh는 검사한 새 ID 토큰으로 재시도한다`() {
        val refreshed = mockk<TokenResponse>()
        every { refreshed.idToken } returns jwt(member, "refreshed")
        coEvery { authManager.refreshAccessToken() } coAnswers {
            token = refreshed.idToken
            TokenRefreshResult.Success(refreshed)
        }
        val retried = TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))
        assertThat(retried?.header("Authorization")).isEqualTo("Bearer ${refreshed.idToken}")
        assertThat(retried?.tag(ExerciseSyncAccount::class.java)).isEqualTo(account)
    }

    @Test
    fun `refresh 반환 토큰만 다른 계정이어도 현재 저장 토큰을 믿고 재전송하지 않는다`() {
        val refreshed = mockk<TokenResponse>()
        every { refreshed.idToken } returns jwt("other")
        coEvery { authManager.refreshAccessToken() } returns TokenRefreshResult.Success(refreshed)
        assertThat(TokenAuthenticator(auth, authManager, guard).authenticate(null, response(request()))).isNull()
    }
}
