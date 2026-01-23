package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ChattingRepository
import javax.inject.Inject

class CreateChatRoomUseCase @Inject constructor(
    private val chattingRepository: ChattingRepository
){
    suspend operator fun invoke(receiverId: String, locationId: Long, longitude: Double, latitude: Double): BaseResult<Long> {
        return chattingRepository.createChatRoom(receiverId, locationId, longitude, latitude)
    }
}