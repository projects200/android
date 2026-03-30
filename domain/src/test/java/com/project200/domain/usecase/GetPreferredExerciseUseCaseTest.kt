package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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
class GetPreferredExerciseUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: GetPreferredExerciseUseCase

    private val samplePreferredExercises = listOf(
        PreferredExercise(
            preferredExerciseId = 1L,
            exerciseTypeId = 10L,
            name = "웨이트",
            skillLevel = "중급",
            daysOfWeek = listOf(true, false, true, false, true, false, false),
            imageUrl = "https://example.com/weight.jpg"
        ),
        PreferredExercise(
            preferredExerciseId = 2L,
            exerciseTypeId = 20L,
            name = "러닝",
            skillLevel = "초급",
            daysOfWeek = listOf(false, true, false, true, false, true, true),
            imageUrl = "https://example.com/running.jpg"
        )
    )

    @Before
    fun setUp() {
        useCase = GetPreferredExerciseUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 선호 운동 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(samplePreferredExercises)
        coEvery { mockRepository.getPreferredExercises() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getPreferredExercises() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 선호 운동 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<PreferredExercise>())
        coEvery { mockRepository.getPreferredExercises() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `선호 운동 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch preferred exercises")
        coEvery { mockRepository.getPreferredExercises() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `이미지가 없는 선호 운동 포함`() = runTest {
        // Given
        val exerciseWithoutImage = listOf(
            PreferredExercise(
                preferredExerciseId = 1L,
                exerciseTypeId = 10L,
                name = "요가",
                skillLevel = "초급",
                daysOfWeek = listOf(true, true, true, true, true, false, false),
                imageUrl = null
            )
        )
        coEvery { mockRepository.getPreferredExercises() } returns BaseResult.Success(exerciseWithoutImage)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data[0].imageUrl).isNull()
    }

    @Test
    fun `다양한 스킬 레벨의 운동 반환`() = runTest {
        // Given
        val exercises = listOf(
            samplePreferredExercises[0].copy(skillLevel = "초급"),
            samplePreferredExercises[1].copy(skillLevel = "고급")
        )
        coEvery { mockRepository.getPreferredExercises() } returns BaseResult.Success(exercises)

        // When
        val result = useCase()

        // Then
        val data = (result as BaseResult.Success).data
        assertThat(data[0].skillLevel).isEqualTo("초급")
        assertThat(data[1].skillLevel).isEqualTo("고급")
    }
}
