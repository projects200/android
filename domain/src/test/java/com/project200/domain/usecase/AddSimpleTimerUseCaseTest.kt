package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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
class AddSimpleTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: AddSimpleTimerUseCase

    @Before
    fun setUp() {
        useCase = AddSimpleTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 추가 성공`() = runTest {
        // Given
        val time = 60
        val newTimerId = 1L
        val successResult = BaseResult.Success(newTimerId)
        coEvery { mockRepository.addSimpleTimer(time) } returns successResult

        // When
        val result = useCase(time)

        // Then
        coVerify(exactly = 1) { mockRepository.addSimpleTimer(time) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isEqualTo(newTimerId)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 추가 실패`() = runTest {
        // Given
        val time = 60
        val errorResult = BaseResult.Error("ERR", "Failed to add timer")
        coEvery { mockRepository.addSimpleTimer(time) } returns errorResult

        // When
        val result = useCase(time)

        // Then
        coVerify(exactly = 1) { mockRepository.addSimpleTimer(time) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `0초 타이머 추가`() = runTest {
        // Given
        val time = 0
        val successResult = BaseResult.Success(1L)
        coEvery { mockRepository.addSimpleTimer(time) } returns successResult

        // When
        val result = useCase(time)

        // Then
        coVerify(exactly = 1) { mockRepository.addSimpleTimer(time) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
