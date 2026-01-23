package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingModel
import com.project200.domain.model.ChattingRoom

interface ChattingRepository {
    suspend fun createChatRoom(receiverId: String, locationId: Long, longitude: Double, latitude: Double): BaseResult<Long>
    suspend fun deleteChatRoom(chatRoomId: Long): BaseResult<Unit>
    suspend fun getChattingRooms(): BaseResult<List<ChattingRoom>>
    suspend fun getChattingMessages(chatRoomId: Long, prevChatId: Long?, size: Int): BaseResult<ChattingModel>
    suspend fun getNewChattingMessages(chatRoomId: Long, lastMessageId: Long?): BaseResult<ChattingModel>
    suspend fun sendChattingMessage(chatRoomId: Long, content: String): BaseResult<Long>
}