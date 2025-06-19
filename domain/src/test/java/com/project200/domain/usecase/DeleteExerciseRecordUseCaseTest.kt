package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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

@ExperimentalCoroutinesApi
class DeleteExerciseRecordUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: DeleteExerciseRecordUseCase

    private val recordId = 123L

    @Before
    fun setUp() {
        useCase = DeleteExerciseRecordUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository deleteExerciseRecord 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteExerciseRecord(recordId) } returns successResult

        // When
        val result = useCase(recordId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExerciseRecord(recordId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository deleteExerciseRecord 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DELETE_FAILED", "Failed to delete record")
        coEvery { mockRepository.deleteExerciseRecord(recordId) } returns errorResult

        // When
        val result = useCase(recordId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExerciseRecord(recordId) }
        assertThat(result).isEqualTo(errorResult)
    }
}