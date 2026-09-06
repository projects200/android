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
class DeleteSimpleTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: DeleteSimpleTimerUseCase

    @Before
    fun setUp() {
        useCase = DeleteSimpleTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 삭제 성공`() = runTest {
        // Given
        val localId = "local-1"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteSimpleTimer(localId) } returns successResult

        // When
        val result = useCase(localId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteSimpleTimer(localId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 삭제 실패`() = runTest {
        // Given
        val localId = "local-1"
        val errorResult = BaseResult.Error("ERR", "Delete failed")
        coEvery { mockRepository.deleteSimpleTimer(localId) } returns errorResult

        // When
        val result = useCase(localId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteSimpleTimer(localId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
