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
class EditPreferredExerciseUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: EditPreferredExerciseUseCase

    private val samplePreferredExercises = listOf(
        PreferredExercise(
            preferredExerciseId = 1L,
            exerciseTypeId = 10L,
            name = "웨이트",
            skillLevel = "고급",
            daysOfWeek = listOf(true, true, true, false, true, false, false),
            imageUrl = "https://example.com/weight.jpg"
        ),
        PreferredExercise(
            preferredExerciseId = 2L,
            exerciseTypeId = 20L,
            name = "러닝",
            skillLevel = "중급",
            daysOfWeek = listOf(false, true, false, true, false, true, true),
            imageUrl = "https://example.com/running.jpg"
        )
    )

    @Before
    fun setUp() {
        useCase = EditPreferredExerciseUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 선호 운동 수정 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editPreferredExercise(samplePreferredExercises) } returns successResult

        // When
        val result = useCase(samplePreferredExercises)

        // Then
        coVerify(exactly = 1) { mockRepository.editPreferredExercise(samplePreferredExercises) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `선호 운동 수정 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("EDIT_FAILED", "Failed to edit preferred exercises")
        coEvery { mockRepository.editPreferredExercise(samplePreferredExercises) } returns errorResult

        // When
        val result = useCase(samplePreferredExercises)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("EDIT_FAILED")
    }

    @Test
    fun `단일 선호 운동 수정`() = runTest {
        // Given
        val singleExercise = listOf(samplePreferredExercises[0])
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editPreferredExercise(singleExercise) } returns successResult

        // When
        val result = useCase(singleExercise)

        // Then
        coVerify(exactly = 1) { mockRepository.editPreferredExercise(singleExercise) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `스킬 레벨 변경`() = runTest {
        // Given
        val updatedExercise = listOf(samplePreferredExercises[0].copy(skillLevel = "초급"))
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editPreferredExercise(updatedExercise) } returns successResult

        // When
        val result = useCase(updatedExercise)

        // Then
        coVerify(exactly = 1) { mockRepository.editPreferredExercise(updatedExercise) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `운동 요일 변경`() = runTest {
        // Given
        val updatedDays = listOf(true, true, true, true, true, true, true)
        val updatedExercise = listOf(samplePreferredExercises[0].copy(daysOfWeek = updatedDays))
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editPreferredExercise(updatedExercise) } returns successResult

        // When
        val result = useCase(updatedExercise)

        // Then
        coVerify(exactly = 1) { mockRepository.editPreferredExercise(updatedExercise) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
