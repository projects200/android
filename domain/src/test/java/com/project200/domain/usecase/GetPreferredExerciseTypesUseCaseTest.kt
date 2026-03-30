package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.PreferredExercise
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
class GetPreferredExerciseTypesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetPreferredExerciseTypesUseCase

    private val sampleExerciseTypes = listOf(
        ExerciseType(id = 1L, name = "웨이트", imageUrl = "https://example.com/weight.jpg"),
        ExerciseType(id = 2L, name = "러닝", imageUrl = "https://example.com/running.jpg"),
        ExerciseType(id = 3L, name = "수영", imageUrl = null)
    )

    @Before
    fun setUp() {
        useCase = GetPreferredExerciseTypesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 타입이 PreferredExercise로 변환되어 반환`() = runTest {
        // Given
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(sampleExerciseTypes)

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getPreferredExerciseTypes() }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
        val data = (result as BaseResult.Success).data
        assertThat(data).hasSize(3)
        assertThat(data[0].exerciseTypeId).isEqualTo(1L)
        assertThat(data[0].name).isEqualTo("웨이트")
    }

    @Test
    fun `변환된 PreferredExercise의 기본값 확인`() = runTest {
        // Given
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(sampleExerciseTypes)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data[0]
        assertThat(data.preferredExerciseId).isEqualTo(-1)
        assertThat(data.skillLevel).isEmpty()
        assertThat(data.daysOfWeek).containsExactly(false, false, false, false, false, false, false)
    }

    @Test
    fun `빈 운동 타입 목록 반환`() = runTest {
        // Given
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(emptyList())

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
    fun `이미지가 없는 운동 타입도 정상 변환`() = runTest {
        // Given
        val typeWithoutImage = listOf(ExerciseType(id = 1L, name = "요가", imageUrl = null))
        coEvery { mockRepository.getPreferredExerciseTypes() } returns BaseResult.Success(typeWithoutImage)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data[0]
        assertThat(data.imageUrl).isNull()
        assertThat(data.name).isEqualTo("요가")
    }
}
