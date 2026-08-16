package com.project200.undabang.main

import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var mockFcmTokenSyncScheduler: FcmTokenSyncScheduler

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
        every { mockFcmTokenSyncScheduler.schedule() } just Runs
        coEvery { mockClearSessionUseCase() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            checkForUpdateUseCase = mockCheckForUpdateUseCase,
            fcmTokenSyncScheduler = mockFcmTokenSyncScheduler,
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
    fun `진입 - 토큰이 유효하면 Content로 전이하고 FCM 등록을 1회 예약한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = false)

            // When: init이 진입을 시작한다 — 별도 호출 없음
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Content)
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
            verify(exactly = 1) { mockFcmTokenSyncScheduler.schedule() }
        }

    @Test
    fun `진입 - 미인증이면 Login으로 전이하고 FCM 등록을 예약하지 않는다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = false)

            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.entryState.value).isEqualTo(EntryState.Login)
            verify(exactly = 0) { mockFcmTokenSyncScheduler.schedule() }
        }

    @Test
    fun `진입 - 토큰 갱신 성공이면 Content로 전이한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns TokenRefreshResult.Success(mockk())

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
            verify(exactly = 0) { mockFcmTokenSyncScheduler.schedule() }
        }

    @Test
    fun `진입 - 갱신이 네트워크 오류면 오프라인으로 Content 진입한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true, needsRefresh = true)
            coEvery { mockAuthManager.refreshAccessToken() } returns
                TokenRefreshResult.Error(AuthorizationException.GeneralErrors.NETWORK_ERROR)

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
            verify(exactly = 0) { mockFcmTokenSyncScheduler.schedule() }
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
            verify(exactly = 0) { mockFcmTokenSyncScheduler.schedule() }
        }

    @Test
    fun `진입 - 선택 업데이트면 다이얼로그 플래그를 켜고 라우팅은 병행한다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(UpdateCheckResult.UpdateAvailable(isForceUpdate = false))
            stubAuthState(isAuthorized = true)

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
    fun `FCM 등록 - 재연결에는 다시 예약하지 않는다`() =
        runTest {
            // Given: 진입에서 1회 예약된 상태. 재시도는 WorkManager 백오프가 맡는다
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 재연결
            networkStateFlow.emit(false)
            networkStateFlow.emit(true)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 진입 1회뿐 — ViewModel은 재시도를 손으로 다시 구현하지 않는다
            verify(exactly = 1) { mockFcmTokenSyncScheduler.schedule() }
        }

    @Test
    fun `isOfflineEntry - 온라인이면 false를 반환한다`() =
        runTest {
            // Given
            stubNoUpdate()
            stubAuthState(isAuthorized = true)
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
            every { mockNetworkMonitor.isCurrentlyConnected() } returns false
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            assertThat(viewModel.isOfflineEntry()).isTrue()
        }
}
