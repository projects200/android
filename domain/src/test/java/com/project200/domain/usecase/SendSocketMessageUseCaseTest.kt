package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SendSocketMessageUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: SendSocketMessageUseCase

    @Before
    fun setUp() {
        useCase = SendSocketMessageUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 메시지 전송`() {
        // Given
        val message = "안녕하세요"
        every { mockRepository.sendMessage(message) } just runs

        // When
        useCase(message)

        // Then
        verify(exactly = 1) { mockRepository.sendMessage(message) }
    }

    @Test
    fun `빈 메시지 전송`() {
        // Given
        val emptyMessage = ""
        every { mockRepository.sendMessage(emptyMessage) } just runs

        // When
        useCase(emptyMessage)

        // Then
        verify(exactly = 1) { mockRepository.sendMessage(emptyMessage) }
    }

    @Test
    fun `긴 메시지 전송`() {
        // Given
        val longMessage = "A".repeat(1000)
        every { mockRepository.sendMessage(longMessage) } just runs

        // When
        useCase(longMessage)

        // Then
        verify(exactly = 1) { mockRepository.sendMessage(longMessage) }
    }

    @Test
    fun `특수문자 포함 메시지 전송`() {
        // Given
        val specialMessage = "안녕하세요! 😀🎉 #테스트"
        every { mockRepository.sendMessage(specialMessage) } just runs

        // When
        useCase(specialMessage)

        // Then
        verify(exactly = 1) { mockRepository.sendMessage(specialMessage) }
    }

    @Test
    fun `줄바꿈 포함 메시지 전송`() {
        // Given
        val multilineMessage = "첫 번째 줄\n두 번째 줄\n세 번째 줄"
        every { mockRepository.sendMessage(multilineMessage) } just runs

        // When
        useCase(multilineMessage)

        // Then
        verify(exactly = 1) { mockRepository.sendMessage(multilineMessage) }
    }
}
