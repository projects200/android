package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score
import com.project200.domain.repository.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class GetScoreUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetScoreUseCase

    private val sampleScore = Score(
        score = 85,
        maxScore = 100,
        minScore = 0
    )

    @Before
    fun setUp() {
        useCase = GetScoreUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 점수 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleScore)
        coEvery { mockRepository.getScore() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getScore() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.score).isEqualTo(85)
    }

    @Test
    fun `최대 점수 반환`() = runTest {
        // Given
        val maxScore = Score(score = 100, maxScore = 100, minScore = 0)
        coEvery { mockRepository.getScore() } returns BaseResult.Success(maxScore)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.score).isEqualTo(100)
    }

    @Test
    fun `최소 점수 반환`() = runTest {
        // Given
        val minScore = Score(score = 0, maxScore = 100, minScore = 0)
        coEvery { mockRepository.getScore() } returns BaseResult.Success(minScore)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.score).isEqualTo(0)
    }

    @Test
    fun `점수 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch score")
        coEvery { mockRepository.getScore() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `기본값이 있는 점수 반환`() = runTest {
        // Given
        val scoreWithDefaults = Score(score = 50)
        coEvery { mockRepository.getScore() } returns BaseResult.Success(scoreWithDefaults)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data.score).isEqualTo(50)
        assertThat(data.maxScore).isEqualTo(0)
        assertThat(data.minScore).isEqualTo(100)
    }
}
