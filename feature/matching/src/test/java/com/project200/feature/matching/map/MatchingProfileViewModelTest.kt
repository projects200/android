package com.project200.feature.matching.map

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.BlockMemberUseCase
import com.project200.domain.usecase.CreateChatRoomUseCase
import com.project200.domain.usecase.GetMatchingMemberExerciseUseCase
import com.project200.domain.usecase.GetMatchingProfileUseCase
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
class MatchingProfileViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetMatchingProfileUseCase: GetMatchingProfileUseCase

    @MockK
    private lateinit var mockGetMemberExerciseUseCase: GetMatchingMemberExerciseUseCase

    @MockK
    private lateinit var mockCreateChatRoomUseCase: CreateChatRoomUseCase

    @MockK
    private lateinit var mockBlockMemberUseCase: BlockMemberUseCase

    @MockK
    private lateinit var mockClockProvider: ClockProvider

    private lateinit var viewModel: MatchingProfileViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now = LocalDate.of(2025, 1, 15)
    private val currentYearMonth = YearMonth.of(2025, 1)

    private val sampleProfile = MatchingMemberProfile(
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
                imageUrl = "https://example.com/health.jpg"
            )
        )
    )

    private val sampleExerciseCounts = listOf(
        ExerciseCount(date = LocalDate.of(2025, 1, 5), count = 2),
        ExerciseCount(date = LocalDate.of(2025, 1, 10), count = 1)
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

    private fun createViewModel(): MatchingProfileViewModel {
        return MatchingProfileViewModel(
            getMatchingProfileUseCase = mockGetMatchingProfileUseCase,
            getMemberExerciseUseCase = mockGetMemberExerciseUseCase,
            createChatRoomUseCase = mockCreateChatRoomUseCase,
            blockMemberUseCase = mockBlockMemberUseCase,
            clockProvider = mockClockProvider
        )
    }

    @Test
    fun `setInitialData - memberId와 placeId가 설정되고 프로필이 로드된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockGetMatchingProfileUseCase("member1") }
        assertThat(viewModel.profile.value).isEqualTo(sampleProfile)
    }

    @Test
    fun `getProfile - 성공 시 profile이 업데이트된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.profile.value).isEqualTo(sampleProfile)
    }

    @Test
    fun `getProfile - 실패 시 toast가 emit된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Error("ERROR", "프로필 로드 실패")
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()

        viewModel.toast.test {
            viewModel.setInitialData("member1", 100L)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo("프로필 로드 실패")
        }
    }

    @Test
    fun `onMonthChanged - 캐시된 월은 API를 호출하지 않는다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMonthChanged(currentYearMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGetMemberExerciseUseCase(any(), any(), any()) }
    }

    @Test
    fun `onMonthChanged - 새로운 월은 getMemberExerciseUseCase를 호출한다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        val previousMonth = currentYearMonth.minusMonths(1)
        viewModel.onMonthChanged(previousMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { mockGetMemberExerciseUseCase(any(), any(), any()) }
        assertThat(viewModel.selectedMonth.value).isEqualTo(previousMonth)
    }

    @Test
    fun `onPreviousMonthClicked - 이전 월로 변경된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPreviousMonthClicked()

        assertThat(viewModel.selectedMonth.value).isEqualTo(currentYearMonth.minusMonths(1))
    }

    @Test
    fun `onNextMonthClicked - 미래가 아닌 경우 다음 월로 변경된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPreviousMonthClicked()
        val previousMonth = viewModel.selectedMonth.value

        viewModel.onNextMonthClicked()

        assertThat(viewModel.selectedMonth.value).isEqualTo(previousMonth!!.plusMonths(1))
    }

    @Test
    fun `onNextMonthClicked - 미래인 경우 변경되지 않는다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        val systemYearMonth = YearMonth.now()
        
        repeat(24) { viewModel.onNextMonthClicked() }
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(viewModel.selectedMonth.value!!.isAfter(systemYearMonth)).isFalse()
    }

    @Test
    fun `createChatRoom - 결과가 emit된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        coEvery { mockCreateChatRoomUseCase(any(), any(), any(), any()) } returns BaseResult.Success(999L)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createChatRoomResult.test {
            viewModel.createChatRoom(37.5, 127.0)
            testDispatcher.scheduler.advanceUntilIdle()

            val result = awaitItem()
            assertThat(result).isInstanceOf(BaseResult.Success::class.java)
            assertThat((result as BaseResult.Success).data).isEqualTo(999L)
        }
    }

    @Test
    fun `blockMember - 성공 시 blockResult가 emit된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        coEvery { mockBlockMemberUseCase(any()) } returns BaseResult.Success(Unit)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.blockResult.test {
            viewModel.blockMember()
            testDispatcher.scheduler.advanceUntilIdle()

            val result = awaitItem()
            assertThat(result).isInstanceOf(BaseResult.Success::class.java)
        }
    }

    @Test
    fun `blockMember - 실패 시 toast가 emit된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)
        coEvery { mockBlockMemberUseCase(any()) } returns BaseResult.Error("ERROR", "차단 실패")

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toast.test {
            viewModel.blockMember()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo("차단 실패")
        }
    }

    @Test
    fun `exerciseDates - 운동 기록이 있는 날짜가 업데이트된다`() = runTest {
        coEvery { mockGetMatchingProfileUseCase(any()) } returns BaseResult.Success(sampleProfile)
        coEvery { mockGetMemberExerciseUseCase(any(), any(), any()) } returns BaseResult.Success(sampleExerciseCounts)

        viewModel = createViewModel()
        viewModel.setInitialData("member1", 100L)
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedDates = setOf(
            LocalDate.of(2025, 1, 5),
            LocalDate.of(2025, 1, 10)
        )
        assertThat(viewModel.exerciseDates.value).isEqualTo(expectedDates)
    }
}
