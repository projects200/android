package com.project200.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
class ObserveSocketErrorsUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: ObserveSocketErrorsUseCase

    private val errorsFlow = MutableSharedFlow<String>()

    @Before
    fun setUp() {
        every { mockRepository.socketErrors } returns errorsFlow
        useCase = ObserveSocketErrorsUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 에러 Flow 반환`() = runTest {
        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorsFlow)
    }

    @Test
    fun `에러 수신 시 Flow에서 방출`() = runTest {
        // Given
        val errorMessage = "Connection failed"
        val flow = useCase()

        // When & Then
        flow.test {
            errorsFlow.emit(errorMessage)
            val received = awaitItem()
            assertThat(received).isEqualTo(errorMessage)
        }
    }

    @Test
    fun `여러 에러 순차적으로 수신`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            errorsFlow.emit("Connection timeout")
            assertThat(awaitItem()).isEqualTo("Connection timeout")

            errorsFlow.emit("Server error")
            assertThat(awaitItem()).isEqualTo("Server error")
        }
    }

    @Test
    fun `네트워크 오류 수신`() = runTest {
        // Given
        val networkError = "Network unavailable"
        val flow = useCase()

        // When & Then
        flow.test {
            errorsFlow.emit(networkError)
            assertThat(awaitItem()).contains("Network")
        }
    }

    @Test
    fun `인증 오류 수신`() = runTest {
        // Given
        val authError = "Authentication failed"
        val flow = useCase()

        // When & Then
        flow.test {
            errorsFlow.emit(authError)
            assertThat(awaitItem()).contains("Authentication")
        }
    }
}
