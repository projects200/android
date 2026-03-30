package com.project200.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.ChattingMessage
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ObserveSocketMessagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: ObserveSocketMessagesUseCase

    private val messagesFlow = MutableSharedFlow<ChattingMessage>()

    private val sampleMessage = ChattingMessage(
        chatId = 1L,
        senderId = "sender123",
        nickname = "테스트유저",
        profileUrl = "https://example.com/profile.jpg",
        thumbnailImageUrl = "https://example.com/thumb.jpg",
        content = "안녕하세요",
        chatType = "TALK",
        sentAt = LocalDateTime.now(),
        isMine = false,
        showProfile = true,
        showTime = true
    )

    @Before
    fun setUp() {
        every { mockRepository.incomingMessages } returns messagesFlow
        useCase = ObserveSocketMessagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 메시지 Flow 반환`() = runTest {
        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(messagesFlow)
    }

    @Test
    fun `메시지 수신 시 Flow에서 방출`() = runTest {
        // Given
        val flow = useCase()

        // When & Then
        flow.test {
            messagesFlow.emit(sampleMessage)
            val received = awaitItem()
            assertThat(received).isEqualTo(sampleMessage)
            assertThat(received.content).isEqualTo("안녕하세요")
        }
    }

    @Test
    fun `여러 메시지 순차적으로 수신`() = runTest {
        // Given
        val message2 = sampleMessage.copy(chatId = 2L, content = "반갑습니다")
        val flow = useCase()

        // When & Then
        flow.test {
            messagesFlow.emit(sampleMessage)
            assertThat(awaitItem().content).isEqualTo("안녕하세요")

            messagesFlow.emit(message2)
            assertThat(awaitItem().content).isEqualTo("반갑습니다")
        }
    }

    @Test
    fun `내 메시지 수신`() = runTest {
        // Given
        val myMessage = sampleMessage.copy(isMine = true)
        val flow = useCase()

        // When & Then
        flow.test {
            messagesFlow.emit(myMessage)
            val received = awaitItem()
            assertThat(received.isMine).isTrue()
        }
    }
}
