package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
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
class GetExerciseRecordDetailUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ExerciseRecordRepository

    private lateinit var useCase: GetExerciseRecordDetailUseCase

    // 테스트용 샘플 데이터
    private val now: LocalDateTime = LocalDateTime.now()
    private val sampleRecord = ExerciseRecord(
        title = "Morning Swim",
        detail = "A good 1km swim.",
        personalType = "Swimming",
        startedAt = now.minusHours(2),
        endedAt = now.minusHours(1),
        location = "Community Pool",
        pictures = listOf(ExerciseRecordPicture(1L, "http://example.com/swim.jpg"))
    )

    @Before
    fun setUp() {
        useCase = GetExerciseRecordDetailUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 Repository의 getExerciseDetail을 호출하고 결과를 반환`() = runTest {
        // Given
        val recordId = 789L
        val successResultFromRepo = BaseResult.Success(sampleRecord)

        coEvery { mockRepository.getExerciseDetail(recordId) } returns successResultFromRepo

        // When
        val actualResult = useCase.invoke(recordId)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseDetail(recordId) }
        assertThat(actualResult).isEqualTo(successResultFromRepo)
        assertThat((actualResult as BaseResult.Success).data.personalType).isEqualTo("Swimming")
    }

    @Test
    fun `invoke 호출 시 Repository가 에러를 반환하면 해당 에러를 그대로 반환`() = runTest {
        // Given
        val recordId = 101L
        val errorResultFromRepo = BaseResult.Error( "101", "Database error")
        coEvery { mockRepository.getExerciseDetail(recordId) } returns errorResultFromRepo

        // When
        val actualResult = useCase.invoke(recordId)

        // Then
        coVerify(exactly = 1) { mockRepository.getExerciseDetail(recordId) }
        assertThat(actualResult).isEqualTo(errorResultFromRepo)
        assertThat((actualResult as BaseResult.Error).errorCode).isEqualTo("101")
    }
}