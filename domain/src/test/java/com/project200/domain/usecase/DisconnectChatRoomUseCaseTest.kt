package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DisconnectChatRoomUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK(relaxed = true)
    private lateinit var mockRepository: ChatSocketRepository

    private lateinit var useCase: DisconnectChatRoomUseCase

    @Before
    fun setUp() {
        useCase = DisconnectChatRoomUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository disconnect 호출`() {
        // When
        useCase()

        // Then
        verify(exactly = 1) { mockRepository.disconnect() }
    }
}
