package com.project200.domain.model

import java.time.LocalDateTime

data class ChattingRoom(
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
    val isMine: Boolean,
    val showProfile: Boolean = false, // 프로필 표시 여부 (상대방 메시지용)
    val showTime: Boolean = false // 시간 표시 여부
)

data class ChattingModel(
    val hasNext: Boolean = false,
    val opponentActive: Boolean,
    val opponentBlocked: Boolean? = null,
    val messages: List<ChattingMessage>
)