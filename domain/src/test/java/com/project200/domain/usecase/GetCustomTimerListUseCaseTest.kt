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
class GetCustomTimerListUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: GetCustomTimerListUseCase

    private val sampleCustomTimers = listOf(
        CustomTimer(
            id = 1L,
            name = "HIIT 타이머",
            steps = listOf(
                Step(id = 1L, order = 1, time = 30, name = "운동"),
                Step(id = 2L, order = 2, time = 10, name = "휴식")
            )
        ),
        CustomTimer(
            id = 2L,
            name = "타바타",
            steps = listOf(
                Step(id = 3L, order = 1, time = 20, name = "고강도"),
                Step(id = 4L, order = 2, time = 10, name = "휴식")
            )
        )
    )

    @Before
    fun setUp() {
        useCase = GetCustomTimerListUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 커스텀 타이머 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleCustomTimers)
        coEvery { mockRepository.getCustomTimerList() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getCustomTimerList() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 커스텀 타이머 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<CustomTimer>())
        coEvery { mockRepository.getCustomTimerList() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `커스텀 타이머 목록 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch custom timers")
        coEvery { mockRepository.getCustomTimerList() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
