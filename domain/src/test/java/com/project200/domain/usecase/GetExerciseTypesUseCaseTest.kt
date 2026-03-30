package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
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
class GetExerciseTypesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetExerciseTypesUseCase

    private val sampleExerciseTypes = listOf(
        ExerciseType(id = 1L, name = "웨이트", imageUrl = "https://example.com/weight.jpg"),
        ExerciseType(id = 2L, name = "러닝", imageUrl = "https://example.com/running.jpg"),
        ExerciseType(id = 3L, name = "수영", imageUrl = null)
    )

    @Before
    fun setUp() {
        useCase = GetExerciseTypesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 타입 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleExerciseTypes)
        coEvery { mockRepository.getPreferredExerciseTypes() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getPreferredExerciseTypes() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(3)
    }

    @Test
    fun `빈 운동 타입 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<ExerciseType>())
        coEvery { mockRepository.getPreferredExerciseTypes() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `운동 타입 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch exercise types")
        coEvery { mockRepository.getPreferredExerciseTypes() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `이미지 없는 운동 타입 포함`() = runTest {
        // Given
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(sampleExerciseTypes)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data[2].imageUrl).isNull()
        assertThat(data[2].name).isEqualTo("수영")
    }

    @Test
    fun `다양한 운동 타입 반환`() = runTest {
        // Given
        val manyTypes = (1L..10L).map {
            ExerciseType(id = it, name = "운동$it", imageUrl = null)
        }
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(manyTypes)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).hasSize(10)
    }
}
