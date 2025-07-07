package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.PolicyType
import com.project200.domain.model.Score
import com.project200.domain.model.ScorePolicy
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetScorePolicyUseCase
import com.project200.domain.usecase.GetScoreUseCase
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

    @MockK
    private lateinit var mockGetScoreUseCase: GetScoreUseCase

    @MockK
    private lateinit var mockGetScorePolicyUseCase: GetScorePolicyUseCase


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

    // 정책 데이터 더미
    private val samplePolicyData = listOf(
        ScorePolicy(PolicyType.EXERCISE_SCORE_MAX_POINTS.key, 100, "POINTS"),
        ScorePolicy(PolicyType.EXERCISE_SCORE_MIN_POINTS.key, 0, "POINTS"),
        ScorePolicy(PolicyType.SIGNUP_INITIAL_POINTS.key, 35, "POINTS"),
        ScorePolicy(PolicyType.POINTS_PER_EXERCISE.key, 3, "POINTS"),
        ScorePolicy(PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD.key, 2, "DAYS"),
        ScorePolicy(PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS.key, 7, "DAYS"),
        ScorePolicy(PolicyType.PENALTY_SCORE_DECREMENT_POINTS.key, 1, "POINTS")
    )


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockClockProvider.yearMonthNow() } returns thisMonth
        coEvery { mockClockProvider.now() } returns today

        // ViewModel의 init()에서 호출되는 UseCase에 대한 기본 응답 설정
        coEvery { mockGetExerciseCountUseCase.invoke(any(), any()) } returns BaseResult.Success(emptyList())
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(0))
        coEvery { mockGetScorePolicyUseCase.invoke() } returns BaseResult.Success(samplePolicyData)

        viewModel = ExerciseMainViewModel(mockGetExerciseCountUseCase, mockGetScoreUseCase, mockGetScorePolicyUseCase, mockClockProvider)
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
        coVerify(exactly = 3) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates)
    }

    @Test
    fun `onMonthChanged 호출 시 새로운 월의 데이터를 로드하고 캐시에 저장`() = runTest {
        // Given 1: '이번 달' 데이터(init+refreshData)를 미리 로드
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Given 2: '다음 달'에 대한 성공 응답 준비
        val nextMonthCounts = listOf(ExerciseCount(nextMonth.atDay(1), 1))
        val expectedNextMonthDates = setOf(nextMonth.atDay(1))
        coEvery { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) } returns BaseResult.Success(nextMonthCounts)

        // When
        viewModel.onMonthChanged(nextMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: '다음 달' UseCase가 1번 호출되었는지 검증
        coVerify(exactly = 1) { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) }
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates + expectedNextMonthDates)
    }

    @Test
    fun `onMonthChanged 호출 시 이미 캐시된 월이면 UseCase를 호출하지 않음`() = runTest {
        // Given 1: '이번 달' 데이터를 로드하여 캐시
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 캐시된 '이번 달'을 onMonthChanged로 다시 요청
        viewModel.onMonthChanged(thisMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        // --- 수정된 부분 ---
        // init(1) + refreshData(2) = 총 3번 호출된 후, onMonthChanged에서는 추가 호출이 없어야 합니다.
        // 따라서 총 호출 횟수는 3회로 유지됩니다.
        coVerify(exactly = 3) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
    }

    @Test
    fun `데이터 로드 실패 시 토스트 메시지 표시`() = runTest {
        // Given: init() 호출에 대한 기본 성공 응답 설정
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(emptyList())
        testDispatcher.scheduler.advanceUntilIdle() // init() 완료 대기

        // '다음 달'에 대한 에러 응답 준비
        val errorMessage = "Network Error"
        coEvery { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) } returns BaseResult.Error("500", errorMessage)

        // When
        viewModel.onMonthChanged(nextMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
        assertThat(viewModel.exerciseDates.value).isEmpty() // init()에서는 emptyList()를 반환했으므로 비어있어야 함
    }


    @Test
    fun `refreshData 호출 시 캐시를 지우고 현재 월 데이터를 다시 로드`() = runTest {
        // Given 1: 첫 데이터 로드 (init에서 1번, refreshData에서 2번 = 총 3번)
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(100))
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Given 2: refresh 시 반환될 새로운 데이터 정의
        val newSampleCounts = listOf(ExerciseCount(today.withDayOfMonth(25), 3))
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(newSampleCounts)
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(80))

        // When: refreshData 재호출 (UseCase 2번 추가 호출)
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        // --- 수정된 부분 ---
        // init(1) + refreshData(2) + 두 번째 refreshData(2) = 총 5번
        coVerify(exactly = 5) { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) }
        // 첫 번째 refreshData(1) + 두 번째 refreshData(1) = 총 2번
        coVerify(exactly = 2) { mockGetScoreUseCase.invoke() }
        val newExpectedDates = setOf(today.withDayOfMonth(25))
        assertThat(viewModel.exerciseDates.value).isEqualTo(newExpectedDates)
        assertThat(viewModel.score.value).isEqualTo(80)
    }

    @Test
    fun `getScore 호출 시 점수를 성공적으로 가져온다`() = runTest {
        // Given
        val expectedScore = 95
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(expectedScore))

        // When
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        // --- 수정된 부분 ---
        // refreshData()가 한 번만 호출되었으므로 getScoreUseCase도 1번만 호출됩니다.
        coVerify(exactly = 1) { mockGetScoreUseCase.invoke() }
        assertThat(viewModel.score.value).isEqualTo(expectedScore)
    }

    @Test
    fun `getScore 호출 실패 시 토스트 메시지 표시`() = runTest {
        // Given
        val errorMessage = "Failed to load score"
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Error("404", errorMessage)
        coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)

        // When
        viewModel.refreshData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetScoreUseCase.invoke() }
        assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
        assertThat(viewModel.score.value).isNull()
    }
}