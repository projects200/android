package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ChattingRepository
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
class SendChatMessageUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: SendChatMessageUseCase

    @Before
    fun setUp() {
        useCase = SendChatMessageUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 메시지 전송 성공`() = runTest {
        // Given
        val chatRoomId = 1L
        val content = "안녕하세요!"
        val messageId = 100L
        val successResult = BaseResult.Success(messageId)
        coEvery { mockRepository.sendChattingMessage(chatRoomId, content) } returns successResult

        // When
        val result = useCase(chatRoomId, content)

        // Then
        coVerify(exactly = 1) { mockRepository.sendChattingMessage(chatRoomId, content) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isEqualTo(messageId)
    }

    @Test
    fun `invoke 호출 시 메시지 전송 실패`() = runTest {
        // Given
        val chatRoomId = 1L
        val content = "안녕하세요!"
        val errorResult = BaseResult.Error("ERR", "Failed to send message")
        coEvery { mockRepository.sendChattingMessage(chatRoomId, content) } returns errorResult

        // When
        val result = useCase(chatRoomId, content)

        // Then
        coVerify(exactly = 1) { mockRepository.sendChattingMessage(chatRoomId, content) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 메시지 전송`() = runTest {
        // Given
        val chatRoomId = 1L
        val content = ""
        val successResult = BaseResult.Success(101L)
        coEvery { mockRepository.sendChattingMessage(chatRoomId, content) } returns successResult

        // When
        val result = useCase(chatRoomId, content)

        // Then
        coVerify(exactly = 1) { mockRepository.sendChattingMessage(chatRoomId, content) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
