package com.project200.domain.usecase

import com.project200.domain.model.ChattingMessage
import com.project200.domain.repository.ChatSocketRepository
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

class ObserveSocketMessagesUseCase @Inject constructor(
    private val chatSocketRepository: ChatSocketRepository
) {
    operator fun invoke(): SharedFlow<ChattingMessage> {
        return chatSocketRepository.incomingMessages
    }
}