package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordCreationResult
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
class CreateExerciseRecordUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: CreateExerciseRecordUseCase

    private val sampleRecord = ExerciseRecord(
        title = "테스트", detail = "", personalType = "",
        startedAt = LocalDateTime.now(), endedAt = LocalDateTime.now(), location = "", pictures = null
    )
    private val recordId = 1L
    private val earnedPoints = 3
    private val creationResult = ExerciseRecordCreationResult(recordId, earnedPoints)


    @Before
    fun setUp() {
        useCase = CreateExerciseRecordUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository createExerciseRecord 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(creationResult)
        coEvery { mockRepository.createExerciseRecord(sampleRecord) } returns successResult

        // When
        val result = useCase(sampleRecord)

        // Then
        coVerify(exactly = 1) { mockRepository.createExerciseRecord(sampleRecord) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository createExerciseRecord 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Error message")
        coEvery { mockRepository.createExerciseRecord(sampleRecord) } returns errorResult

        // When
        val result = useCase(sampleRecord)

        // Then
        coVerify(exactly = 1) { mockRepository.createExerciseRecord(sampleRecord) }
        assertThat(result).isEqualTo(errorResult)
    }
}