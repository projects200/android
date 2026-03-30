package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.ValidWindow
import com.project200.domain.repository.ScoreRepository
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class GetExpectedScoreInfoUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ScoreRepository

    private lateinit var useCase: GetExpectedScoreInfoUseCase

    private val sampleValidWindow = ValidWindow(
        startDateTime = LocalDateTime.of(2024, 1, 1, 0, 0),
        endDateTime = LocalDateTime.of(2024, 1, 31, 23, 59)
    )

    private val sampleExpectedScoreInfo = ExpectedScoreInfo(
        pointsPerExercise = 2,
        currentUserScore = 80,
        maxScore = 100,
        validWindow = sampleValidWindow,
        earnableScoreDays = listOf(
            LocalDate.of(2024, 1, 15),
            LocalDate.of(2024, 1, 20),
            LocalDate.of(2024, 1, 25)
        )
    )

    @Before
    fun setUp() {
        useCase = GetExpectedScoreInfoUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 예상 점수 정보 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleExpectedScoreInfo)
        coEvery { mockRepository.getExpectedScoreInfo() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getExpectedScoreInfo() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.pointsPerExercise).isEqualTo(2)
    }

    @Test
    fun `현재 점수와 최대 점수 확인`() = runTest {
        // Given
        coEvery { mockRepository.getExpectedScoreInfo() } returns BaseResult.Success(sampleExpectedScoreInfo)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.currentUserScore).isEqualTo(80)
        assertThat(data.maxScore).isEqualTo(100)
    }

    @Test
    fun `점수 획득 가능 날짜 목록 확인`() = runTest {
        // Given
        coEvery { mockRepository.getExpectedScoreInfo() } returns BaseResult.Success(sampleExpectedScoreInfo)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.earnableScoreDays).hasSize(3)
    }

    @Test
    fun `예상 점수 정보 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch expected score info")
        coEvery { mockRepository.getExpectedScoreInfo() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `이미 최대 점수인 경우`() = runTest {
        // Given
        val maxedScore = sampleExpectedScoreInfo.copy(
            currentUserScore = 100,
            maxScore = 100,
            earnableScoreDays = emptyList()
        )
        coEvery { mockRepository.getExpectedScoreInfo() } returns BaseResult.Success(maxedScore)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.currentUserScore).isEqualTo(data.maxScore)
        assertThat(data.earnableScoreDays).isEmpty()
    }
}
