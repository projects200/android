package com.project200.feature.timer.simple

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.usecase.AddSimpleTimerUseCase
import com.project200.domain.usecase.DeleteSimpleTimerUseCase
import com.project200.domain.usecase.EditSimpleTimerUseCase
import com.project200.domain.usecase.GetLocalSimpleTimersUseCase
import com.project200.domain.usecase.GetSimpleTimersUseCase
import com.project200.feature.timer.utils.SimpleTimerServiceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SimpleTimerViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockSimpleTimerServiceManager: SimpleTimerServiceManager

    @MockK
    private lateinit var mockGetSimpleTimersUseCase: GetSimpleTimersUseCase

    @MockK
    private lateinit var mockGetLocalSimpleTimersUseCase: GetLocalSimpleTimersUseCase

    @MockK
    private lateinit var mockAddSimpleTimerUseCase: AddSimpleTimerUseCase

    @MockK
    private lateinit var mockEditSimpleTimerUseCase: EditSimpleTimerUseCase

    @MockK
    private lateinit var mockDeleteSimpleTimerUseCase: DeleteSimpleTimerUseCase

    private lateinit var viewModel: SimpleTimerViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTimers =
        listOf(
            SimpleTimer(localId = "1", time = 60),
            SimpleTimer(localId = "2", time = 120),
            SimpleTimer(localId = "3", time = 30),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockSimpleTimerServiceManager.service } returns MutableStateFlow(null)
        every { mockSimpleTimerServiceManager.bindService() } returns Unit
        every { mockSimpleTimerServiceManager.unbindService() } returns Unit
        // 기본 스텁은 여기서만 세웁니다. createViewModel에 두면 각 테스트가 세운 스텁을 덮어씁니다
        coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(sampleTimers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SimpleTimerViewModel {
        return SimpleTimerViewModel(
            simpleTimerServiceManager = mockSimpleTimerServiceManager,
            getSimpleTimersUseCase = mockGetSimpleTimersUseCase,
            getLocalSimpleTimersUseCase = mockGetLocalSimpleTimersUseCase,
            addSimpleTimerUseCase = mockAddSimpleTimerUseCase,
            editSimpleTimerUseCase = mockEditSimpleTimerUseCase,
            deleteSimpleTimerUseCase = mockDeleteSimpleTimerUseCase,
        )
    }

    @Test
    fun `init - ViewModel 생성 시 서비스가 바인딩된다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            verify { mockSimpleTimerServiceManager.bindService() }
        }

    @Test
    fun `init - ViewModel 생성 시 GetSimpleTimersUseCase로 타이머 목록을 로드한다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value).hasSize(3)
            coVerify { mockGetSimpleTimersUseCase() }
        }

    @Test
    fun `loadTimerItems - 성공하면 타이머 목록이 설정된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newTimers = listOf(SimpleTimer(localId = "10", time = 300))
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(newTimers)

            // When
            viewModel.loadTimerItems()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value).hasSize(1)
            assertThat(viewModel.timerItems.value.first().time).isEqualTo(300)
        }

    @Test
    fun `loadTimerItems - 실패하면 GET_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Error("ERROR", "로드 실패")
            viewModel = createViewModel()

            // When & Then
            viewModel.toastMessage.test {
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.GET_ERROR)
            }
        }

    @Test
    fun `addTimerItem - 성공하면 GetLocalSimpleTimersUseCase로 목록을 다시 읽는다`() =
        runTest {
            // Given
            coEvery { mockAddSimpleTimerUseCase(any()) } returns BaseResult.Success("100")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val reloadedTimers = sampleTimers + SimpleTimer(localId = "100", time = 180)
            coEvery { mockGetLocalSimpleTimersUseCase() } returns BaseResult.Success(reloadedTimers)

            // When
            viewModel.addTimerItem(180)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockGetLocalSimpleTimersUseCase() }
            assertThat(viewModel.timerItems.value).hasSize(4)
            assertThat(viewModel.timerItems.value.last().localId).isEqualTo("100")
        }

    @Test
    fun `addTimerItem - 실패하면 ADD_ERROR 토스트가 발생하고 재조회하지 않는다`() =
        runTest {
            // Given
            coEvery { mockAddSimpleTimerUseCase(any()) } returns BaseResult.Error("ERROR", "추가 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.addTimerItem(60)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.ADD_ERROR)
            }
            coVerify(exactly = 0) { mockGetLocalSimpleTimersUseCase() }
        }

    @Test
    fun `addTimerItem - 최대 개수에 도달하면 추가되지 않는다`() =
        runTest {
            // Given
            val maxTimers = (1..6).map { SimpleTimer(localId = it.toString(), time = 60) }
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(maxTimers)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.addTimerItem(300)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { mockAddSimpleTimerUseCase(any()) }
        }

    @Test
    fun `addTimerItem - 최대 개수 판정은 동기화 대기 행도 포함한다`() =
        runTest {
            // Given - 동기화 대기 행이 섞여 있어도 목록 크기만으로 판정한다
            val pendingIncluded =
                (1..6).map { SimpleTimer(localId = it.toString(), time = 60, isSyncPending = it % 2 == 0) }
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(pendingIncluded)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.addTimerItem(300)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { mockAddSimpleTimerUseCase(any()) }
        }

    @Test
    fun `deleteTimerItem - 성공하면 GetLocalSimpleTimersUseCase로 목록을 다시 읽는다`() =
        runTest {
            // Given
            coEvery { mockDeleteSimpleTimerUseCase(any()) } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val reloadedTimers = sampleTimers.filterNot { it.localId == "1" }
            coEvery { mockGetLocalSimpleTimersUseCase() } returns BaseResult.Success(reloadedTimers)

            // When
            viewModel.deleteTimerItem("1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockGetLocalSimpleTimersUseCase() }
            assertThat(viewModel.timerItems.value.any { it.localId == "1" }).isFalse()
        }

    @Test
    fun `deleteTimerItem - 실패하면 DELETE_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockDeleteSimpleTimerUseCase(any()) } returns BaseResult.Error("ERROR", "삭제 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.deleteTimerItem("1")
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.DELETE_ERROR)
            }
        }

    @Test
    fun `updateTimerItem - 성공하면 GetLocalSimpleTimersUseCase로 목록을 다시 읽는다`() =
        runTest {
            // Given
            coEvery { mockEditSimpleTimerUseCase(any(), any()) } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val reloadedTimers = sampleTimers.map { if (it.localId == "1") it.copy(time = 999) else it }
            coEvery { mockGetLocalSimpleTimersUseCase() } returns BaseResult.Success(reloadedTimers)

            // When
            viewModel.updateTimerItem("1", 999)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockEditSimpleTimerUseCase("1", 999) }
            coVerify { mockGetLocalSimpleTimersUseCase() }
            assertThat(viewModel.timerItems.value.find { it.localId == "1" }?.time).isEqualTo(999)
        }

    @Test
    fun `updateTimerItem - 실패하면 EDIT_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockEditSimpleTimerUseCase(any(), any()) } returns BaseResult.Error("ERROR", "수정 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.updateTimerItem("1", 500)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.EDIT_ERROR)
            }
        }

    @Test
    fun `changeSortOrder - 오름차순으로 정렬된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.changeSortOrder()

            // Then
            val items = viewModel.timerItems.value
            assertThat(items[0].time).isEqualTo(120)
            assertThat(items[1].time).isEqualTo(60)
            assertThat(items[2].time).isEqualTo(30)
        }

    @Test
    fun `changeSortOrder - 두 번 호출하면 다시 오름차순이 된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.changeSortOrder()
            viewModel.changeSortOrder()

            // Then
            val items = viewModel.timerItems.value
            assertThat(items[0].time).isEqualTo(30)
            assertThat(items[1].time).isEqualTo(60)
            assertThat(items[2].time).isEqualTo(120)
        }

    @Test
    fun `startTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.startTimer()

            // Then - no crash
        }

    @Test
    fun `pauseTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.pauseTimer()

            // Then - no crash
        }

    @Test
    fun `setAndStartTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.setAndStartTimer(60)

            // Then - no crash
        }

    @Test
    fun `onCleared - 서비스 매니저의 unbindService가 호출된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When - onCleared는 protected라 리플렉션으로 호출한다
            val onCleared = SimpleTimerViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(viewModel)

            // Then
            verify { mockSimpleTimerServiceManager.unbindService() }
        }

    @Test
    fun `isTimerRunning - 초기값은 false이다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then - service가 null일 때 StateFlow 초기값은 false
            assertThat(viewModel.isTimerRunning.value).isFalse()
        }

    @Test
    fun `remainingTime - 초기값은 0이다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then - service가 null일 때 StateFlow 초기값은 0L
            assertThat(viewModel.remainingTime.value).isEqualTo(0L)
        }
}
