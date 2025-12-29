package com.project200.data.dto

import com.project200.domain.model.SocketType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class PostChatRoomResponse(
    val chatRoomId: Long,
)

@JsonClass(generateAdapter = true)
data class PostChatRoomRequest(
    val receiverId: String,
)

@JsonClass(generateAdapter = true)
data class GetChattingRoomsDTO(
    val otherMemberId: String,
    val chatRoomId: Long,
    val otherMemberNickname: String,
    val otherMemberProfileImageUrl: String?,
    val otherMemberThumbnailImageUrl: String?,
    val lastChatContent: String?,
    val lastChatReceivedAt: LocalDateTime?,
    val unreadCount: Int,
)

@JsonClass(generateAdapter = true)
data class GetChattingMessagesDTO(
    val content: List<ChatMessageDTO>,
    val hasNext: Boolean,
    val opponentActive: Boolean,
    val blockActive: Boolean,
)

@JsonClass(generateAdapter = true)
data class GetNewChattingMessagesDTO(
    val newChats: List<ChatMessageDTO>,
    val opponentActive: Boolean,
    val blockActive: Boolean,
)

@JsonClass(generateAdapter = true)
data class ChatMessageDTO(
    val chatId: Long,
    val senderId: String?,
    val senderNickname: String?,
    val senderProfileUrl: String?,
    val senderThumbnailUrl: String?,
    val chatContent: String,
    val chatType: String,
    val sentAt: LocalDateTime,
    val mine: Boolean,
)

@JsonClass(generateAdapter = true)
data class PostMessageResponse(
    val chatId: Long,
)

@JsonClass(generateAdapter = true)
data class PostChatMessageRequest(
    val content: String,
)

// 티켓 발급 응답
@JsonClass(generateAdapter = true)
data class TicketResponse(
    @Json(name = "chatTicket")
    val chatTicket: String
)

@JsonClass(generateAdapter = true)
data class SocketChatMessage(
    @Json(name = "webSocketType")
    val type: SocketType,
    val data: SocketChatMessageDTO? = null
)

@JsonClass(generateAdapter = true)
data class SocketChatMessageDTO(
    val chatId: Long,
    val senderId: String?,
    val senderNickname: String?,
    val senderProfileUrl: String?,
    val senderThumbnailUrl: String?,
    val chatContent: String,
    val chatType: String,
    val sentAt: LocalDateTime,
)

@JsonClass(generateAdapter = true)
data class SocketChatRequest(
    @Json(name = "webSocketType")
    val type: SocketType,
    val content: String?
)
