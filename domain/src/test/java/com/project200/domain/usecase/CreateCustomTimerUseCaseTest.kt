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
class CreateCustomTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: CreateCustomTimerUseCase

    private val sampleSteps = listOf(
        Step(id = -1, order = 1, time = 30, name = "운동"),
        Step(id = -1, order = 2, time = 10, name = "휴식")
    )

    @Before
    fun setUp() {
        useCase = CreateCustomTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 생성 성공`() = runTest {
        // Given
        val title = "새 타이머"
        val newTimerId = 1L
        val successResult = BaseResult.Success(newTimerId)
        coEvery { mockRepository.createCustomTimer(title, sampleSteps) } returns successResult

        // When
        val result = useCase(title, sampleSteps)

        // Then
        coVerify(exactly = 1) { mockRepository.createCustomTimer(title, sampleSteps) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isEqualTo(newTimerId)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 생성 실패`() = runTest {
        // Given
        val title = "새 타이머"
        val errorResult = BaseResult.Error("ERR", "Failed to create timer")
        coEvery { mockRepository.createCustomTimer(title, sampleSteps) } returns errorResult

        // When
        val result = useCase(title, sampleSteps)

        // Then
        coVerify(exactly = 1) { mockRepository.createCustomTimer(title, sampleSteps) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 스텝 목록으로 타이머 생성`() = runTest {
        // Given
        val title = "빈 타이머"
        val emptySteps = emptyList<Step>()
        val successResult = BaseResult.Success(1L)
        coEvery { mockRepository.createCustomTimer(title, emptySteps) } returns successResult

        // When
        val result = useCase(title, emptySteps)

        // Then
        coVerify(exactly = 1) { mockRepository.createCustomTimer(title, emptySteps) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
