package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom
import com.project200.domain.repository.ChattingRepository
import javax.inject.Inject

class GetChattingRoomsUseCase @Inject constructor(
    private val chattingRepository: ChattingRepository
) {
    suspend operator fun invoke(): BaseResult<List<ChattingRoom>> {
        return chattingRepository.getChattingRooms()
    }
}