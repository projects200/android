package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetCommentsDTO(
    val comments: List<CommentDTO>,
)

@JsonClass(generateAdapter = true)
data class CommentDTO(
    val commentId: Long,
    val memberId: String,
    val memberNickname: String,
    val memberProfileImageUrl: String?,
    val memberThumbnailUrl: String?,
    val content: String,
    val likesCount: Int,
    val isLiked: Boolean = false,
    val createdAt: LocalDateTime,
    val children: List<ReplyDTO>,
)

@JsonClass(generateAdapter = true)
data class ReplyDTO(
    val commentId: Long,
    val memberId: String,
    val memberNickname: String,
    val memberProfileImageUrl: String?,
    val memberThumbnailUrl: String?,
    val content: String,
    val likesCount: Int,
    val isLiked: Boolean = false,
    val createdAt: LocalDateTime,
)

@JsonClass(generateAdapter = true)
data class CreateCommentRequestDTO(
    val content: String,
    val parentCommentId: Long?,
)

@JsonClass(generateAdapter = true)
data class CreateCommentResponseDTO(
    val commentId: Long,
)
