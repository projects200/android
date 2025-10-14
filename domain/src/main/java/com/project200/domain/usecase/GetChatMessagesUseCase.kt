package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingModel
import com.project200.domain.repository.ChattingRepository
import javax.inject.Inject

class GetChatMessagesUseCase @Inject constructor(
    private val chattingRepository: ChattingRepository
) {
    suspend operator fun invoke(chatRoomId: Long, prevChatId: Long? = null, size: Int): BaseResult<ChattingModel> {
        return chattingRepository.getChattingMessages(chatRoomId, prevChatId, size)
    }
}