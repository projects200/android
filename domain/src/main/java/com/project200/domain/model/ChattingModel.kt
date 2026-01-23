package com.project200.domain.model

import java.time.LocalDateTime

data class ChattingRoom(
    val opponentMemberId: String,
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val lastMessage: String?,
    val lastChattedAt: LocalDateTime?,
    val unreadCount: Int
)

data class ChattingMessage(
    val chatId: Long,
    val senderId: String?,
    val nickname: String?,
    val profileUrl: String?,
    val thumbnailImageUrl: String?,
    val content: String,
    val chatType: String,
    val sentAt: LocalDateTime,
    val isMine: Boolean = false,
    val showProfile: Boolean = false, // 프로필 표시 여부 (상대방 메시지용)
    val showTime: Boolean = false // 시간 표시 여부
)

data class ChattingModel(
    val hasNext: Boolean = false,
    val opponentActive: Boolean,
    val blockActive: Boolean,
    val messages: List<ChattingMessage>
)

enum class SocketType {
    PING, TALK, ERROR, PONG, SYSTEM_LEAVE, SYSTEM_BANNED
}

sealed class OpponentStatus {
    data object Left : OpponentStatus()
    data object Blocked : OpponentStatus()
}
