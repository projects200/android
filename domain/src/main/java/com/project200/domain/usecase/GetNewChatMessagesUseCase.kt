package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingModel
import com.project200.domain.repository.ChattingRepository
import javax.inject.Inject

class GetNewChatMessagesUseCase @Inject constructor(
    private val chattingRepository: ChattingRepository
) {
    suspend operator fun invoke(chatRoomId: Long, lastChatId: Long? = null): BaseResult<ChattingModel> {
        return chattingRepository.getNewChattingMessages(chatRoomId, lastChatId)
    }
}