package com.project200.undabang.feature.feed.detail

import com.project200.domain.model.Comment
import com.project200.domain.model.Reply
import java.time.LocalDateTime

sealed class CommentItem {
    abstract val commentId: Long
    abstract val memberId: String
    abstract val memberNickname: String
    abstract val memberProfileImageUrl: String?
    abstract val memberThumbnailUrl: String?
    abstract val content: String
    abstract val likesCount: Int
    abstract val isLiked: Boolean
    abstract val createdAt: LocalDateTime

    data class CommentData(
        val comment: Comment,
    ) : CommentItem() {
        override val commentId: Long = comment.commentId
        override val memberId: String = comment.memberId
        override val memberNickname: String = comment.memberNickname
        override val memberProfileImageUrl: String? = comment.memberProfileImageUrl
        override val memberThumbnailUrl: String? = comment.memberThumbnailUrl
        override val content: String = comment.content
        override val likesCount: Int = comment.likesCount
        override val isLiked: Boolean = comment.isLiked
        override val createdAt: LocalDateTime = comment.createdAt
    }

    data class ReplyData(
        val reply: Reply,
        val parentCommentId: Long,
    ) : CommentItem() {
        override val commentId: Long = reply.commentId
        override val memberId: String = reply.memberId
        override val memberNickname: String = reply.memberNickname
        override val memberProfileImageUrl: String? = reply.memberProfileImageUrl
        override val memberThumbnailUrl: String? = reply.memberThumbnailUrl
        override val content: String = reply.content
        override val likesCount: Int = reply.likesCount
        override val isLiked: Boolean = reply.isLiked
        override val createdAt: LocalDateTime = reply.createdAt
    }
}

fun List<Comment>.toCommentItems(): List<CommentItem> {
    val result = mutableListOf<CommentItem>()
    for (comment in this) {
        result.add(CommentItem.CommentData(comment))
        for (reply in comment.children) {
            result.add(CommentItem.ReplyData(reply, comment.commentId))
        }
    }
    return result
}
