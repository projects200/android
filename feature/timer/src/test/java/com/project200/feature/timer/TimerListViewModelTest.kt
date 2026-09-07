package com.project200.feature.timer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.GetCustomTimerListUseCase
import com.project200.domain.usecase.GetLocalCustomTimerListUseCase
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

    @MockK
    private lateinit var getLocalCustomTimerListUseCase: GetLocalCustomTimerListUseCase

    private lateinit var viewModel: TimerListViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTimer =
        CustomTimer(
            localId = "local-1",
            name = "테스트 타이머",
            steps = emptyList(),
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
    fun `init - ViewModel 생성 시 GetCustomTimerListUseCase로 커스텀 타이머 목록을 로드한다`() =
        runTest {
            // Given
            coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(listOf(sampleTimer))

            // When
            viewModel = TimerListViewModel(getCustomTimerListUseCase, getLocalCustomTimerListUseCase)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.customTimerList.value).hasSize(1)
            coVerify { getCustomTimerListUseCase() }
        }

    @Test
    fun `init - 빈 목록도 정상 처리한다`() =
        runTest {
            // Given
            coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(emptyList())

            // When
            viewModel = TimerListViewModel(getCustomTimerListUseCase, getLocalCustomTimerListUseCase)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.customTimerList.value).isEmpty()
        }

    @Test
    fun `loadCustomTimers - 실패하면 목록이 비어있는 채로 유지된다`() =
        runTest {
            // Given
            val error = BaseResult.Error("ERROR", "Failed to load")
            coEvery { getCustomTimerListUseCase() } returns error

            // When
            viewModel = TimerListViewModel(getCustomTimerListUseCase, getLocalCustomTimerListUseCase)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.customTimerList.value).isEmpty()
        }

    @Test
    fun `refreshLocalCustomTimers - 로컬 목록으로 갱신하고 실패해도 기존 목록을 유지한다`() =
        runTest {
            // Given
            coEvery { getCustomTimerListUseCase() } returns BaseResult.Success(listOf(sampleTimer))
            viewModel = TimerListViewModel(getCustomTimerListUseCase, getLocalCustomTimerListUseCase)
            testDispatcher.scheduler.advanceUntilIdle()

            val refreshedTimer = sampleTimer.copy(name = "로컬 갱신 타이머")
            coEvery { getLocalCustomTimerListUseCase() } returns BaseResult.Success(listOf(refreshedTimer))

            // When
            viewModel.refreshLocalCustomTimers()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.customTimerList.value).containsExactly(refreshedTimer)
            coVerify(exactly = 1) { getLocalCustomTimerListUseCase() }
            coVerify(exactly = 1) { getCustomTimerListUseCase() }

            // When - 두 번째 새로고침이 실패한다
            coEvery { getLocalCustomTimerListUseCase() } returns BaseResult.Error("ERROR", "실패")
            viewModel.refreshLocalCustomTimers()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then - 실패해도 이전 목록이 유지된다
            assertThat(viewModel.customTimerList.value).containsExactly(refreshedTimer)
        }
}
