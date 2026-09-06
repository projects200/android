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
class GetLocalSimpleTimersUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: GetLocalSimpleTimersUseCase

    @Before
    fun setUp() {
        useCase = GetLocalSimpleTimersUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 로컬 심플 타이머 목록을 반환한다`() = runTest {
        // Given
        val sampleTimers = listOf(SimpleTimer(localId = "local-1", serverId = 1L, time = 30))
        val successResult = BaseResult.Success(sampleTimers)
        coEvery { mockRepository.getLocalSimpleTimers() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getLocalSimpleTimers() }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `서버를 보지 않는다`() = runTest {
        // Given
        coEvery { mockRepository.getLocalSimpleTimers() } returns BaseResult.Success(emptyList())

        // When
        useCase()

        // Then
        coVerify(exactly = 0) { mockRepository.getSimpleTimers() }
    }
}
