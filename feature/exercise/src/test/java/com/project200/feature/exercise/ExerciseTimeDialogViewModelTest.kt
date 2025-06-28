package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.feature.exercise.form.ExerciseTimeDialogViewModel
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseTimeDialogViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockClockProvider: ClockProvider

    private lateinit var viewModel: ExerciseTimeDialogViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockClockProvider = mockk()
        viewModel = ExerciseTimeDialogViewModel(mockClockProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onDateTimeConfirmed - 선택된 시간이 오늘 미래 시간일 경우, ShowFutureTimeErrorToast를 발행한다`() = runTest {
        // Given: 현재 시간이 2025년 6월 27일 오전 10시 0분으로 가정
        val currentMockDateTime = LocalDateTime.of(2025, 6, 27, 10, 0)
        every { mockClockProvider.localDateTimeNow() } returns currentMockDateTime // ClockProvider의 localDateTimeNow() 사용

        // When: 오늘 날짜의 미래 시간 (11시 0분)을 선택
        val selectedYear = 2025
        val selectedMonth = 6
        val selectedDay = 27
        val selectedHour = 11
        val selectedMinute = 0

        // Then: ShowFutureTimeErrorToast 이벤트가 발행되는지 확인
        viewModel.event.test {
            viewModel.onDateTimeConfirmed(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            val emittedEvent = awaitItem()
            assertThat(emittedEvent).isEqualTo(ExerciseTimeDialogViewModel.Event.ShowFutureTimeErrorToast)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onDateTimeConfirmed - 선택된 시간이 오늘 과거 시간일 경우, TimeSelected를 발행한다`() = runTest {
        // Given: 현재 시간이 2025년 6월 27일 오전 10시 0분으로 가정
        val currentMockDateTime = LocalDateTime.of(2025, 6, 27, 10, 0)
        every { mockClockProvider.localDateTimeNow() } returns currentMockDateTime // ClockProvider의 localDateTimeNow() 사용

        // When: 오늘 날짜의 과거 시간 (9시 0분)을 선택
        val selectedYear = 2025
        val selectedMonth = 6
        val selectedDay = 27
        val selectedHour = 9
        val selectedMinute = 0

        // Then: TimeSelected 이벤트가 발행되는지 확인
        viewModel.event.test {
            viewModel.onDateTimeConfirmed(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            val emittedEvent = awaitItem()
            assertThat(emittedEvent).isEqualTo(
                ExerciseTimeDialogViewModel.Event.TimeSelected(
                    selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute
                )
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onDateTimeConfirmed - 선택된 시간이 오늘 현재 시간일 경우, TimeSelected를 발행한다`() = runTest {
        // Given: 현재 시간이 2025년 6월 27일 오전 10시 0분으로 가정
        val currentMockDateTime = LocalDateTime.of(2025, 6, 27, 10, 0)
        every { mockClockProvider.localDateTimeNow() } returns currentMockDateTime // ClockProvider의 localDateTimeNow() 사용

        // When: 오늘 날짜의 현재 시간 (10시 0분)을 선택
        val selectedYear = 2025
        val selectedMonth = 6
        val selectedDay = 27
        val selectedHour = 10
        val selectedMinute = 0

        // Then: TimeSelected 이벤트가 발행되는지 확인
        viewModel.event.test {
            viewModel.onDateTimeConfirmed(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            val emittedEvent = awaitItem()
            assertThat(emittedEvent).isEqualTo(
                ExerciseTimeDialogViewModel.Event.TimeSelected(
                    selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute
                )
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onDateTimeConfirmed - 선택된 날짜가 과거일 경우, TimeSelected를 발행한다 (시간 무관)`() = runTest {
        // Given: 현재 시간이 2025년 6월 27일 오전 10시 0분으로 가정
        val currentMockDateTime = LocalDateTime.of(2025, 6, 27, 10, 0)
        every { mockClockProvider.localDateTimeNow() } returns currentMockDateTime // ClockProvider의 localDateTimeNow() 사용

        // When: 과거 날짜 (6월 26일)의 미래 시간 (11시 0분)을 선택
        val selectedYear = 2025
        val selectedMonth = 6
        val selectedDay = 26
        val selectedHour = 11
        val selectedMinute = 0

        // Then: TimeSelected 이벤트가 발행되는지 확인 (과거 날짜는 시간 제약 없음)
        viewModel.event.test {
            viewModel.onDateTimeConfirmed(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute)
            val emittedEvent = awaitItem()
            assertThat(emittedEvent).isEqualTo(
                ExerciseTimeDialogViewModel.Event.TimeSelected(
                    selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute
                )
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `setInitialDateTime은 initialDateTime LiveData를 업데이트한다`() {
        // Given
        val dateTime = LocalDateTime.of(2024, 1, 1, 12, 30)

        // When
        viewModel.setInitialDateTime(dateTime)

        // Then
        assertThat(viewModel.initialDateTime.value).isEqualTo(dateTime)
    }

    @Test
    fun `setInitialDateTime에 null을 전달하면 initialDateTime LiveData가 null로 업데이트된다`() {
        // Given
        viewModel.setInitialDateTime(LocalDateTime.of(2024, 1, 1, 12, 30)) // 초기값 설정

        // When
        viewModel.setInitialDateTime(null)

        // Then
        assertThat(viewModel.initialDateTime.value).isNull()
    }
}