package com.project200.feature.timer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.GetCustomTimerListUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class TimerListViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getCustomTimerListUseCase: GetCustomTimerListUseCase

    private lateinit var viewModel: TimerListViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTimer = CustomTimer(
        id = 1L,
        name = "테스트 타이머",
        steps = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - ViewModel 생성 시 커스텀 타이머 목록을 로드한다`() = runTest {
        // Given
        coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(listOf(sampleTimer))

        // When
        viewModel = TimerListViewModel(getCustomTimerListUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.customTimerList.value).hasSize(1)
        coVerify { getCustomTimerListUseCase() }
    }

    @Test
    fun `init - 빈 목록도 정상 처리한다`() = runTest {
        // Given
        coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(emptyList())

        // When
        viewModel = TimerListViewModel(getCustomTimerListUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.customTimerList.value).isEmpty()
    }

    @Test
    fun `init - 에러 발생 시 errorToast 이벤트를 발생시킨다`() = runTest {
        // Given
        val error = BaseResult.Error("ERROR", "Failed to load")
        coEvery { getCustomTimerListUseCase() } returns error

        // When
        viewModel = TimerListViewModel(getCustomTimerListUseCase)

        viewModel.errorToast.test {
            testDispatcher.scheduler.advanceUntilIdle()
            val result = awaitItem()
            assertThat(result.errorCode).isEqualTo("ERROR")
        }
    }

    @Test
    fun `loadCustomTimers - 타이머 목록을 다시 로드한다`() = runTest {
        // Given
        coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(listOf(sampleTimer))
        viewModel = TimerListViewModel(getCustomTimerListUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.loadCustomTimers()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { getCustomTimerListUseCase() }
    }
}
