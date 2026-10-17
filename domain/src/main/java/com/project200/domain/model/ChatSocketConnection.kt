package com.project200.domain.model

data class ChatSocketConnection(
    val roomId: Long = -1L,
    val status: ChatSocketStatus = ChatSocketStatus.Disconnected,
)

enum class ChatSocketStatus {
    Connecting,
    Connected,
    Closing,
    Disconnected,
    Failed,
}
