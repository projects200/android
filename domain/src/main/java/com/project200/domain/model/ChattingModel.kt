package com.project200.domain.model

import java.time.LocalDateTime

data class ChattingRoom(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val lastMessage: String,
    val lastChattedAt: LocalDateTime,
    val unreadCount: Int
)