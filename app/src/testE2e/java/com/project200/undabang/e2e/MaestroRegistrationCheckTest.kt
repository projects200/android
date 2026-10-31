package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.SessionDataCleaner
import com.project200.undabang.oauth.AuthStateManager
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroRegistrationCheckTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val preferences = PreferenceManager(context)
    private val stateManager = mockk<AuthStateManager>()
    private val cleaner = mockk<SessionDataCleaner>()
    private val pending = mutableListOf("pending-create", "pending-update", "pending-delete")
    private val check =
        MaestroRegistrationCheck(api, preferences, stateManager, cleaner, UnconfinedTestDispatcher(), ExerciseImageFileStore(context))

    @Before
    fun setUp() {
        preferences.completePendingLogin()
        preferences.saveMemberId(MEMBER, SUBJECT)
        every { stateManager.getCurrent() } returns authorizedState()
        coEvery { cleaner.hasStoredData() } coAnswers { pending.isNotEmpty() }
        coEvery { cleaner.clearAll() } coAnswers { pending.clear() }
        coEvery { cleaner.clearForAccountAdoption(any(), any(), any()) } coAnswers {
            assertThat(firstArg<Boolean>()).isFalse()
            if (!secondArg<() -> Boolean>().invoke() || pending.isNotEmpty()) {
                false
            } else {
                thirdArg<() -> Unit>().invoke()
                true
            }
        }
    }

    @After
    fun tearDown() {
        preferences.clearMemberId()
        preferences.completePendingLogin()
    }

    @Test
    fun `같은 회원의 실제 저장소 등록 확인은 한 번만 요청하고 pending을 보존한다`() =
        runTest {
            coEvery { api.getIsRegistered() } returns success(MEMBER).copy(code = SECRET)

            assertThat(check.requireRegistered()).isEqualTo(MEMBER)

            assertThat(preferences.getMemberId()).isEqualTo(MEMBER)
            assertThat(preferences.getRetainedMemberId()).isEqualTo(MEMBER)
            assertThat(preferences.getMemberSubject()).isEqualTo(SUBJECT)
            assertThat(preferences.getPendingLogin()).isNull()
            assertThat(pending).hasSize(3)
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
            coVerify(exactly = 1) { api.getIsRegistered() }
        }

    @Test
    fun `다른 회원의 등록 확인은 자동 승인하지 않고 기존 회원과 pending을 보존한다`() =
        runTest {
            coEvery { api.getIsRegistered() } returns success("other-member")

            assertSafeFailure("registration_unknown") { check.requireRegistered() }

            assertBlockedIdentity()
            assertThat(preferences.getPendingLogin()?.targetMemberId).isEqualTo("other-member")
            assertThat(preferences.getPendingLogin()?.confirmationId).isNotNull()
            assertThat(pending).hasSize(3)
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
            coVerify(exactly = 1) { api.getIsRegistered() }
        }

    @Test
    fun `소유 회원을 모르는 저장 데이터는 등록 응답만으로 자동 승인하거나 지우지 않는다`() =
        runTest {
            preferences.clearMemberId()
            coEvery { api.getIsRegistered() } returns success(MEMBER)

            assertSafeFailure("registration_unknown") { check.requireRegistered() }

            assertBlockedIdentity(null)
            assertThat(preferences.getPendingLogin()?.targetMemberId).isEqualTo(MEMBER)
            assertThat(preferences.getPendingLogin()?.confirmationId).isNotNull()
            assertThat(pending).hasSize(3)
            coVerify(exactly = 1) { cleaner.hasStoredData() }
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
            coVerify(exactly = 1) { api.getIsRegistered() }
        }

    @Test
    fun `인증 subject가 없거나 공백이면 등록 요청 없이 기존 데이터를 보존하고 거절한다`() =
        runTest {
            for (state in listOf(AuthState(), authorizedState(""), authorizedState(" "))) {
                every { stateManager.getCurrent() } returns state

                assertSafeFailure("registration_unknown") { check.requireRegistered() }

                assertBlockedIdentity()
                assertThat(preferences.getPendingLogin()?.subject).isNull()
                assertThat(pending).hasSize(3)
            }
            coVerify(exactly = 0) { api.getIsRegistered() }
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
        }

    @Test
    fun `미가입과 누락 데이터와 허용된 비즈니스 오류만 고정 코드로 구분한다`() =
        runTest {
            val responses =
                listOf(
                    BaseResponse(true, "OK", SECRET, GetIsRegisteredData(MEMBER, false)) to "registration_unregistered",
                    BaseResponse<GetIsRegisteredData>(true, "OK", SECRET) to "registration_response_missing",
                    BaseResponse<GetIsRegisteredData>(false, "AUTHENTICATION_FAILED", SECRET) to "registration_authentication_failed",
                    BaseResponse<GetIsRegisteredData>(false, "USER_ID_HEADER_MISSING", SECRET) to "registration_user_id_header_missing",
                    BaseResponse<GetIsRegisteredData>(false, SECRET, SECRET) to "registration_business_error",
                    BaseResponse(false, SECRET, SECRET, GetIsRegisteredData("other-member", true)) to "registration_business_error",
                )
            for ((response, reason) in responses) {
                coEvery { api.getIsRegistered() } returns response

                assertSafeFailure(reason) { check.requireRegistered() }
                assertSafeFailure(reason) { check.readRegisteredMember() }

                assertBlockedIdentity()
                assertThat(pending).hasSize(3)
            }
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = responses.size * 2) { api.getIsRegistered() }
        }

    @Test
    fun `공백 회원 응답은 채택하지 않고 기존 회원과 pending을 보존한다`() =
        runTest {
            for (member in listOf("", " ")) {
                coEvery { api.getIsRegistered() } returns success(member)

                assertSafeFailure("registration_member_id_missing") { check.requireRegistered() }
                assertSafeFailure("registration_member_id_missing") { check.readRegisteredMember() }

                assertBlockedIdentity()
                assertThat(preferences.getPendingLogin()?.confirmationId).isNull()
                assertThat(pending).hasSize(3)
            }
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
            coVerify(exactly = 4) { api.getIsRegistered() }
        }

    @Test
    fun `관찰 조회는 다른 회원과 공백 회원을 받아도 로컬 회원과 pending을 변경하지 않는다`() =
        runTest {
            coEvery { api.getIsRegistered() } returns success("other-member")
            assertThat(check.readRegisteredMember()).isEqualTo("other-member")
            coEvery { api.getIsRegistered() } returns success("")
            assertSafeFailure("registration_member_id_missing") { check.readRegisteredMember() }

            assertThat(preferences.getMemberId()).isEqualTo(MEMBER)
            assertThat(pending).hasSize(3)
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 2) { api.getIsRegistered() }
        }

    @Test
    fun `HTTP 원본 본문은 관찰기가 읽지 않고 실제 저장소가 한 번만 읽는다`() =
        runTest {
            for (status in listOf(401, 502)) {
                val body = CountingBody("{\"succeed\":false,\"code\":\"AUTHENTICATION_FAILED\",\"message\":\"$SECRET\"}")
                coEvery { api.getIsRegistered() } throws HttpException(Response.error<Unit>(status, body))

                assertSafeFailure("registration_http_$status") { check.requireRegistered() }

                assertThat(body.reads).isEqualTo(1)
                assertBlockedIdentity()
                val observationBody = CountingBody(SECRET)
                coEvery { api.getIsRegistered() } throws HttpException(Response.error<Unit>(status, observationBody))
                assertSafeFailure("registration_http_$status") { check.readRegisteredMember() }
                assertThat(observationBody.reads).isEqualTo(0)
            }
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = 4) { api.getIsRegistered() }
        }

    @Test
    fun `통신과 타임아웃과 디코딩과 알 수 없는 예외는 메시지 없이 구분한다`() =
        runTest {
            val failures =
                listOf(
                    IOException(SECRET) to "registration_network_error",
                    SocketTimeoutException(SECRET) to "registration_network_timeout",
                    JsonDataException(SECRET) to "registration_parse_error",
                    JsonEncodingException(SECRET) to "registration_parse_error",
                    EOFException(SECRET) to "registration_parse_error",
                    IllegalStateException(SECRET) to "registration_unknown",
                )
            for ((exception, reason) in failures) {
                coEvery { api.getIsRegistered() } throws exception
                assertSafeFailure(reason) { check.requireRegistered() }
                assertSafeFailure(reason) { check.readRegisteredMember() }
            }
            assertBlockedIdentity()
            assertThat(pending).hasSize(3)
            coVerify(exactly = 0) { cleaner.clearAll() }
            coVerify(exactly = failures.size * 2) { api.getIsRegistered() }
        }

    @Test
    fun `연속 실패의 종류가 바뀌고 성공해도 이전 진단이 남지 않는다`() =
        runTest {
            coEvery { api.getIsRegistered() } throws IOException(SECRET)
            assertSafeFailure("registration_network_error") { check.requireRegistered() }
            coEvery { api.getIsRegistered() } returns BaseResponse(false, "USER_ID_HEADER_MISSING", SECRET)
            assertSafeFailure("registration_user_id_header_missing") { check.requireRegistered() }
            coEvery { api.getIsRegistered() } returns success(MEMBER)
            assertThat(check.requireRegistered()).isEqualTo(MEMBER)
            coEvery { api.getIsRegistered() } returns success(MEMBER).copy(data = null)
            assertSafeFailure("registration_response_missing") { check.requireRegistered() }
            coEvery { api.getIsRegistered() } returns success(MEMBER)
            assertThat(check.readRegisteredMember()).isEqualTo(MEMBER)
            coEvery { api.getIsRegistered() } throws JsonDataException(SECRET)
            assertSafeFailure("registration_parse_error") { check.readRegisteredMember() }
            coEvery { api.getIsRegistered() } throws SocketTimeoutException(SECRET)
            assertSafeFailure("registration_network_timeout") { check.readRegisteredMember() }
            coEvery { api.getIsRegistered() } returns success(MEMBER)
            assertThat(check.readRegisteredMember()).isEqualTo(MEMBER)
            coVerify(exactly = 8) { api.getIsRegistered() }
        }

    @Test
    fun `등록 요청 취소는 원래 취소 객체를 그대로 전달한다`() {
        val cancellation = CancellationException(SECRET)
        coEvery { api.getIsRegistered() } throws cancellation

        val normal = assertThrows(CancellationException::class.java) { runTest { check.requireRegistered() } }
        val observation = assertThrows(CancellationException::class.java) { runTest { check.readRegisteredMember() } }

        assertThat(normal).isSameInstanceAs(cancellation)
        assertThat(observation).isSameInstanceAs(cancellation)
        assertBlockedIdentity()
        coVerify(exactly = 0) { cleaner.clearAll() }
        coVerify(exactly = 2) { api.getIsRegistered() }
    }

    @Test
    fun `서버 성공 후 로컬 저장소 확인 예외는 등록 HTTP 실패로 바꾸지 않는다`() {
        val persistenceFailure = IOException(SECRET)
        preferences.clearMemberId()
        coEvery { api.getIsRegistered() } returns success(MEMBER)
        coEvery { cleaner.hasStoredData() } throws persistenceFailure

        val failure = assertThrows(IOException::class.java) { runTest { check.requireRegistered() } }

        assertThat(failure).isSameInstanceAs(persistenceFailure)
        assertBlockedIdentity(null)
        assertThat(pending).hasSize(3)
        coVerify(exactly = 0) { cleaner.clearAll() }
        coVerify(exactly = 0) { cleaner.clearForAccountAdoption(any(), any(), any()) }
        coVerify(exactly = 1) { api.getIsRegistered() }
    }

    private fun assertBlockedIdentity(member: String? = MEMBER) {
        assertThat(preferences.getMemberId()).isNull()
        assertThat(preferences.getRetainedMemberId()).isEqualTo(member)
        assertThat(preferences.getMemberSubject()).isEqualTo(if (member == null) null else SUBJECT)
        assertThat(preferences.getPendingLogin()).isNotNull()
    }

    private suspend fun assertSafeFailure(
        reason: String,
        action: suspend () -> Unit,
    ) {
        val failure =
            try {
                action()
                throw AssertionError("Expected registration failure")
            } catch (failure: MaestroSetupFailure) {
                failure
            }
        assertThat(failure.reason).isEqualTo(reason)
        assertThat(failure.message).isEqualTo(reason)
        assertThat(failure.cause).isNull()
        assertThat(failure.toString()).doesNotContain(SECRET)
    }

    private fun success(memberId: String): BaseResponse<GetIsRegisteredData> =
        BaseResponse(true, "OK", SECRET, GetIsRegisteredData(memberId, true))

    private fun authorizedState(subject: String = SUBJECT): AuthState {
        val configuration =
            AuthorizationServiceConfiguration(
                Uri.parse("$ISSUER/authorize"),
                Uri.parse("$ISSUER/token"),
            )
        val request =
            AuthorizationRequest.Builder(configuration, CLIENT, ResponseTypeValues.CODE, Uri.parse("com.project200.undabang://callback"))
                .setScope("openid")
                .build()
        val response = AuthorizationResponse.Builder(request).setAuthorizationCode("test-code").build()
        val token =
            TokenResponse.Builder(response.createTokenExchangeRequest())
                .setTokenType("Bearer")
                .setAccessToken("test-access-token")
                .setIdToken(jwt(subject))
                .setRefreshToken("test-refresh-token")
                .build()
        return AuthState().apply {
            update(response, null)
            update(token, null)
        }
    }

    private fun jwt(subject: String): String {
        val payload = JSONObject().put("sub", subject).put("iss", ISSUER).put("aud", CLIENT)
        return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
            Base64.encodeToString(payload.toString().toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE) +
            ".synthetic-signature"
    }

    private class CountingBody(text: String) : ResponseBody() {
        private val buffer = Buffer().writeUtf8(text)
        var reads = 0
            private set

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = -1

        override fun source(): BufferedSource {
            reads += 1
            return buffer
        }
    }

    private companion object {
        const val MEMBER = "dedicated-member"
        const val SUBJECT = "dedicated-subject"
        const val CLIENT = "test-client"
        const val ISSUER = "https://dev-auth.example.test/pool"
        const val SECRET = "SYNTHETIC_SECRET token/member/body/message/https://private.example.test"
    }
}
