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
class GetNewChatMessagesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: GetNewChatMessagesUseCase

    private val sampleChattingModel = ChattingModel(
        hasNext = false,
        opponentActive = true,
        blockActive = false,
        messages = listOf(
            ChattingMessage(
                chatId = 101L,
                senderId = "member2",
                nickname = "사용자2",
                profileUrl = null,
                thumbnailImageUrl = null,
                content = "새 메시지입니다",
                chatType = "TALK",
                sentAt = LocalDateTime.now(),
                isMine = false
            )
        )
    )

    @Before
    fun setUp() {
        useCase = GetNewChatMessagesUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 새 채팅 메시지 성공적으로 반환`() = runTest {
        // Given
        val chatRoomId = 1L
        val lastChatId = 100L
        val successResult = BaseResult.Success(sampleChattingModel)
        coEvery { mockRepository.getNewChattingMessages(chatRoomId, lastChatId) } returns successResult

        // When
        val result = useCase(chatRoomId, lastChatId)

        // Then
        coVerify(exactly = 1) { mockRepository.getNewChattingMessages(chatRoomId, lastChatId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `lastChatId가 null인 경우`() = runTest {
        // Given
        val chatRoomId = 1L
        val successResult = BaseResult.Success(sampleChattingModel)
        coEvery { mockRepository.getNewChattingMessages(chatRoomId, null) } returns successResult

        // When
        val result = useCase(chatRoomId, null)

        // Then
        coVerify(exactly = 1) { mockRepository.getNewChattingMessages(chatRoomId, null) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `새 채팅 메시지 조회 실패`() = runTest {
        // Given
        val chatRoomId = 1L
        val errorResult = BaseResult.Error("ERR", "Failed to fetch new messages")
        coEvery { mockRepository.getNewChattingMessages(chatRoomId, null) } returns errorResult

        // When
        val result = useCase(chatRoomId)

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
