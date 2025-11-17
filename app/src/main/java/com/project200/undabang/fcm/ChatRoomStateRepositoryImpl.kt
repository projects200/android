package com.project200.undabang.fcm

import com.project200.common.utils.ChatRoomStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRoomStateRepositoryImpl @Inject constructor() : ChatRoomStateRepository {

    private val _activeChatRoomId = MutableStateFlow<Long?>(null)
    override val activeChatRoomId = _activeChatRoomId.asStateFlow()

    override fun setActiveChatRoomId(roomId: Long?) {
        _activeChatRoomId.value = roomId
    }
}