package com.project200.data.mapper

import com.project200.data.dto.SocketChatMessageDTO
import com.project200.domain.model.ChattingMessage

fun SocketChatMessageDTO.toModel(): ChattingMessage {
    return ChattingMessage(
        chatId = this.chatId,
        senderId = this.senderId,
        nickname = this.senderNickname,
        profileUrl = this.senderProfileUrl,
        thumbnailImageUrl = this.senderThumbnailUrl,
        content = this.chatContent,
        chatType = this.chatType,
        sentAt = this.sentAt,
        isMine = false
    )
}