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

    private lateinit var useCase: EditCustomTimerUseCase

    private val localId = "local-1"
    private val title = "수정된 타이머"
    private val sampleSteps = listOf(
        Step(order = 1, time = 30, name = "운동"),
        Step(order = 2, time = 10, name = "휴식")
    )

    @Before
    fun setUp() {
        useCase = EditCustomTimerUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 이름과 스텝을 그대로 repository에 위임한다`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editCustomTimer(localId, title, sampleSteps) } returns successResult

        // When
        val result = useCase(localId, title, sampleSteps)

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimer(localId, title, sampleSteps) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 수정 실패를 그대로 돌려준다`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Edit failed")
        coEvery { mockRepository.editCustomTimer(localId, title, sampleSteps) } returns errorResult

        // When
        val result = useCase(localId, title, sampleSteps)

        // Then
        coVerify(exactly = 1) { mockRepository.editCustomTimer(localId, title, sampleSteps) }
        assertThat(result).isEqualTo(errorResult)
    }
}
