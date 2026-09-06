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
class EditSimpleTimerUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: EditSimpleTimerUseCase

    @Before
    fun setUp() {
        useCase = EditSimpleTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 수정 성공`() = runTest {
        // Given
        val localId = "local-1"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editSimpleTimer(localId, 90) } returns successResult

        // When
        val result = useCase(localId, 90)

        // Then
        coVerify(exactly = 1) { mockRepository.editSimpleTimer(localId, 90) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 심플 타이머 수정 실패`() = runTest {
        // Given
        val localId = "local-1"
        val errorResult = BaseResult.Error("ERR", "Edit failed")
        coEvery { mockRepository.editSimpleTimer(localId, 90) } returns errorResult

        // When
        val result = useCase(localId, 90)

        // Then
        coVerify(exactly = 1) { mockRepository.editSimpleTimer(localId, 90) }
        assertThat(result).isEqualTo(errorResult)
    }
}
