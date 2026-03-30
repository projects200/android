package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConnectChatRoomUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK(relaxed = true)
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: ConnectChatRoomUseCase

    @Before
    fun setUp() {
        useCase = ConnectChatRoomUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository connect 호출`() {
        // Given
        val chatRoomId = 1L

        // When
        useCase(chatRoomId)

        // Then
        verify(exactly = 1) { mockRepository.connect(chatRoomId) }
    }

    @Test
    fun `다른 채팅방으로 연결`() {
        // Given
        val chatRoomId = 999L

        // When
        useCase(chatRoomId)

        // Then
        verify(exactly = 1) { mockRepository.connect(chatRoomId) }
    }
}
