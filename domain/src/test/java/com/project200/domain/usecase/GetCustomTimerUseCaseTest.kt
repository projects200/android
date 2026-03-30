package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
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
class GetCustomTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: GetCustomTimerUseCase

    private val sampleCustomTimer = CustomTimer(
        id = 1L,
        name = "HIIT 타이머",
        steps = listOf(
            Step(id = 1L, order = 1, time = 30, name = "운동"),
            Step(id = 2L, order = 2, time = 10, name = "휴식")
        )
    )

    @Before
    fun setUp() {
        useCase = GetCustomTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 성공적으로 반환`() = runTest {
        // Given
        val customTimerId = 1L
        val successResult = BaseResult.Success(sampleCustomTimer)
        coEvery { mockRepository.getCustomTimer(customTimerId) } returns successResult

        // When
        val result = useCase(customTimerId)

        // Then
        coVerify(exactly = 1) { mockRepository.getCustomTimer(customTimerId) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.id).isEqualTo(customTimerId)
    }

    @Test
    fun `존재하지 않는 커스텀 타이머 조회`() = runTest {
        // Given
        val customTimerId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Custom timer not found")
        coEvery { mockRepository.getCustomTimer(customTimerId) } returns errorResult

        // When
        val result = useCase(customTimerId)

        // Then
        coVerify(exactly = 1) { mockRepository.getCustomTimer(customTimerId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
