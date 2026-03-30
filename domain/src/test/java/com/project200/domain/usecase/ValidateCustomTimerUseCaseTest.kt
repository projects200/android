package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.CustomTimerValidationResult
import com.project200.domain.model.Step
import org.junit.Before
import org.junit.Test

class ValidateCustomTimerUseCaseTest {

    private lateinit var useCase: ValidateCustomTimerUseCase

    @Before
    fun setUp() {
        useCase = ValidateCustomTimerUseCase()
    }

    @Test
    fun `유효한 타이머는 Success 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 30, name = "운동"),
            Step(id = 2L, order = 2, time = 10, name = "휴식")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.Success)
    }

    @Test
    fun `빈 타이틀은 EmptyTitle 반환`() {
        // Given
        val title = ""
        val steps = listOf(Step(id = 1L, order = 1, time = 30, name = "운동"))

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.EmptyTitle)
    }

    @Test
    fun `공백만 있는 타이틀은 EmptyTitle 반환`() {
        // Given
        val title = "   "
        val steps = listOf(Step(id = 1L, order = 1, time = 30, name = "운동"))

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.EmptyTitle)
    }

    @Test
    fun `빈 스텝 목록은 NoSteps 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = emptyList<Step>()

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.NoSteps)
    }

    @Test
    fun `5초 미만 스텝이 있으면 InvalidStepTime 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 30, name = "운동"),
            Step(id = 2L, order = 2, time = 4, name = "휴식")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.InvalidStepTime)
    }

    @Test
    fun `0초 스텝이 있으면 InvalidStepTime 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 0, name = "운동")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.InvalidStepTime)
    }

    @Test
    fun `빈 스텝 이름이 있으면 EmptyStepName 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 30, name = ""),
            Step(id = 2L, order = 2, time = 10, name = "휴식")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.EmptyStepName)
    }

    @Test
    fun `공백만 있는 스텝 이름은 EmptyStepName 반환`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 30, name = "   ")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.EmptyStepName)
    }

    @Test
    fun `정확히 5초인 스텝은 유효하다`() {
        // Given
        val title = "HIIT 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 5, name = "운동")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.Success)
    }

    @Test
    fun `여러 유효한 스텝이 있는 타이머는 Success 반환`() {
        // Given
        val title = "복잡한 타이머"
        val steps = listOf(
            Step(id = 1L, order = 1, time = 60, name = "준비운동"),
            Step(id = 2L, order = 2, time = 45, name = "고강도"),
            Step(id = 3L, order = 3, time = 15, name = "휴식"),
            Step(id = 4L, order = 4, time = 45, name = "고강도"),
            Step(id = 5L, order = 5, time = 60, name = "마무리")
        )

        // When
        val result = useCase(title, steps)

        // Then
        assertThat(result).isEqualTo(CustomTimerValidationResult.Success)
    }
}
