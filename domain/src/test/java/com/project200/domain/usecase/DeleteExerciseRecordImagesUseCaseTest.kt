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
class DeleteExerciseRecordImagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: DeleteExerciseRecordImagesUseCase

    private val exerciseId = 1L
    private val imageIdsToDelete = listOf(10L, 11L)

    @Before
    fun setUp() {
        useCase = DeleteExerciseRecordImagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository deleteExerciseRecordImages 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteExerciseRecordImages(exerciseId, imageIdsToDelete) } returns successResult

        // When
        val result = useCase(exerciseId, imageIdsToDelete)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExerciseRecordImages(exerciseId, imageIdsToDelete) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository deleteExerciseRecordImages 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DELETE_IMAGE_ERROR", "Error deleting images")
        coEvery { mockRepository.deleteExerciseRecordImages(exerciseId, imageIdsToDelete) } returns errorResult

        // When
        val result = useCase(exerciseId, imageIdsToDelete)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExerciseRecordImages(exerciseId, imageIdsToDelete) }
        assertThat(result).isEqualTo(errorResult)
    }
}