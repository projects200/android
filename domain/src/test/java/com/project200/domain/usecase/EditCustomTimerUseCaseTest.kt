package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
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
class EditCustomTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    @MockK
    private lateinit var mockEditCustomTimerNameUseCase: EditCustomTimerNameUseCase

    private lateinit var useCase: EditCustomTimerUseCase

    private val customTimerId = 1L
    private val title = "수정된 타이머"
    private val sampleSteps = listOf(
        Step(id = 1L, order = 1, time = 30, name = "운동"),
        Step(id = 2L, order = 2, time = 10, name = "휴식")
    )

    @Before
    fun setUp() {
        useCase = EditCustomTimerUseCase(mockRepository, mockEditCustomTimerNameUseCase)
    }

    @Test
    fun `스텝이 변경된 경우 repository editCustomTimer 호출`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editCustomTimer(customTimerId, title, sampleSteps) } returns successResult

        // When
        val result = useCase(
            hasTitleChanged = true,
            hasStepsChanged = true,
            customTimerId = customTimerId,
            title = title,
            steps = sampleSteps
        )

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimer(customTimerId, title, sampleSteps) }
        coVerify(exactly = 0) { mockEditCustomTimerNameUseCase(any(), any()) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `타이틀만 변경된 경우 editCustomTimerNameUseCase 호출`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockEditCustomTimerNameUseCase(customTimerId, title) } returns successResult

        // When
        val result = useCase(
            hasTitleChanged = true,
            hasStepsChanged = false,
            customTimerId = customTimerId,
            title = title,
            steps = sampleSteps
        )

        // Then
        coVerify(exactly = 0) { mockRepository.editCustomTimer(any(), any(), any()) }
        coVerify(exactly = 1) { mockEditCustomTimerNameUseCase(customTimerId, title) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `아무것도 변경되지 않은 경우 Error 반환`() = runTest {
        // When
        val result = useCase(
            hasTitleChanged = false,
            hasStepsChanged = false,
            customTimerId = customTimerId,
            title = title,
            steps = sampleSteps
        )

        // Then
        coVerify(exactly = 0) { mockRepository.editCustomTimer(any(), any(), any()) }
        coVerify(exactly = 0) { mockEditCustomTimerNameUseCase(any(), any()) }
        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
    }

    @Test
    fun `스텝 변경 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Edit failed")
        coEvery { mockRepository.editCustomTimer(customTimerId, title, sampleSteps) } returns errorResult

        // When
        val result = useCase(
            hasTitleChanged = true,
            hasStepsChanged = true,
            customTimerId = customTimerId,
            title = title,
            steps = sampleSteps
        )

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
