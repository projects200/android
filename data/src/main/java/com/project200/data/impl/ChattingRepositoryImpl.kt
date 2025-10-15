package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetChattingMessagesDTO
import com.project200.data.dto.GetChattingRoomsDTO
import com.project200.data.dto.GetNewChattingMessagesDTO
import com.project200.data.dto.PostChatMessageRequest
import com.project200.data.dto.PostChatRoomRequest
import com.project200.data.dto.PostChatRoomResponse
import com.project200.data.dto.PostMessageResponse
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ChattingModel
import com.project200.domain.model.ChattingRoom
import com.project200.domain.repository.ChattingRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class ChattingRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ChattingRepository {
        // 채팅방 생성
        override suspend fun createChatRoom(receiverId: String): BaseResult<Long> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postChatRoom(PostChatRoomRequest(receiverId)) },
                mapper = { dto: PostChatRoomResponse? ->
                    dto?.chatRoomId ?: throw NoSuchElementException()
                },
            )
        }

        // 채팅방 나가기
        override suspend fun deleteChatRoom(chatRoomId: Long): BaseResult<Unit> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.deleteChatRoom(chatRoomId) },
                mapper = { Unit },
            )
        }

        // 채팅방 목록 조회
        override suspend fun getChattingRooms(): BaseResult<List<ChattingRoom>> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getChattingRooms() },
                mapper = { dtoList: List<GetChattingRoomsDTO>? ->
                    dtoList?.map { it.toModel() } ?: throw NoSuchElementException()
                },
            )
        }

        // 채팅 메세지 목록 조회
        override suspend fun getChattingMessages(
            chatRoomId: Long,
            prevChatId: Long?,
            size: Int,
        ): BaseResult<ChattingModel> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getChatMessages(chatRoomId, prevChatId, size) },
                mapper = { dto: GetChattingMessagesDTO? ->
                    dto?.toModel() ?: throw NoSuchElementException()
                },
            )
        }

        // 새 메세지 조회
        override suspend fun getNewChattingMessages(
            chatRoomId: Long,
            lastMessageId: Long?,
        ): BaseResult<ChattingModel> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getNewChatMessages(chatRoomId, lastMessageId) },
                mapper = { dto: GetNewChattingMessagesDTO? ->
                    dto?.toModel() ?: throw NoSuchElementException()
                },
            )
        }

        // 메세지 전송
        override suspend fun sendChattingMessage(
            chatRoomId: Long,
            content: String,
        ): BaseResult<Long> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postChatMessage(chatRoomId, PostChatMessageRequest(content)) },
                mapper = { dto: PostMessageResponse? ->
                    dto?.chatId ?: throw NoSuchElementException()
                },
            )
        }
    }
