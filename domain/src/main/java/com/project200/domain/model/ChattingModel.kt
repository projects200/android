package com.project200.domain.model

data class ChattingRoom(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String,
    val thumbnailImageUrl: String,
    val lastMessage: String,
    val lastChattedAt: String,
    val unreadCount: Long
)