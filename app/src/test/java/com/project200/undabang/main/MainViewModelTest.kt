package com.project200.undabang.main

import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.LoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    private lateinit var mockLoginUseCase: LoginUseCase

    @MockK
    private lateinit var mockNetworkMonitor: NetworkMonitor

    private lateinit var viewModel: MainViewModel
    private lateinit var networkStateFlow: MutableSharedFlow<Boolean>

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        networkStateFlow = MutableSharedFlow()
        every { mockNetworkMonitor.networkState } returns networkStateFlow
        every { mockNetworkMonitor.isCurrentlyConnected() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            checkForUpdateUseCase = mockCheckForUpdateUseCase,
            loginUseCase = mockLoginUseCase,
            networkMonitor = mockNetworkMonitor,
        )
    }

    @Test
    fun `오프라인 콜드스타트 - 첫 온라인 전환에 업데이트를 재확인한다`() =
        runTest {
            // Given: 오프라인 상태로 시작
            every { mockNetworkMonitor.isCurrentlyConnected() } returns false
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)
            viewModel = createViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.onContentShown()

            // When: 망이 붙어 첫 emit이 true로 옴 (false emit 없음)
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 재확인이 일어난다
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `온라인 콜드스타트 - 첫 onAvailable emit에 재확인이 헛발 실행되지 않는다`() =
        runTest {
            // Given: 온라인 상태로 시작 (setUp 기본값)
            viewModel = createViewModel()
            testDispatcher.scheduler.runCurrent()
            viewModel.onContentShown()

            // When: registerNetworkCallback 직후 시스템이 쏘는 onAvailable
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 재확인 없음
            coVerify(exactly = 0) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `checkForUpdate - 업데이트가 필요하면 UpdateAvailable 결과를 반환한다`() =
        runTest {
            // Given
            val updateResult = UpdateCheckResult.UpdateAvailable(isForceUpdate = false)
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(updateResult)

            viewModel = createViewModel()

            // When
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.updateCheckResult.value).isEqualTo(updateResult)
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `checkForUpdate - 강제 업데이트가 필요하면 isForceUpdate가 true인 결과를 반환한다`() =
        runTest {
            // Given
            val updateResult = UpdateCheckResult.UpdateAvailable(isForceUpdate = true)
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(updateResult)

            viewModel = createViewModel()

            // When
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.updateCheckResult.value
            assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
            assertThat((result as UpdateCheckResult.UpdateAvailable).isForceUpdate).isTrue()
        }

    @Test
    fun `checkForUpdate - 업데이트가 불필요하면 NoUpdateNeeded 결과를 반환한다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)

            viewModel = createViewModel()

            // When
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.updateCheckResult.value).isEqualTo(UpdateCheckResult.NoUpdateNeeded)
        }

    @Test
    fun `checkForUpdate - 이미 체크했으면 다시 호출하지 않는다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)

            viewModel = createViewModel()
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `checkForUpdate - 실패해도 NoUpdateNeeded를 방출해 라우팅이 진행된다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns Result.failure(Exception("Network error"))

            viewModel = createViewModel()

            // When
            viewModel.checkForUpdate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.updateCheckResult.value).isEqualTo(UpdateCheckResult.NoUpdateNeeded)
        }

    @Test
    fun `재연결 - 콘텐츠 진입 후 오프라인에서 온라인으로 전환되면 강제 업데이트 이벤트를 방출한다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(
                    UpdateCheckResult.UpdateAvailable(isForceUpdate = true),
                )
            coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            viewModel.onContentShown()

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.forceUpdateAfterReconnect.collect { events.add(it) } }
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle() // collectJob이 구독을 시작한 뒤 emit

            // When: offline → online 전환 (emit은 collect lambda 완료를 대기하므로 별도 코루틴으로)
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(events).hasSize(1)
            collectJob.cancel()
        }

    @Test
    fun `재연결 - 선택 업데이트면 강제 업데이트 이벤트를 방출하지 않는다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(
                    UpdateCheckResult.UpdateAvailable(isForceUpdate = false),
                )
            coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            viewModel.onContentShown()

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.forceUpdateAfterReconnect.collect { events.add(it) } }
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 전환
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(events).isEmpty()
            collectJob.cancel()
        }

    @Test
    fun `재연결 - 업데이트 확인 실패 시 강제 업데이트 이벤트를 방출하지 않는다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns Result.failure(Exception("Network error"))
            coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            viewModel.onContentShown()

            val events = mutableListOf<Unit>()
            val collectJob = launch { viewModel.forceUpdateAfterReconnect.collect { events.add(it) } }
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 전환
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(events).isEmpty()
            collectJob.cancel()
        }

    @Test
    fun `재연결 - 콘텐츠 진입 전에는 온라인 전환이 업데이트 재확인을 트리거하지 않는다`() =
        runTest {
            // Given
            coEvery { mockCheckForUpdateUseCase() } returns
                Result.success(
                    UpdateCheckResult.UpdateAvailable(isForceUpdate = true),
                )
            viewModel = createViewModel()
            testDispatcher.scheduler.runCurrent()
            // onContentShown() 호출 안 함

            // When: offline → online 전환
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: UseCase 호출 없음
            coVerify(exactly = 0) { mockCheckForUpdateUseCase() }
        }

    @Test
    fun `loginInBackground - 서버 로그인을 백그라운드에서 호출한다`() =
        runTest {
            // Given
            coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
            viewModel = createViewModel()

            // When
            viewModel.loginInBackground()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockLoginUseCase() }
        }

    @Test
    fun `loginInBackground - 실패하면 재연결 시 재시도한다`() =
        runTest {
            // Given: 처음엔 실패, 이후엔 성공
            coEvery { mockLoginUseCase() } returns
                BaseResult.Error("NETWORK_ERROR", "네트워크 오류") andThen
                BaseResult.Success(Unit)
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)
            viewModel = createViewModel()
            viewModel.onContentShown()

            // When: 최초 로그인 실패
            viewModel.loginInBackground()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 재연결
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 총 2회 호출 (최초 + 재시도)
            coVerify(exactly = 2) { mockLoginUseCase() }
        }

    @Test
    fun `loginInBackground - 성공하면 재연결 시 재시도하지 않는다`() =
        runTest {
            // Given
            coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
            coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)
            viewModel = createViewModel()
            viewModel.onContentShown()

            // When: 최초 로그인 성공
            viewModel.loginInBackground()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: offline → online 재연결
            launch { networkStateFlow.emit(false) }
            launch { networkStateFlow.emit(true) }
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 최초 1회만 호출
            coVerify(exactly = 1) { mockLoginUseCase() }
        }

    @Test
    fun `showBottomNavigation - 호출하면 true가 설정된다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.showBottomNavigation()

            // Then
            assertThat(viewModel.showBottomNavigation.value).isTrue()
        }

    @Test
    fun `hideBottomNavigation - 호출하면 false가 설정된다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.hideBottomNavigation()

            // Then
            assertThat(viewModel.showBottomNavigation.value).isFalse()
        }

    @Test
    fun `showBottomNavigation과 hideBottomNavigation - 토글이 정상 동작한다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When & Then
            viewModel.showBottomNavigation()
            assertThat(viewModel.showBottomNavigation.value).isTrue()

            viewModel.hideBottomNavigation()
            assertThat(viewModel.showBottomNavigation.value).isFalse()

            viewModel.showBottomNavigation()
            assertThat(viewModel.showBottomNavigation.value).isTrue()
        }
}
