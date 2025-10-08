package com.project200.data.mapper

import com.project200.data.dto.GetChattingRoomsDTO
import com.project200.domain.model.ChattingRoom

fun GetChattingRoomsDTO.toModel(): ChattingRoom {
    return ChattingRoom(
        id = this.chatroomId,
        nickname = this.otherMemberNickname,
        profileImageUrl = this.otherMemberProfileImageUrl,
        thumbnailImageUrl = this.otherMemberThumbnailImageUrl,
        lastMessage = this.lastChatContent,
        lastChattedAt = this.lastChatSendedAt,
        unreadCount = this.unreadCount
    )
}