package com.project200.domain.repository

import com.project200.domain.model.ChatSocketConnection
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.OpponentStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatSocketRepository {
    val connection: StateFlow<ChatSocketConnection>
    val incomingMessages: SharedFlow<ChattingMessage>
    val socketErrors: SharedFlow<String>
    val opponentStatusChanges: SharedFlow<OpponentStatus>
    fun connect(chatRoomId: Long)
    fun disconnect()
    fun sendMessage(content: String)
}