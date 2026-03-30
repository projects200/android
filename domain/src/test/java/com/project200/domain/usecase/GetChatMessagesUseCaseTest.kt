package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingModel
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class GetChatMessagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: GetChatMessagesUseCase

    private val sampleChattingModel = ChattingModel(
        hasNext = true,
        opponentActive = true,
        blockActive = false,
        messages = listOf(
            ChattingMessage(
                chatId = 1L,
                senderId = "member1",
                nickname = "사용자1",
                profileUrl = null,
                thumbnailImageUrl = null,
                content = "안녕하세요",
                chatType = "TALK",
                sentAt = LocalDateTime.now(),
                isMine = false
            )
        )
    )

    @Before
    fun setUp() {
        useCase = GetChatMessagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 채팅 메시지 목록 성공적으로 반환`() = runTest {
        // Given
        val chatRoomId = 1L
        val size = 20
        val successResult = BaseResult.Success(sampleChattingModel)
        coEvery { mockRepository.getChattingMessages(chatRoomId, null, size) } returns successResult

        // When
        val result = useCase(chatRoomId, null, size)

        // Then
        coVerify(exactly = 1) { mockRepository.getChattingMessages(chatRoomId, null, size) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `prevChatId로 페이징 조회`() = runTest {
        // Given
        val chatRoomId = 1L
        val prevChatId = 100L
        val size = 20
        val successResult = BaseResult.Success(sampleChattingModel)
        coEvery { mockRepository.getChattingMessages(chatRoomId, prevChatId, size) } returns successResult

        // When
        val result = useCase(chatRoomId, prevChatId, size)

        // Then
        coVerify(exactly = 1) { mockRepository.getChattingMessages(chatRoomId, prevChatId, size) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `채팅 메시지 조회 실패`() = runTest {
        // Given
        val chatRoomId = 1L
        val size = 20
        val errorResult = BaseResult.Error("ERR", "Failed to fetch messages")
        coEvery { mockRepository.getChattingMessages(chatRoomId, null, size) } returns errorResult

        // When
        val result = useCase(chatRoomId, null, size)

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
