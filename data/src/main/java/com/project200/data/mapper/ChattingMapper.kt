package com.project200.data.mapper

import com.project200.data.dto.ChatMessageDTO
import com.project200.data.dto.GetChattingMessagesDTO
import com.project200.data.dto.GetChattingRoomsDTO
import com.project200.data.dto.GetNewChattingMessagesDTO
import com.project200.domain.model.ChattingMessage
import com.project200.domain.model.ChattingModel
import com.project200.domain.model.ChattingRoom

fun GetChattingRoomsDTO.toModel(): ChattingRoom {
    return ChattingRoom(
        opponentMemberId = this.otherMemberId,
        id = this.chatRoomId,
        nickname = this.otherMemberNickname,
        profileImageUrl = this.otherMemberProfileImageUrl,
        thumbnailImageUrl = this.otherMemberThumbnailImageUrl,
        lastMessage = this.lastChatContent,
        lastChattedAt = this.lastChatReceivedAt,
        unreadCount = this.unreadCount,
    )
}

fun ChatMessageDTO.toModel(): ChattingMessage {
    return ChattingMessage(
        chatId = this.chatId,
        senderId = this.senderId,
        nickname = this.senderNickname,
        profileUrl = this.senderProfileUrl,
        thumbnailImageUrl = this.senderThumbnailUrl,
        content = this.chatContent,
        chatType = this.chatType,
        sentAt = this.sentAt,
        isMine = this.mine,
    )
}

fun GetChattingMessagesDTO.toModel(): ChattingModel {
    return ChattingModel(
        hasNext = this.hasNext,
        opponentActive = this.opponentActive,
        opponentBlocked = this.opponentBlocked,
        messages = this.content.map { it.toModel() },
    )
}

fun GetNewChattingMessagesDTO.toModel(): ChattingModel {
    return ChattingModel(
        hasNext = false,
        opponentActive = this.opponentActive,
        messages = this.newChats.map { it.toModel() },
    )
}
