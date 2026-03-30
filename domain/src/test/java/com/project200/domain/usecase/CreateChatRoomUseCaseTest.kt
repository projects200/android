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
class CreateChatRoomUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: ChattingRepository

    private lateinit var useCase: CreateChatRoomUseCase

    @Before
    fun setUp() {
        useCase = CreateChatRoomUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 채팅방 생성 성공`() = runTest {
        // Given
        val receiverId = "member123"
        val locationId = 1L
        val longitude = 127.0
        val latitude = 37.5
        val newChatRoomId = 100L
        val successResult = BaseResult.Success(newChatRoomId)
        coEvery { mockRepository.createChatRoom(receiverId, locationId, longitude, latitude) } returns successResult

        // When
        val result = useCase(receiverId, locationId, longitude, latitude)

        // Then
        coVerify(exactly = 1) { mockRepository.createChatRoom(receiverId, locationId, longitude, latitude) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).isEqualTo(newChatRoomId)
    }

    @Test
    fun `invoke 호출 시 채팅방 생성 실패`() = runTest {
        // Given
        val receiverId = "member123"
        val locationId = 1L
        val longitude = 127.0
        val latitude = 37.5
        val errorResult = BaseResult.Error("ERR", "Failed to create chat room")
        coEvery { mockRepository.createChatRoom(receiverId, locationId, longitude, latitude) } returns errorResult

        // When
        val result = useCase(receiverId, locationId, longitude, latitude)

        // Then
        coVerify(exactly = 1) { mockRepository.createChatRoom(receiverId, locationId, longitude, latitude) }
        assertThat(result).isEqualTo(errorResult)
    }
}
