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
class UploadExerciseRecordImagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: UploadExerciseRecordImagesUseCase

    private val recordId = 1L
    private val images = listOf("uri1", "uri2")

    @Before
    fun setUp() {
        useCase = UploadExerciseRecordImagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository uploadExerciseRecordImages 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(recordId)
        coEvery { mockRepository.uploadExerciseRecordImages(recordId, images) } returns successResult

        // When
        val result = useCase(recordId, images)

        // Then
        coVerify(exactly = 1) { mockRepository.uploadExerciseRecordImages(recordId, images) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository uploadExerciseRecordImages 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Error message")
        coEvery { mockRepository.uploadExerciseRecordImages(recordId, images) } returns errorResult

        // When
        val result = useCase(recordId, images)

        // Then
        coVerify(exactly = 1) { mockRepository.uploadExerciseRecordImages(recordId, images) }
        assertThat(result).isEqualTo(errorResult)
    }
}