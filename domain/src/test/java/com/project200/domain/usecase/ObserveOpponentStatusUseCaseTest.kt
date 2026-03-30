package com.project200.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.OpponentStatus
import com.project200.domain.repository.ChatSocketRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ObserveOpponentStatusUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: ObserveOpponentStatusUseCase

    private val statusFlow = MutableSharedFlow<OpponentStatus>()

    @Before
    fun setUp() {
        every { mockRepository.opponentStatusChanges } returns statusFlow
        useCase = ObserveOpponentStatusUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 상태 Flow 반환`() = runTest {
        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(statusFlow)
    }

    @Test
    fun `상대방 퇴장 상태 수신`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            statusFlow.emit(OpponentStatus.Left)
            val received = awaitItem()
            assertThat(received).isEqualTo(OpponentStatus.Left)
        }
    }

    @Test
    fun `상대방 차단 상태 수신`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            statusFlow.emit(OpponentStatus.Blocked)
            val received = awaitItem()
            assertThat(received).isEqualTo(OpponentStatus.Blocked)
        }
    }

    @Test
    fun `여러 상태 변경 순차적으로 수신`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            statusFlow.emit(OpponentStatus.Left)
            assertThat(awaitItem()).isEqualTo(OpponentStatus.Left)

            statusFlow.emit(OpponentStatus.Blocked)
            assertThat(awaitItem()).isEqualTo(OpponentStatus.Blocked)
        }
    }

    @Test
    fun `상태가 Left인지 확인`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            statusFlow.emit(OpponentStatus.Left)
            val received = awaitItem()
            assertThat(received).isInstanceOf(OpponentStatus.Left::class.java)
        }
    }

    @Test
    fun `상태가 Blocked인지 확인`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            statusFlow.emit(OpponentStatus.Blocked)
            val received = awaitItem()
            assertThat(received).isInstanceOf(OpponentStatus.Blocked::class.java)
        }
    }
}
