package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
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
class GetLocalCustomTimerListUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: TimerRepository

    private lateinit var useCase: GetLocalCustomTimerListUseCase

    @Before
    fun setUp() {
        useCase = GetLocalCustomTimerListUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 로컬 커스텀 타이머 목록을 반환한다`() = runTest {
        // Given
        val sampleCustomTimers = listOf(CustomTimer(localId = "local-1", serverId = 1L, name = "8세트"))
        val successResult = BaseResult.Success(sampleCustomTimers)
        coEvery { mockRepository.getLocalCustomTimerList() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getLocalCustomTimerList() }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `서버를 보지 않는다`() = runTest {
        // Given
        coEvery { mockRepository.getLocalCustomTimerList() } returns BaseResult.Success(emptyList())

        // When
        useCase()

        // Then
        coVerify(exactly = 0) { mockRepository.getCustomTimerList() }
    }
}
