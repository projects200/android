package com.project200.undabang.profile.mypage

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class MypageViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetExerciseCountInMonthUseCase: GetExerciseCountInMonthUseCase

    @MockK
    private lateinit var mockGetUserProfileUseCase: GetUserProfileUseCase

    @MockK
    private lateinit var mockClockProvider: ClockProvider

    private lateinit var viewModel: MypageViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now = LocalDate.of(2025, 1, 15)
    private val currentYearMonth = YearMonth.of(2025, 1)

    private val sampleProfile = UserProfile(
        profileThumbnailUrl = "https://example.com/thumb.jpg",
        profileImageUrl = "https://example.com/image.jpg",
        nickname = "테스트유저",
        gender = "MALE",
        birthDate = "1990-01-01",
        bio = "테스트 소개",
        yearlyExerciseDays = 100,
        exerciseCountInLast30Days = 15,
        exerciseScore = 80,
        preferredExercises = listOf(
            PreferredExercise(
                preferredExerciseId = 1L,
                exerciseTypeId = 1L,
                name = "헬스",
                skillLevel = "BEGINNER",
                daysOfWeek = listOf(true, false, true, false, true, false, false),
                imageUrl = "https://example.com/exercise.jpg"
            )
        )
    )

    private val sampleExerciseCounts = listOf(
        ExerciseCount(date = LocalDate.of(2025, 1, 5), count = 2),
        ExerciseCount(date = LocalDate.of(2025, 1, 10), count = 1),
        ExerciseCount(date = LocalDate.of(2025, 1, 12), count = 0)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockClockProvider.now() } returns now
        every { mockClockProvider.yearMonthNow() } returns currentYearMonth
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = MypageViewModel(
            getExerciseCountInMonthUseCase = mockGetExerciseCountInMonthUseCase,
            getUserProfileUseCase = mockGetUserProfileUseCase,
            clockProvider = mockClockProvider
        )
    }

    @Test
    fun `init - ViewModel 초기화 시 getProfile과 onMonthChanged가 호출된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        // When
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetUserProfileUseCase() }
        coVerify(exactly = 1) { mockGetExerciseCountInMonthUseCase(any(), any()) }
    }

    @Test
    fun `getProfile - 성공 시 profile LiveData가 업데이트된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        // When
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.profile.value).isEqualTo(sampleProfile)
    }

    @Test
    fun `getProfile - 실패 시 toast가 emit된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Error("ERROR", "프로필 로드 실패")
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        // When
        createViewModel()
        
        viewModel.toast.test {
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Then
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `onMonthChanged - 캐시된 월은 API를 호출하지 않는다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - 동일한 월을 다시 선택
        viewModel.onMonthChanged(currentYearMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - 초기화 시 1회만 호출되어야 함
        coVerify(exactly = 1) { mockGetExerciseCountInMonthUseCase(any(), any()) }
    }

    @Test
    fun `onMonthChanged - 새로운 월은 getExerciseCounts를 호출한다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val previousMonth = currentYearMonth.minusMonths(1)

        // When
        viewModel.onMonthChanged(previousMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 2) { mockGetExerciseCountInMonthUseCase(any(), any()) }
        assertThat(viewModel.selectedMonth.value).isEqualTo(previousMonth)
    }

    @Test
    fun `onMonthChanged - 운동 기록 조회 성공 시 exerciseDates가 업데이트된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        // When
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - count > 0인 날짜만 포함
        val expectedDates = setOf(
            LocalDate.of(2025, 1, 5),
            LocalDate.of(2025, 1, 10)
        )
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates)
    }

    @Test
    fun `onMonthChanged - 운동 기록 조회 실패 시 toast가 emit된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Error("ERROR", "조회 실패")

        // When
        createViewModel()

        viewModel.toast.test {
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `onPreviousMonthClicked - 이전 월로 selectedMonth가 변경된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedMonth = currentYearMonth.minusMonths(1)

        // When
        viewModel.onPreviousMonthClicked()

        // Then
        assertThat(viewModel.selectedMonth.value).isEqualTo(expectedMonth)
    }

    @Test
    fun `onNextMonthClicked - 다음 월이 현재 월보다 이전이면 selectedMonth가 변경된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // 이전 월로 먼저 이동
        viewModel.onPreviousMonthClicked()
        val previousMonth = viewModel.selectedMonth.value!!

        // When
        viewModel.onNextMonthClicked()

        // Then
        assertThat(viewModel.selectedMonth.value).isEqualTo(previousMonth.plusMonths(1))
    }

    @Test
    fun `onNextMonthClicked - 다음 월이 미래이면 selectedMonth가 변경되지 않는다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val initialMonth = viewModel.selectedMonth.value

        // When - 현재 월에서 다음 월로 이동 시도
        viewModel.onNextMonthClicked()

        // Then - 변경되지 않음
        assertThat(viewModel.selectedMonth.value).isEqualTo(initialMonth)
    }

    @Test
    fun `getProfile - 수동 호출 시 프로필이 다시 로드된다`() = runTest {
        // Given
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetExerciseCountInMonthUseCase(any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedProfile = sampleProfile.copy(nickname = "새닉네임")
        coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(updatedProfile)

        // When
        viewModel.getProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.profile.value).isEqualTo(updatedProfile)
        coVerify(exactly = 2) { mockGetUserProfileUseCase() }
    }
}
