package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.CustomTimerDetailStepDTO
import com.project200.data.dto.CustomTimerSummaryDTO
import com.project200.data.dto.DeleteTimerRequestDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.TimerMutationResponseDTO
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.SessionDataCleaner
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import com.project200.undabang.oauth.config.CognitoConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroOfflineRecoveryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val config = mockk<CognitoConfig>()
    private val stateManager = mockk<AuthStateManager>()
    private val authManager = mockk<AuthManager>()
    private val dataCleaner = mockk<SessionDataCleaner>()
    private val commonCleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
    private val preferences = PreferenceManager(context)
    private val protocol = MaestroSetupProtocol(context)
    private val offline = MaestroOfflineFixtures(api, mockk(), mockk())

    // 실제 AuthRepository가 clearAll을 호출하면 pending 증거가 사라지는 경계를 감지합니다.
    private val pending = mutableListOf("pending-create", "pending-update", "pending-delete")
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0),
        )
    private val feature =
        MaestroFeatureRequest(
            "offline",
            "offline-timers-online",
            run.id,
            MEMBER,
            JSONObject().put("startedAt", run.startedAt.toString()).put("endedAt", run.endedAt.toString()),
        )
    private val recoveryFile = File(context.filesDir, "maestro-feature-recovery.json")
    private val resultFile = File(context.filesDir, "maestro-result.json")
    private lateinit var trusted: AuthorizationServiceConfiguration
    private lateinit var savedState: AuthState
    private var current = AuthState()
    private lateinit var bootstrap: MaestroSessionBootstrap
    private lateinit var coordinator: MaestroSetupCoordinator
    private lateinit var recoveryBytes: ByteArray

    @Before
    fun setUp() {
        protocol.deleteRequest()
        protocol.clearOutputs()
        protocol.completeFeatureRecovery()
        preferences.saveMemberId(MEMBER)
        every { config.cognitoRegion } returns "test-region"
        every { config.cognitoUserPoolId } returns "test-pool"
        every { config.cognitoAppClientId } returns CLIENT
        every { config.issuerUri } returns ISSUER
        every { config.redirectUri } returns "com.project200.undabang://callback"
        trusted =
            AuthorizationServiceConfiguration(
                AuthorizationServiceDiscovery(
                    JSONObject()
                        .put("issuer", ISSUER)
                        .put("authorization_endpoint", "$ISSUER/authorize")
                        .put("token_endpoint", "$ISSUER/token")
                        .put("jwks_uri", "$ISSUER/keys")
                        .put("response_types_supported", JSONArray().put("code"))
                        .put("subject_types_supported", JSONArray().put("public"))
                        .put("id_token_signing_alg_values_supported", JSONArray().put("RS256")),
                ),
            )
        savedState = authorizedState()
        mockkStatic(AuthorizationServiceConfiguration::class)
        every { AuthorizationServiceConfiguration.fetchFromIssuer(any(), any()) } answers {
            secondArg<AuthorizationServiceConfiguration.RetrieveConfigurationCallback>()
                .onFetchConfigurationCompleted(trusted, null)
        }
        every { stateManager.getCurrent() } answers { current }
        every { stateManager.clearAuthState() } answers { current = AuthState() }
        every { stateManager.update(any<AuthorizationResponse>(), any()) } answers {
            current.update(firstArg<AuthorizationResponse>(), null)
        }
        every { stateManager.update(any<TokenResponse>(), any()) } answers {
            current.update(firstArg<TokenResponse>(), null)
        }
        coEvery { authManager.refreshAccessToken() } coAnswers {
            val refreshed = tokens(checkNotNull(current.lastAuthorizationResponse), "refreshed-token")
            current.update(refreshed, null)
            TokenRefreshResult.Success(refreshed)
        }
        coEvery { dataCleaner.clearAll() } coAnswers { pending.clear() }
        val repository = AuthRepositoryImpl(api, preferences, stateManager, dataCleaner, UnconfinedTestDispatcher())
        bootstrap = MaestroSessionBootstrap(config, stateManager, authManager, repository, api)
        coordinator = MaestroSetupCoordinator(protocol, bootstrap, commonCleaner, mapOf("offline" to offline), offline)
        coEvery { api.getIsRegistered() } returns success(GetIsRegisteredData(MEMBER, true))
        coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
        coEvery { api.getCustomTimerList() } returns
            success(GetCustomTimerListDTO(1, listOf(CustomTimerSummaryDTO(1, run.createdTimer))))
        coEvery { api.getCustomTimer(1) } returns
            success(GetCustomTimerDetailDTO(1, run.createdTimer, 1, listOf(CustomTimerDetailStepDTO(2, "offline-step", 0, 45))))
        coEvery { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) } returns
            BaseResponse(false, "SERVER_ERROR", "private diagnostic")
        protocol.markFeatureRecovery(feature)
        recoveryBytes = recoveryFile.readBytes()
    }

    @After
    fun tearDown() {
        unmockkStatic(AuthorizationServiceConfiguration::class)
        protocol.deleteRequest()
        protocol.clearOutputs()
        protocol.completeFeatureRecovery()
        preferences.clearMemberId()
    }

    @Test
    fun `실제 인증 저장소와 서버 삭제 실패를 거쳐도 pending과 회원과 원 ownership이 남는다`() =
        runTest {
            // 이전 실패로 토큰이 없어도 회원 경계는 남아 있어야 합니다.
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("fixture_delete_failed")
            assertPreserved()
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
            coVerify(exactly = 1) { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { commonCleaner.clean(any(), any()) }
            assertThat(current.isAuthorized).isFalse()
            assertThat(current.refreshToken).isNull()

            // 보존된 회원과 동일한 자격 증명으로 재시도하면 실제 정리 완료 뒤에만 세션을 지웁니다.
            coEvery { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) } returns success(TimerMutationResponseDTO())
            writeCleanup()
            coordinator.consumeRequest()

            assertThat(result().getString("status")).isEqualTo("cleaned")
            assertThat(preferences.getMemberId()).isNull()
            assertThat(recoveryFile.exists()).isFalse()
            assertThat(pending).containsExactly("pending-create", "pending-update", "pending-delete")
            coVerify(exactly = 0) { dataCleaner.clearAll() }
            coVerify(exactly = 2) { api.getIsRegistered() }
            coVerify(exactly = 2) { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) }
            coVerify(exactly = 1) { commonCleaner.clean(any(), any()) }
        }

    @Test
    fun `실제 등록 응답이 다른 회원이면 로컬 회원 교체나 서버 삭제를 허가하지 않는다`() =
        runTest {
            coEvery { api.getIsRegistered() } returns success(GetIsRegisteredData("other-member", true))
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("member_mismatch")
            assertPreserved()
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
            assertNoFixtureCalls()
        }

    @Test
    fun `저장된 로컬 회원이 요청과 다르면 자격 증명을 복원하거나 갱신하지 않는다`() =
        runTest {
            preferences.saveMemberId("other-local-member")
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("member_mismatch")
            assertPreserved("other-local-member")
            coVerify(exactly = 0) { authManager.refreshAccessToken() }
            coVerify(exactly = 0) { api.getIsRegistered() }
            verify(exactly = 0) { stateManager.update(any<AuthorizationResponse>(), any()) }
            assertNoFixtureCalls()
        }

    @Test
    fun `cleanup ownership이 다른 실행이면 인증 복원보다 먼저 거절한다`() =
        runTest {
            writeCleanup(feature.copy(runId = "ffffffffffffffffffffffffffffffff"))

            coordinator.consumeRequest()

            assertFailure("feature_recovery_owner_mismatch")
            assertPreserved()
            coVerify(exactly = 0) { authManager.refreshAccessToken() }
            coVerify(exactly = 0) { api.getIsRegistered() }
            verify(exactly = 0) { stateManager.update(any<AuthorizationResponse>(), any()) }
            assertNoFixtureCalls()
        }

    @Test
    fun `cleanup ownership이 없으면 같은 로컬 회원만으로 서버 삭제를 허가하지 않는다`() =
        runTest {
            protocol.completeFeatureRecovery()
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("feature_recovery_missing")
            assertThat(preferences.getMemberId()).isEqualTo(MEMBER)
            assertThat(pending).hasSize(3)
            coVerify(exactly = 0) { dataCleaner.clearAll() }
            coVerify(exactly = 0) { authManager.refreshAccessToken() }
            assertNoFixtureCalls()
        }

    @Test
    fun `등록 조회 실패와 누락 본문과 미가입은 로컬 회원이나 pending을 바꾸지 않는다`() =
        runTest {
            for (response in listOf(
                BaseResponse<GetIsRegisteredData>(false, "SERVER_ERROR", "private diagnostic"),
                BaseResponse<GetIsRegisteredData>(true, "OK", ""),
                success(GetIsRegisteredData(MEMBER, false)),
                success(GetIsRegisteredData("", true)),
            )) {
                coEvery { api.getIsRegistered() } returns response
                writeCleanup()

                coordinator.consumeRequest()

                assertFailure("registration_failed")
                assertPreserved()
            }
            assertNoFixtureCalls()
        }

    @Test
    fun `등록 조회 예외도 인증 성공으로 대체하지 않고 복구 증거를 보존한다`() =
        runTest {
            coEvery { api.getIsRegistered() } throws IOException("private diagnostic")
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("setup_failed")
            assertPreserved()
            assertThat(current.isAuthorized).isFalse()
            assertNoFixtureCalls()
        }

    @Test
    fun `토큰 갱신 실패 응답이나 취소는 등록 조회와 서버 삭제를 허가하지 않는다`() =
        runTest {
            coEvery { authManager.refreshAccessToken() } returns TokenRefreshResult.NoRefreshToken
            writeCleanup()
            coordinator.consumeRequest()
            assertFailure("refresh_failed")
            assertPreserved()

            coEvery { authManager.refreshAccessToken() } throws CancellationException("private diagnostic")
            writeCleanup()
            coordinator.consumeRequest()
            assertFailure("setup_failed")
            assertPreserved()
            coVerify(exactly = 0) { api.getIsRegistered() }
            assertNoFixtureCalls()
        }

    @Test
    fun `갱신 성공 응답만 있고 현재 토큰이 쓸 수 없으면 등록이나 삭제를 하지 않는다`() =
        runTest {
            coEvery { authManager.refreshAccessToken() } coAnswers {
                current = AuthState()
                TokenRefreshResult.Success(checkNotNull(savedState.lastTokenResponse))
            }
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("refresh_failed")
            assertPreserved()
            coVerify(exactly = 0) { api.getIsRegistered() }
            assertNoFixtureCalls()
        }

    @Test
    fun `다른 client의 가져온 인증은 신뢰 검증에서 실패하고 회원을 지우지 않는다`() =
        runTest {
            writeCleanup(state = authorizedState("foreign-client"))

            coordinator.consumeRequest()

            assertFailure("untrusted_session")
            assertPreserved()
            coVerify(exactly = 0) { authManager.refreshAccessToken() }
            coVerify(exactly = 0) { api.getIsRegistered() }
            assertNoFixtureCalls()
        }

    @Test
    fun `유효한 현재 인증은 cleanup에서 재가져오지 않고 실제 갱신과 등록 검증을 거친다`() =
        runTest {
            current = authorizedState()
            writeCleanup()

            coordinator.consumeRequest()

            assertFailure("fixture_delete_failed")
            assertPreserved()
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
            verify(exactly = 0) { stateManager.update(any<AuthorizationResponse>(), any()) }
            verify(exactly = 0) { stateManager.update(any<TokenResponse>(), any()) }
        }

    @Test
    fun `관찰 중 실제 서버 읽기 실패도 회원과 pending과 ownership을 보존한다`() =
        runTest {
            current = authorizedState()
            coEvery { api.getCustomTimerList() } returns BaseResponse(false, "SERVER_ERROR", "private diagnostic")
            writeObservation()

            coordinator.consumeRequest()

            assertFailure("fixture_read_failed")
            assertPreserved()
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
            coVerify(exactly = 0) { commonCleaner.clean(any(), any()) }
            verify(exactly = 0) { stateManager.update(any<AuthorizationResponse>(), any()) }
            verify(exactly = 0) { stateManager.update(any<TokenResponse>(), any()) }
            assertThat(current.isAuthorized).isFalse()
        }

    @Test
    fun `관찰의 실제 등록 응답이 다르면 로컬 회원을 교체하지 않고 관찰을 거절한다`() =
        runTest {
            current = authorizedState()
            coEvery { api.getIsRegistered() } returns success(GetIsRegisteredData("other-member", true))
            writeObservation()

            coordinator.consumeRequest()

            assertFailure("member_mismatch")
            assertPreserved()
            coVerify(exactly = 1) { api.getIsRegistered() }
            assertNoFixtureCalls()
        }

    @Test
    fun `정상 관찰은 현재 인증을 실제 검증하고 회원과 pending과 ownership을 유지한다`() =
        runTest {
            current = authorizedState()
            coEvery { api.getCustomTimerList() } returns
                success(
                    GetCustomTimerListDTO(
                        2,
                        listOf(CustomTimerSummaryDTO(1, run.createdTimer), CustomTimerSummaryDTO(2, run.updatedTimer)),
                    ),
                )
            coEvery { api.getCustomTimer(2) } returns
                success(
                    GetCustomTimerDetailDTO(
                        2,
                        run.updatedTimer,
                        1,
                        listOf(CustomTimerDetailStepDTO(3, "offline-step", 0, 75)),
                    ),
                )
            writeObservation()

            coordinator.consumeRequest()

            assertThat(result().getString("status")).isEqualTo("observed")
            assertPreserved()
            assertThat(current.isAuthorized).isTrue()
            assertThat(current.refreshToken).isEqualTo("refreshed-token")
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
            coVerify(exactly = 0) { commonCleaner.clean(any(), any()) }
            verify(exactly = 0) { stateManager.update(any<AuthorizationResponse>(), any()) }
            verify(exactly = 0) { stateManager.update(any<TokenResponse>(), any()) }
            verify(exactly = 0) { stateManager.clearAuthState() }
        }

    @Test
    fun `일반 인증은 기존 회원 초기화와 등록 저장소의 계정 경계 정리를 유지한다`() =
        runTest {
            val memberId = bootstrap.authenticate(savedState, MEMBER)

            assertThat(memberId).isEqualTo(MEMBER)
            assertThat(preferences.getMemberId()).isEqualTo(MEMBER)
            assertThat(pending).isEmpty()
            coVerify(exactly = 1) { dataCleaner.clearAll() }
            coVerify(exactly = 1) { authManager.refreshAccessToken() }
            coVerify(exactly = 1) { api.getIsRegistered() }
        }

    private fun authorizedState(client: String = CLIENT): AuthState {
        val request =
            AuthorizationRequest.Builder(trusted, client, ResponseTypeValues.CODE, Uri.parse(config.redirectUri))
                .setScope("openid")
                .build()
        val response = AuthorizationResponse.Builder(request).setAuthorizationCode("test-code").build()
        return AuthState().apply {
            update(response, null)
            update(tokens(response, "saved-refresh-token"), null)
        }
    }

    private fun tokens(
        response: AuthorizationResponse,
        refreshToken: String,
    ): TokenResponse =
        TokenResponse.Builder(response.createTokenExchangeRequest())
            .setTokenType("Bearer")
            .setAccessToken("test-access-token")
            .setIdToken("test-id-token")
            .setRefreshToken(refreshToken)
            .build()

    private fun writeCleanup(
        owner: MaestroFeatureRequest = feature,
        state: AuthState = savedState,
    ) {
        File(context.filesDir, "maestro-request.json").writeText(
            JSONObject()
                .put("operation", "cleanup")
                .put("authState", state.jsonSerialize())
                .put("memberId", owner.memberId)
                .put("fixtureStartDate", LocalDate.of(2026, 1, 1).toString())
                .put("feature", owner.feature)
                .put("flow", owner.flow)
                .put("runId", owner.runId)
                .put("parameters", owner.parameters)
                .toString(),
        )
    }

    private fun writeObservation() {
        File(context.filesDir, "maestro-request.json").writeText(
            JSONObject()
                .put("operation", "offline-observe")
                .put("memberId", MEMBER)
                .put("runId", run.id)
                .put("flow", feature.flow)
                .put("startedAt", run.startedAt.toString())
                .put("endedAt", run.endedAt.toString())
                .put("stage", "timers-synced")
                .toString(),
        )
    }

    private fun assertFailure(reason: String) {
        val result = result()
        assertThat(result.getString("status")).isEqualTo("failed")
        assertThat(result.getString("reason")).isEqualTo(reason)
    }

    private fun assertPreserved(member: String = MEMBER) {
        assertThat(preferences.getMemberId()).isEqualTo(member)
        assertThat(pending).containsExactly("pending-create", "pending-update", "pending-delete")
        assertThat(recoveryFile.readBytes()).isEqualTo(recoveryBytes)
        coVerify(exactly = 0) { dataCleaner.clearAll() }
    }

    private fun assertNoFixtureCalls() {
        coVerify(exactly = 0) { api.getExerciseList(any()) }
        coVerify(exactly = 0) { api.getCustomTimerList() }
        coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        coVerify(exactly = 0) { commonCleaner.clean(any(), any()) }
    }

    private fun result(): JSONObject = JSONObject(resultFile.readText())

    private fun <T> success(data: T): BaseResponse<T> = BaseResponse(true, "OK", "", data)

    private companion object {
        const val MEMBER = "dedicated-member"
        const val CLIENT = "test-client"
        const val ISSUER = "https://dev-auth.example.test/pool"
    }
}
