package com.project200.domain.repository

import com.project200.domain.model.ChattingMessage
import kotlinx.coroutines.flow.SharedFlow

interface ChatSocketRepository {
    val incomingMessages: SharedFlow<ChattingMessage>
    val socketErrors: SharedFlow<String>
    fun connect(chatRoomId: Long)
    fun disconnect()
    fun sendMessage(content: String)
}