package com.project200.domain.usecase

import com.project200.domain.repository.ChatSocketRepository
import javax.inject.Inject

class ConnectChatRoomUseCase @Inject constructor(
    private val chatSocketRepository: ChatSocketRepository
) {
    operator fun invoke(chatRoomId: Long) {
        chatSocketRepository.connect(chatRoomId)
    }
}