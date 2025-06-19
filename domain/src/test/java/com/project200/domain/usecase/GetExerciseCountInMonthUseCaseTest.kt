package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.repository.ExerciseRecordRepository
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
class GetExerciseCountInMonthUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: GetExerciseCountInMonthUseCase

    private val startDate: LocalDate = LocalDate.of(2025, 6, 1)
    private val endDate: LocalDate = LocalDate.of(2025, 6, 30)
    private val sampleCounts = listOf(
        ExerciseCount(date = LocalDate.of(2025, 6, 5), count = 2),
        ExerciseCount(date = LocalDate.of(2025, 6, 10), count = 1)
    )

    @Before
    fun setUp() {
        useCase = GetExerciseCountInMonthUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository getExerciseCountByRange 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleCounts)
        coEvery { mockRepository.getExerciseCountByRange(startDate, endDate) } returns successResult

        // When
        val result = useCase(startDate, endDate)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseCountByRange(startDate, endDate) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `invoke 호출 시 repository getExerciseCountByRange 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("FETCH_ERROR", "Error fetching counts")
        coEvery { mockRepository.getExerciseCountByRange(startDate, endDate) } returns errorResult

        // When
        val result = useCase(startDate, endDate)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseCountByRange(startDate, endDate) }
        assertThat(result).isEqualTo(errorResult)
    }
}