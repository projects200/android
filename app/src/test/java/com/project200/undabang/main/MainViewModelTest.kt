package com.project200.undabang.main

import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.FcmTokenSyncResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.SyncFcmTokenUseCase
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class MainViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockCheckForUpdateUseCase: CheckForUpdateUseCase

    @MockK
    private lateinit var mockSyncFcmTokenUseCase: SyncFcmTokenUseCase

    @MockK
    private lateinit var mockNetworkMonitor: NetworkMonitor

    @MockK
    private lateinit var mockAuthManager: AuthManager

    @MockK
    private lateinit var mockAuthStateManager: AuthStateManager

    @MockK
    private lateinit var mockClearSessionUseCase: ClearSessionUseCase

    @MockK
    private lateinit var mockGetMemberIdUseCase: GetMemberIdUseCase

    private lateinit var viewModel: MainViewModel
    private lateinit var networkStateFlow: MutableSharedFlow<Boolean>
    private lateinit var forceLogoutFlow: MutableSharedFlow<Unit>

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        networkStateFlow = MutableSharedFlow()
        forceLogoutFlow = MutableSharedFlow(extraBufferCapacity = 1)
        every { mockNetworkMonitor.networkState } returns networkStateFlow
        every { mockNetworkMonitor.isCurrentlyConnected() } returns true
        every { mockAuthManager.forceLogoutFlow } returns forceLogoutFlow
        coEvery { mockClearSessionUseCase() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            checkForUpdateUseCase = mockCheckForUpdateUseCase,
            syncFcmTokenUseCase = mockSyncFcmTokenUseCase,
            networkMonitor = mockNetworkMonitor,
            authManager = mockAuthManager,
            authStateManager = mockAuthStateManager,
            clearSessionUseCase = mockClearSessionUseCase,
            getMemberIdUseCase = mockGetMemberIdUseCase,
        )
    }

    /** 토큰 상태 스텁. AuthState는 AppAuth 클래스라 mockk로 만든다.
     *  isAuthorized가 true면 회원ID 조회도 함께 스텁한다(기본값 "member-1", memberId로 override 가능). */
    private fun stubAuthState(
        isAuthorized: Boolean,
        needsRefresh: Boolean = false,
        memberId: String? = "member-1",
    ) {
        val authState = mockk<AuthState>()
        every { authState.isAuthorized } returns isAuthorized
        every { authState.needsTokenRefresh } returns needsRefresh
        every { mockAuthStateManager.getCurrent() } returns authState
        if (isAuthorized) {
            coEvery { mockGetMemberIdUseCase() } returns memberId
        }
    }

    private fun stubNoUpdate() {
        coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)
    }

    // ── 진입 라우팅 ──────────────────────────────────────────────

    @Test
    fun `진입 - 토큰이 유효하면 Content로 전이하고 FCM 등록을 1회 수행한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = false)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS

            // When: init이 진입을 시작한다 — 별도 호출 없음
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
            coVerify(exactly = 1) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `진입 - 미인증이면 Login으로 전이하고 FCM 등록을 하지 않는다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = false)

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
            coVerify(exactly = 0) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `진입 - 토큰 갱신 성공이면 Content로 전이한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns TokenRefreshResult.Success(mockk())
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
        }

    @Test
    fun `진입 - 갱신이 invalid_grant면 Login으로 전이한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns
                TokenRefreshResult.Error(AuthorizationException.TokenRequestErrors.INVALID_GRANT)

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
            coVerify(exactly = 0) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `진입 - 갱신이 네트워크 오류면 오프라인으로 Content 진입한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns
                TokenRefreshResult.Error(AuthorizationException.GeneralErrors.NETWORK_ERROR)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.FAILURE

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
        }

    @Test
    fun `진입 - 리프레시 토큰이 없으면 Login으로 전이한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns TokenRefreshResult.NoRefreshToken

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
        }

    @Test
    fun `진입 - 토큰은 유효하지만 회원ID가 없으면 Login으로 전이한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, memberId = null)

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
            coVerify(exactly = 0) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `진입 - 예상 밖 예외가 나도 크래시 없이 Login으로 폴백한다`() =
        runTest {
            // Given: apiCallBuilder 밖 스택(AuthStateManager)에서 예외
            stubNoUpdate()
            every { mockAuthStateManager.getCurrent() } throws RuntimeException("keystore corrupted")

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 코루틴은 반드시 상태 전이로 끝난다
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
        }

    // ── 업데이트 게이트 ──────────────────────────────────────────

    @Test
    fun `진입 - 강제 업데이트면 ForceUpdate로 전이하고 라우팅을 진행하지 않는다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(UpdateCheckResult.UpdateAvailable(isForceUpdate = true))

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 게이트 닫힘 — 토큰 판정·FCM 등록 미진행
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.ForceUpdate(fromReconnect = false))
            coVerify(exactly = 0) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `진입 - 선택 업데이트면 다이얼로그 플래그를 켜고 라우팅은 병행한다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(UpdateCheckResult.UpdateAvailable(isForceUpdate = false))
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.optionalUpdate.value).isTrue()
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)

            // 표시 완료 소비
            viewModel.onOptionalUpdateShown()
            assertThat(viewModel.optionalUpdate.value).isFalse()
        }

    @Test
    fun `진입 - 업데이트 확인이 실패해도 라우팅이 진행된다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns Result.failure(Exception("Network error"))
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 스플래시에 갇히지 않는다
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
        }

    // ── forceLogout ─────────────────────────────────────────────

    @Test
    fun `forceLogout - Content 중 수신하면 Login으로 전이한다`() =
        runTest {
            // Given: 정상 진입 완료
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)

            // When: 사용 중 invalid_grant (TokenAuthenticator 경유)
            forceLogoutFlow.emit(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
        }

    @Test
    fun `forceLogout - 진입 도중 수신해도 뒤늦은 Content가 Login을 덮지 않는다`() =
        runTest {
            // Given: refresh 도중 forceLogout이 발행되는 시나리오 (invalid_grant와 동일 타이밍)
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } coAnswers {
                forceLogoutFlow.emit(Unit) // 갱신 도중 강제 로그아웃 발생
                TokenRefreshResult.Error(AuthorizationException.GeneralErrors.NETWORK_ERROR) // 진입은 오프라인 경로로 완주 시도
            }
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 코루틴이 완주해 Content를 쓰려 해도 compareAndSet(Loading, Content)이 실패한다
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
        }

    // ── 재연결 ──────────────────────────────────────────────────

    @Test
    fun `재연결 - 오프라인 콜드스타트 후 첫 온라인 전환에 업데이트를 재확인한다`() =
        runTest {
            // Given: 오프라인 상태로 시작해 Content 진입 (진입 시 확인 1회)
            every { mockNetworkMonitor.isCurrentlyConnected() } returns false
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: 망이 붙어 첫 emit이 true로 옴 (false emit 없음)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 1회 + 재확인 1회
            coVerify(exactly = 2) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `재연결 - 온라인 시작이면 첫 onAvailable emit에 재확인이 헛발 실행되지 않는다`() =
        runTest {
            // Given: 온라인 시작(setUp 기본값)으로 Content 진입
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: registerNetworkCallback 직후 시스템이 쏘는 onAvailable
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 재확인 없음 (진입 1회뿐)
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }

            // 그리고 정상 재연결(false → true)은 동작한다 — 경로가 죽어서 1회인 게 아님을 고정
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 2) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `재연결 - Content가 아니면 재확인을 트리거하지 않는다`() =
        runTest {
            // Given: 미인증으로 Login에 머무는 상태
            stubNoUpdate()
            stubAuthState(isAuthorized = false)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)

            // When: offline → online 전환
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 1회뿐
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `재연결 - 재확인에서 강제 업데이트면 ForceUpdate로 전이한다`() =
        runTest {
            // Given: Content 진입 (진입 시엔 업데이트 없음, 재확인에선 강제 업데이트)
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(UpdateCheckResult.NoUpdateNeeded) andThen
                Result.success(UpdateCheckResult.UpdateAvailable(isForceUpdate = true))
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 전환
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.ForceUpdate(fromReconnect = true))
        }

    @Test
    fun `재연결 - 재확인이 실패하면 무시하고 Content를 유지한다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(UpdateCheckResult.NoUpdateNeeded) andThen
                Result.failure(Exception("Network error"))
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
        }

    // ── FCM 등록 ────────────────────────────────────────────────

    @Test
    fun `FCM 등록 - 실패하면 재연결 때 재시도한다`() =
        runTest {
            // Given: 처음엔 실패, 이후엔 성공
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns
                FcmTokenSyncResult.FAILURE andThen
                FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 재연결
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 1회 + 재시도 1회
            coVerify(exactly = 2) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `FCM 등록 - 성공하면 재연결 때 재시도하지 않는다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 1회뿐
            coVerify(exactly = 1) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `FCM 등록 - 보낼 토큰이 없으면 대기가 닫히지 않고 재연결 때 재시도한다`() =
        runTest {
            // Given: 저장된 토큰이 없어 SKIPPED로 끝난다. 그사이 토큰이 도착하면 다음엔 성공한다
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns
                FcmTokenSyncResult.SKIPPED andThen
                FcmTokenSyncResult.SUCCESS
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 재연결
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: SKIPPED는 성공이 아니므로 대기가 열려 있고 재시도가 나간다
            coVerify(exactly = 2) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `FCM 등록 - 요청이 진행 중이면 재연결이 겹쳐도 중복 발행하지 않는다`() =
        runTest {
            // Given: 등록 요청이 느리게 진행 중
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } coAnswers {
                delay(10_000) // 응답 지연 — 이 사이 재연결이 겹친다
                FcmTokenSyncResult.FAILURE
            }
            viewModel = createViewModel()
            testDispatcher.scheduler.runCurrent() // 진입 시작 → 등록 요청 발사(지연 중)

            // When: 요청이 나는 중에 offline → online 재연결
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.runCurrent()

            // Then: 실행 중 가드(registrationJob.isActive)가 두 번째 발사를 막는다
            coVerify(exactly = 1) { mockSyncFcmTokenUseCase() }
        }

    @Test
    fun `isOfflineEntry - 온라인이면 false를 반환한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            every { mockNetworkMonitor.isCurrentlyConnected() } returns true
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            assertThat(viewModel.isOfflineEntry()).isFalse()
        }

    @Test
    fun `isOfflineEntry - 오프라인이면 true를 반환한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            coEvery { mockSyncFcmTokenUseCase() } returns FcmTokenSyncResult.SUCCESS
            every { mockNetworkMonitor.isCurrentlyConnected() } returns false
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            assertThat(viewModel.isOfflineEntry()).isTrue()
        }
}
