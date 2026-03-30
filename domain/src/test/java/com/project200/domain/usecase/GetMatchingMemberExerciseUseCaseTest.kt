package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.repository.MatchingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@ExperimentalCoroutinesApi
class GetMatchingMemberExerciseUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: GetMatchingMemberExerciseUseCase

    private val sampleMemberId = "member123"
    private val sampleStartDate = LocalDate.of(2024, 1, 1)
    private val sampleEndDate = LocalDate.of(2024, 1, 31)

    private val sampleExerciseCounts = listOf(
        ExerciseCount(date = LocalDate.of(2024, 1, 5), count = 2),
        ExerciseCount(date = LocalDate.of(2024, 1, 10), count = 1),
        ExerciseCount(date = LocalDate.of(2024, 1, 15), count = 3),
        ExerciseCount(date = LocalDate.of(2024, 1, 20), count = 1)
    )

    @Before
    fun setUp() {
        useCase = GetMatchingMemberExerciseUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 기록 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleExerciseCounts)
        coEvery {
            mockRepository.getMemberExerciseDates(sampleMemberId, sampleStartDate, sampleEndDate)
        } returns successResult

        // When
        val result = useCase(sampleMemberId, sampleStartDate, sampleEndDate)

        // Then
        coVerify(exactly = 1) {
            mockRepository.getMemberExerciseDates(sampleMemberId, sampleStartDate, sampleEndDate)
        }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(4)
    }

    @Test
    fun `빈 운동 기록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<ExerciseCount>())
        coEvery {
            mockRepository.getMemberExerciseDates(sampleMemberId, sampleStartDate, sampleEndDate)
        } returns successResult

        // When
        val result = useCase(sampleMemberId, sampleStartDate, sampleEndDate)

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `운동 기록 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch exercise data")
        coEvery {
            mockRepository.getMemberExerciseDates(sampleMemberId, sampleStartDate, sampleEndDate)
        } returns errorResult

        // When
        val result = useCase(sampleMemberId, sampleStartDate, sampleEndDate)

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `단일 날짜 조회`() = runTest {
        // Given
        val singleDate = LocalDate.of(2024, 1, 15)
        val singleExercise = listOf(ExerciseCount(date = singleDate, count = 3))
        coEvery {
            mockRepository.getMemberExerciseDates(sampleMemberId, singleDate, singleDate)
        } returns BaseResult.Success(singleExercise)

        // When
        val result = useCase(sampleMemberId, singleDate, singleDate)

        // Then
        coVerify(exactly = 1) {
            mockRepository.getMemberExerciseDates(sampleMemberId, singleDate, singleDate)
        }
        assertThat((result as BaseResult.Success).data).hasSize(1)
        assertThat(result.data[0].count).isEqualTo(3)
    }

    @Test
    fun `존재하지 않는 멤버 조회 시 에러 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("NOT_FOUND", "Member not found")
        coEvery {
            mockRepository.getMemberExerciseDates("nonexistent", sampleStartDate, sampleEndDate)
        } returns errorResult

        // When
        val result = useCase("nonexistent", sampleStartDate, sampleEndDate)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }
}
