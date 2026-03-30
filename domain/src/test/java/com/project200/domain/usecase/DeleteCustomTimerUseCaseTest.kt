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
class DeleteCustomTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: DeleteCustomTimerUseCase

    @Before
    fun setUp() {
        useCase = DeleteCustomTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 삭제 성공`() = runTest {
        // Given
        val customTimerId = 1L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteCustomTimer(customTimerId) } returns successResult

        // When
        val result = useCase(customTimerId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteCustomTimer(customTimerId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 삭제 실패`() = runTest {
        // Given
        val customTimerId = 1L
        val errorResult = BaseResult.Error("ERR", "Delete failed")
        coEvery { mockRepository.deleteCustomTimer(customTimerId) } returns errorResult

        // When
        val result = useCase(customTimerId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteCustomTimer(customTimerId) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `존재하지 않는 커스텀 타이머 삭제 시도`() = runTest {
        // Given
        val customTimerId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Timer not found")
        coEvery { mockRepository.deleteCustomTimer(customTimerId) } returns errorResult

        // When
        val result = useCase(customTimerId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteCustomTimer(customTimerId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
