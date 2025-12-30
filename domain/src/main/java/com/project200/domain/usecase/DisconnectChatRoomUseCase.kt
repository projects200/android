package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import javax.inject.Inject

class DisconnectChatRoomUseCase @Inject constructor(
    private val chatSocketRepository: ChatSocketRepository
) {
    operator fun invoke() {
        chatSocketRepository.disconnect()
    }
}