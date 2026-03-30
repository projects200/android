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
class ExitChatRoomUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: ExitChatRoomUseCase

    @Before
    fun setUp() {
        useCase = ExitChatRoomUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 채팅방 나가기 성공`() = runTest {
        // Given
        val chatRoomId = 1L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteChatRoom(chatRoomId) } returns successResult

        // When
        val result = useCase(chatRoomId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteChatRoom(chatRoomId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 채팅방 나가기 실패`() = runTest {
        // Given
        val chatRoomId = 1L
        val errorResult = BaseResult.Error("ERR", "Failed to exit chat room")
        coEvery { mockRepository.deleteChatRoom(chatRoomId) } returns errorResult

        // When
        val result = useCase(chatRoomId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteChatRoom(chatRoomId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
