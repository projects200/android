package com.project200.feature.timer.custom

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.Step
import com.project200.domain.usecase.DeleteCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.feature.timer.utils.CustomTimerServiceManager
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
class CustomTimerViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockTimerServiceManager: CustomTimerServiceManager

    @MockK
    private lateinit var mockGetCustomTimerUseCase: GetCustomTimerUseCase

    @MockK
    private lateinit var mockDeleteCustomTimerUseCase: DeleteCustomTimerUseCase

    private lateinit var viewModel: CustomTimerViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleSteps =
        listOf(
            Step(order = 0, time = 30, name = "준비"),
            Step(order = 1, time = 60, name = "운동"),
            Step(order = 2, time = 15, name = "휴식"),
        )

    private val sampleTimer =
        CustomTimer(
            localId = "local-1",
            name = "테스트 타이머",
            steps = sampleSteps,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockTimerServiceManager.service } returns MutableStateFlow(null)
        every { mockTimerServiceManager.bindService() } returns Unit
        every { mockTimerServiceManager.unbindService() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CustomTimerViewModel {
        return CustomTimerViewModel(
            timerServiceManager = mockTimerServiceManager,
            getCustomTimerUseCase = mockGetCustomTimerUseCase,
            deleteCustomTimerUseCase = mockDeleteCustomTimerUseCase,
        )
    }

    @Test
    fun `init - ViewModel 생성 시 서비스가 바인딩된다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            verify { mockTimerServiceManager.bindService() }
        }

    @Test
    fun `loadTimerData - 성공하면 setTimerLocalId로 지정한 localId로 조회하고 title과 steps가 설정된다`() =
        runTest {
            // Given
            coEvery { mockGetCustomTimerUseCase("local-123") } returns BaseResult.Success(sampleTimer)

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-123")

            // When
            viewModel.loadTimerData()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockGetCustomTimerUseCase("local-123") }
            assertThat(viewModel.title.value).isEqualTo("테스트 타이머")
            assertThat(viewModel.steps.value).hasSize(3)
        }

    @Test
    fun `loadTimerData - 실패하면 errorEvent가 발생한다`() =
        runTest {
            // Given
            coEvery { mockGetCustomTimerUseCase(any()) } returns BaseResult.Error("ERROR", "로드 실패")

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-1")

            // When & Then
            viewModel.errorEvent.test {
                viewModel.loadTimerData()
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem()
            }
        }

    @Test
    fun `loadTimerData - steps 순서가 유지된다`() =
        runTest {
            // Given
            coEvery { mockGetCustomTimerUseCase(any()) } returns BaseResult.Success(sampleTimer)

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-1")

            // When
            viewModel.loadTimerData()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val steps = viewModel.steps.value
            assertThat(steps[0].name).isEqualTo("준비")
            assertThat(steps[1].name).isEqualTo("운동")
            assertThat(steps[2].name).isEqualTo("휴식")
        }

    @Test
    fun `deleteTimer - 성공하면 Success 결과가 설정된다`() =
        runTest {
            // Given
            coEvery { mockDeleteCustomTimerUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-1")

            // When
            viewModel.deleteTimer()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.deleteResult.replayCache.firstOrNull()).isInstanceOf(BaseResult.Success::class.java)
        }

    @Test
    fun `deleteTimer - 실패하면 Error 결과가 설정된다`() =
        runTest {
            // Given
            coEvery { mockDeleteCustomTimerUseCase(any()) } returns BaseResult.Error("ERROR", "삭제 실패")

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-1")

            // When
            viewModel.deleteTimer()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.deleteResult.replayCache.firstOrNull()).isInstanceOf(BaseResult.Error::class.java)
        }

    @Test
    fun `deleteTimer - 올바른 타이머 localId로 UseCase가 호출된다`() =
        runTest {
            // Given
            coEvery { mockDeleteCustomTimerUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.setTimerLocalId("local-456")

            // When
            viewModel.deleteTimer()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockDeleteCustomTimerUseCase("local-456") }
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
    fun `resetTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.resetTimer(isUserAction = true)

            // Then - no crash
        }

    @Test
    fun `jumpToStep - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.jumpToStep(0)

            // Then - no crash
        }

    @Test
    fun `toggleRepeat - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.toggleRepeat()

            // Then - no crash
        }
}
