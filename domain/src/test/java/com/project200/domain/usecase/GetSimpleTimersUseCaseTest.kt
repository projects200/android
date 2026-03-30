package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
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
class GetSimpleTimersUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: GetSimpleTimersUseCase

    private val sampleTimers = listOf(
        SimpleTimer(id = 1L, time = 30),
        SimpleTimer(id = 2L, time = 60),
        SimpleTimer(id = 3L, time = 90)
    )

    @Before
    fun setUp() {
        useCase = GetSimpleTimersUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleTimers)
        coEvery { mockRepository.getSimpleTimers() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getSimpleTimers() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(3)
    }

    @Test
    fun `빈 타이머 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<SimpleTimer>())
        coEvery { mockRepository.getSimpleTimers() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `타이머 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch timers")
        coEvery { mockRepository.getSimpleTimers() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
