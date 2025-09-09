package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.Policy
import com.project200.domain.model.PolicyGroup
import com.project200.domain.model.Score
import com.project200.domain.model.ValidWindow
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetExerciseRecordListUseCase
import com.project200.domain.usecase.GetExpectedScoreInfoUseCase
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
import java.time.LocalDateTime
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

    @MockK
    private lateinit var mockGetExerciseRecordListUseCase: GetExerciseRecordListUseCase

    @MockK
    private lateinit var mockGetExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase

    private lateinit var viewModel: ExerciseMainViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val today = LocalDate.of(2025, 6, 19)
    private val thisMonth = YearMonth.from(today)
    private val nextMonth = thisMonth.plusMonths(1)
    private val sampleCounts =
        listOf(
            ExerciseCount(date = thisMonth.atDay(5), count = 2),
            ExerciseCount(date = thisMonth.atDay(10), count = 1),
            ExerciseCount(date = thisMonth.atDay(12), count = 0),
        )
    private val expectedDates = setOf(thisMonth.atDay(5), thisMonth.atDay(10))

    // 정책 데이터 더미
    private val samplePolicies =
        listOf(
            Policy(
                policyKey = "EXERCISE_SCORE_MAX_POINTS",
                policyValue = "100",
                policyUnit = "POINTS",
                policyDescription = "회원이 가질 수 있는 최대 운동 점수",
            ),
            Policy(
                policyKey = "EXERCISE_SCORE_MIN_POINTS",
                policyValue = "0",
                policyUnit = "POINTS",
                policyDescription = "회원이 가질 수 있는 최소 운동 점수",
            ),
            Policy(
                policyKey = "SIGNUP_INITIAL_POINTS",
                policyValue = "35",
                policyUnit = "POINTS",
                policyDescription = "회원 가입 시 기본으로 부여되는 점수",
            ),
            Policy(
                policyKey = "POINTS_PER_EXERCISE",
                policyValue = "3",
                policyUnit = "POINTS",
                policyDescription = "운동 기록 1회당 부여되는 점수 (일 1회)",
            ),
            Policy(
                policyKey = "EXERCISE_RECORD_VALIDITY_PERIOD",
                policyValue = "2",
                policyUnit = "DAYS",
                policyDescription = "점수 획득이 가능한 운동 기록의 유효 기간. (단위: DAYS, HOURS, MINUTES)",
            ),
            Policy(
                policyKey = "EXERCISE_RECORD_MAX_PER_DAY",
                policyValue = "1",
                policyUnit = "COUNT",
                policyDescription = "하루에 기록할 수 있는 최대 운동 횟수",
            ),
            Policy(
                policyKey = "PENALTY_INACTIVITY_THRESHOLD_DAYS",
                policyValue = "7",
                policyUnit = "DAYS",
                policyDescription = "페널티가 시작되는 비활성 기준일 (이 기간 이상 운동 기록이 없을 경우)",
            ),
            Policy(
                policyKey = "PENALTY_SCORE_DECREMENT_POINTS",
                policyValue = "1",
                policyUnit = "POINTS",
                policyDescription = "비활성 상태일 때 매일 차감되는 점수",
            ),
        )
    private val samplePolicyGroup =
        PolicyGroup(
            groupName = "exercise-score",
            size = samplePolicies.size,
            policies = samplePolicies,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockClockProvider.yearMonthNow() } returns thisMonth
        coEvery { mockClockProvider.now() } returns today

        // ViewModel의 init()에서 호출되는 UseCase에 대한 기본 응답 설정
        coEvery { mockGetExerciseCountUseCase.invoke(any(), any()) } returns BaseResult.Success(emptyList())
        coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(0))
        coEvery { mockGetScorePolicyUseCase.invoke() } returns BaseResult.Success(samplePolicyGroup)
        coEvery { mockGetExerciseRecordListUseCase.invoke(any()) } returns BaseResult.Success(emptyList())
        coEvery { mockGetExpectedScoreInfoUseCase.invoke() } returns
            BaseResult.Success<ExpectedScoreInfo>(
                ExpectedScoreInfo(
                    currentUserScore = 0,
                    maxScore = 100,
                    pointsPerExercise = 3,
                    validWindow =
                        ValidWindow(
                            startDateTime = LocalDateTime.of(2025, 6, 15, 0, 0),
                            endDateTime = LocalDateTime.of(2025, 6, 25, 0, 0),
                        ),
                    earnableScoreDays = listOf(today.minusDays(1), today),
                ),
            )

        viewModel =
            ExerciseMainViewModel(
                mockGetExerciseCountUseCase,
                mockGetScoreUseCase,
                mockGetScorePolicyUseCase,
                mockGetExerciseRecordListUseCase,
                mockGetExpectedScoreInfoUseCase,
                mockClockProvider,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `첫 데이터 로드 시 현재 월의 운동 기록을 성공적으로 가져온다`() =
        runTest {
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
    fun `onMonthChanged 호출 시 새로운 월의 데이터를 로드하고 캐시에 저장`() =
        runTest {
            // Given 1: '이번 달' 데이터(init+refreshData)를 미리 로드
            coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(sampleCounts)
            viewModel.refreshData()
            testDispatcher.scheduler.advanceUntilIdle()

            // Given 2: '다음 달'에 대한 성공 응답 준비
            val nextMonthCounts = listOf(ExerciseCount(nextMonth.atDay(1), 1))
            val expectedNextMonthDates = setOf(nextMonth.atDay(1))
            coEvery {
                mockGetExerciseCountUseCase.invoke(
                    nextMonth.atDay(1),
                    nextMonth.atEndOfMonth(),
                )
            } returns BaseResult.Success(nextMonthCounts)

            // When
            viewModel.onMonthChanged(nextMonth)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: '다음 달' UseCase가 1번 호출되었는지 검증
            coVerify(exactly = 1) { mockGetExerciseCountUseCase.invoke(nextMonth.atDay(1), nextMonth.atEndOfMonth()) }
            assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates + expectedNextMonthDates)
        }

    @Test
    fun `onMonthChanged 호출 시 이미 캐시된 월이면 UseCase를 호출하지 않음`() =
        runTest {
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
    fun `데이터 로드 실패 시 토스트 메시지 표시`() =
        runTest {
            // Given: init() 호출에 대한 기본 성공 응답 설정
            coEvery { mockGetExerciseCountUseCase.invoke(thisMonth.atDay(1), today) } returns BaseResult.Success(emptyList())
            testDispatcher.scheduler.advanceUntilIdle() // init() 완료 대기

            // '다음 달'에 대한 에러 응답 준비
            val errorMessage = "Network Error"
            coEvery {
                mockGetExerciseCountUseCase.invoke(
                    nextMonth.atDay(1),
                    nextMonth.atEndOfMonth(),
                )
            } returns BaseResult.Error("500", errorMessage)

            // When
            viewModel.onMonthChanged(nextMonth)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
            assertThat(viewModel.exerciseDates.value).isEmpty() // init()에서는 emptyList()를 반환했으므로 비어있어야 함
        }

    @Test
    fun `refreshData 호출 시 캐시를 지우고 현재 월 데이터를 다시 로드`() =
        runTest {
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
            assertThat(viewModel.score.value).isEqualTo(Score(80))
        }

    @Test
    fun `getScore 호출 시 점수를 성공적으로 가져온다`() =
        runTest {
            // Given
            val expectedScore = 95
            coEvery { mockGetScoreUseCase.invoke() } returns BaseResult.Success(Score(expectedScore))

            // When
            viewModel.refreshData()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            // refreshData()가 한 번만 호출되었으므로 getScoreUseCase도 1번만 호출됩니다.
            coVerify(exactly = 1) { mockGetScoreUseCase.invoke() }
            assertThat(viewModel.score.value).isEqualTo(Score(expectedScore))
        }

    @Test
    fun `getScore 호출 실패 시 토스트 메시지 표시`() =
        runTest {
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

    @Test
    fun `onDateSelected - 점수 획득 가능 시 예상 점수 업데이트`() =
        runTest {
            // Given: 점수 획득이 가능한 모든 조건 충족
            val earnableDate = today.minusDays(1)
            val scoreInfo =
                ExpectedScoreInfo(
                    currentUserScore = 50,
                    maxScore = 100,
                    pointsPerExercise = 3,
                    validWindow =
                        ValidWindow(
                            startDateTime = today.minusDays(5).atStartOfDay(),
                            endDateTime = today.plusDays(5).atStartOfDay(),
                        ),
                    earnableScoreDays = listOf(earnableDate, today),
                )
            coEvery { mockGetExpectedScoreInfoUseCase() } returns BaseResult.Success(scoreInfo)
            viewModel.loadExpectedScoreInfo()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.onDateSelected(earnableDate)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.earnablePoints.value).isEqualTo(3)
        }

    @Test
    fun `onDateSelected - 현재 점수가 최대 점수 이상이면 예상 점수는 0`() =
        runTest {
            // Given: 현재 점수가 최대 점수와 같음
            val earnableDate = today.minusDays(1)
            val scoreInfo =
                ExpectedScoreInfo(
                    currentUserScore = 100,
                    maxScore = 100,
                    pointsPerExercise = 3,
                    validWindow =
                        ValidWindow(
                            startDateTime = today.minusDays(5).atStartOfDay(),
                            endDateTime = today.plusDays(5).atStartOfDay(),
                        ),
                    earnableScoreDays = listOf(earnableDate),
                )
            coEvery { mockGetExpectedScoreInfoUseCase() } returns BaseResult.Success(scoreInfo)
            viewModel.loadExpectedScoreInfo()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.onDateSelected(earnableDate)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.earnablePoints.value).isEqualTo(0)
        }

    @Test
    fun `onDateSelected - 선택 날짜가 유효 기간 밖이면 예상 점수는 0`() =
        runTest {
            // Given: 선택한 날짜가 유효 기간을 벗어남
            val notEarnableDate = today.plusDays(10)
            val scoreInfo =
                ExpectedScoreInfo(
                    currentUserScore = 50,
                    maxScore = 100,
                    pointsPerExercise = 3,
                    validWindow =
                        ValidWindow(
                            startDateTime = today.minusDays(5).atStartOfDay(),
                            endDateTime = today.plusDays(5).atStartOfDay(),
                        ),
                    earnableScoreDays = listOf(today),
                )
            coEvery { mockGetExpectedScoreInfoUseCase() } returns BaseResult.Success(scoreInfo)
            viewModel.loadExpectedScoreInfo()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.onDateSelected(notEarnableDate)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.earnablePoints.value).isEqualTo(0)
        }

    @Test
    fun `onDateSelected - 선택 날짜가 획득 가능일이 아니면 예상 점수는 0`() =
        runTest {
            // Given: 선택한 날짜가 점수 획득 가능일이 아님
            val notEarnableDate = today.minusDays(2)
            val scoreInfo =
                ExpectedScoreInfo(
                    currentUserScore = 50,
                    maxScore = 100,
                    pointsPerExercise = 3,
                    validWindow =
                        ValidWindow(
                            startDateTime = today.minusDays(5).atStartOfDay(),
                            endDateTime = today.plusDays(5).atStartOfDay(),
                        ),
                    earnableScoreDays = listOf(today.minusDays(1), today),
                )
            coEvery { mockGetExpectedScoreInfoUseCase() } returns BaseResult.Success(scoreInfo)
            viewModel.loadExpectedScoreInfo()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.onDateSelected(notEarnableDate)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.earnablePoints.value).isEqualTo(0)
        }

    @Test
    fun `loadExpectedScoreInfo 실패 시 예상 점수는 0`() =
        runTest {
            // Given
            coEvery { mockGetExpectedScoreInfoUseCase() } returns BaseResult.Error("500", "Server Error")

            // When
            viewModel.loadExpectedScoreInfo()
            viewModel.onDateSelected(today)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.earnablePoints.value).isEqualTo(0)
        }
}
