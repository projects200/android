package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.feature.exercise.main.ExerciseMainViewModel
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
import java.time.LocalDate
import java.time.YearMonth

@ExperimentalCoroutinesApi
class ExerciseMainViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetExerciseCountUseCase: GetExerciseCountInMonthUseCase

    @MockK
    private lateinit var mockClockProvider: ClockProvider

    private lateinit var viewModel: ExerciseMainViewModel
    private val testDispatcher = StandardTestDispatcher()


    private val today = LocalDate.of(2025, 6, 19)
    private val thisMonth = YearMonth.from(today)
    private val nextMonth = thisMonth.plusMonths(1)
    private val sampleCounts = listOf(
        ExerciseCount(date = thisMonth.atDay(5), count = 2),
        ExerciseCount(date = thisMonth.atDay(10), count = 1),
        ExerciseCount(date = thisMonth.atDay(12), count = 0)
    )
    private val expectedDates = setOf(thisMonth.atDay(5), thisMonth.atDay(10))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockClockProvider.yearMonthNow() } returns thisMonth
        coEvery { mockClockProvider.now() } returns today
        viewModel = ExerciseMainViewModel(mockGetExerciseCountUseCase, mockClockProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `첫 데이터 로드 시 현재 월의 운동 기록을 성공적으로 가져온다`() = runTest {
        // Given
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)

        // When
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates)
    }

    @Test
    fun `onMonthChanged 호출 시 새로운 월의 데이터를 로드하고 캐시에 저장`() = runTest {
        // Given 1: '이번 달' 데이터를 먼저 로드하여 캐시
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        viewModel.refreshData() // 첫 로드는 refreshData() 사용
        testDispatcher.scheduler.advanceUntilIdle()

        // Given 2: '다음 달'에 대한 성공 응답을 준비
        val nextMonthCounts = listOf(ExerciseCount(nextMonth.atDay(1), 1))
        val expectedNextMonthDates = setOf(nextMonth.atDay(1))
        coEvery { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) } returns BaseResult.Success(nextMonthCounts)

        // When
        viewModel.onMonthChanged(nextMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: LiveData가 이전 캐시 데이터와 새 데이터를 모두 포함하는지 검증
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates + expectedNextMonthDates)
    }

    @Test
    fun `onMonthChanged 호출 시 이미 캐시된 월이면 UseCase를 호출하지 않음`() = runTest {
        // Given 1: '이번 달' 데이터를 로드하여 캐시
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        viewModel.refreshData() // 첫 로드는 refreshData() 사용
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 캐시된 '이번 달'을 onMonthChanged로 다시 요청
        viewModel.onMonthChanged(thisMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: '이번 달'에 대한 UseCase 호출은 최초 1번만 이루어져야 함
        coVerify(exactly = 1) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
    }

    @Test
    fun `데이터 로드 실패 시 토스트 메시지 표시`() = runTest {
        // Given: '다음 달'에 대한 에러 응답을 준비
        val errorMessage = "Network Error"
        coEvery { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) } returns BaseResult.Error("500", errorMessage)

        // When: onMonthChanged로 에러가 발생하는 데이터 로드를 트리거
        viewModel.onMonthChanged(nextMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 토스트 메시지가 표시되고, 데이터는 비어있어야 함
        assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
        assertThat(viewModel.exerciseDates.value).isNull()
    }

    @Test
    fun `refreshData 호출 시 캐시를 지우고 현재 월 데이터를 다시 로드`() = runTest {
        // Given 1: 첫 데이터 로드
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Given 2: refresh 시 반환될 새로운 데이터 정의
        val newSampleCounts = listOf(ExerciseCount(today.withDayOfMonth(25), 3))
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(newSampleCounts)

        // When: refreshData 재호출
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: UseCase가 총 2번 호출되고, 데이터가 새로운 값으로 갱신되었는지 검증
        coVerify(exactly = 2) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
        val newExpectedDates = setOf(today.withDayOfMonth(25))
        assertThat(viewModel.exerciseDates.value).isEqualTo(newExpectedDates)
    }
}