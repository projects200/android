package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom
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
class GetChattingRoomsUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: GetChattingRoomsUseCase

    private val sampleChattingRooms = listOf(
        ChattingRoom(
            opponentMemberId = "member1",
            id = 1L,
            nickname = "사용자1",
            profileImageUrl = null,
            thumbnailImageUrl = null,
            lastMessage = "안녕하세요",
            lastChattedAt = LocalDateTime.now(),
            unreadCount = 3
        ),
        ChattingRoom(
            opponentMemberId = "member2",
            id = 2L,
            nickname = "사용자2",
            profileImageUrl = "https://example.com/profile.jpg",
            thumbnailImageUrl = "https://example.com/thumb.jpg",
            lastMessage = "운동하실래요?",
            lastChattedAt = LocalDateTime.now(),
            unreadCount = 0
        )
    )

    @Before
    fun setUp() {
        useCase = GetChattingRoomsUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 채팅방 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleChattingRooms)
        coEvery { mockRepository.getChattingRooms() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getChattingRooms() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 채팅방 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<ChattingRoom>())
        coEvery { mockRepository.getChattingRooms() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `채팅방 목록 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch chatting rooms")
        coEvery { mockRepository.getChattingRooms() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }
}
