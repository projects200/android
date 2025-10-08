package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingRoom

interface ChattingRepository {
    suspend fun getChattingRooms(): BaseResult<List<ChattingRoom>>
}