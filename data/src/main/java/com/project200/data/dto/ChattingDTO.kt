package com.project200.data.dto

import com.project200.domain.model.SocketType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
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
    val chatTicket: String,
)

@JsonClass(generateAdapter = true)
data class SocketChatMessage(
    val succeed: Boolean,
    val type: SocketType,
    val message: String? = null,
    val data: Any? = null,
) {
    /**
     * TALK 타입일 때 안전하게 DTO로 변환하여 가져오는 도우미 함수
     */
    fun getChatData(moshi: Moshi): SocketChatMessageDTO? {
        if (type != SocketType.TALK || data == null) return null
        return try {
            val adapter = moshi.adapter(SocketChatMessageDTO::class.java)
            adapter.fromJsonValue(data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ERROR 타입일 때 data 필드에 담긴 에러 상세 내용을 가져오는 도우미 함수
     */
    fun getErrorDetail(): String? {
        return data as? String
    }
}

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
    val type: SocketType,
    val content: String?,
)
