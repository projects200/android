package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetChattingRoomsDTO(
    val chatroomId: Long,
    val otherMemberNickname: String,
    val otherMemberProfileImageUrl: String?,
    val otherMemberThumbnailImageUrl: String?,
    val lastChatContent: String,
    val lastChatSendedAt: LocalDateTime,
    val unreadCount: Int
)