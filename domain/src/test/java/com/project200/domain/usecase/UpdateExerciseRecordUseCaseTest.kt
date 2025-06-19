package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class UpdateExerciseRecordUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: UpdateExerciseRecordUseCase

    private val recordId = 1L
    private val sampleRecordToUpdate = ExerciseRecord(
        title = "Updated Title", detail = "Updated detail", personalType = "Running",
        startedAt = LocalDateTime.now(), endedAt = LocalDateTime.now(), location = "Park", pictures = null
    )

    @Before
    fun setUp() {
        useCase = UpdateExerciseRecordUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository updateExerciseRecord 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(recordId)
        coEvery { mockRepository.updateExerciseRecord(sampleRecordToUpdate, recordId) } returns successResult

        // When
        val result = useCase(recordId, sampleRecordToUpdate)

        // Then
        coVerify(exactly = 1) { mockRepository.updateExerciseRecord(sampleRecordToUpdate, recordId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository updateExerciseRecord 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("UPDATE_ERROR", "Error updating record")
        coEvery { mockRepository.updateExerciseRecord(sampleRecordToUpdate, recordId) } returns errorResult

        // When
        val result = useCase(recordId, sampleRecordToUpdate)

        // Then
        coVerify(exactly = 1) { mockRepository.updateExerciseRecord(sampleRecordToUpdate, recordId) }
        assertThat(result).isEqualTo(errorResult)
    }
}