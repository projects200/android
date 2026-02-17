package com.project200.data.mapper

import com.project200.data.dto.CommentDTO
import com.project200.data.dto.CreateCommentResponseDTO
import com.project200.data.dto.ReplyDTO
import com.project200.data.dto.TaggedMemberDTO
import com.project200.domain.model.Comment
import com.project200.domain.model.CreateCommentResult
import com.project200.domain.model.Reply
import com.project200.domain.model.TaggedMember

fun CommentDTO.toModel(): Comment {
    return Comment(
        commentId = commentId,
        memberId = memberId,
        memberNickname = memberNickname,
        memberProfileImageUrl = memberProfileImageUrl,
        memberThumbnailUrl = memberThumbnailUrl,
        content = content,
        likesCount = likesCount,
        isLiked = isLiked,
        createdAt = createdAt,
        children = children.map { it.toModel() },
    )
}

fun ReplyDTO.toModel(): Reply {
    return Reply(
        commentId = commentId,
        memberId = memberId,
        memberNickname = memberNickname,
        memberProfileImageUrl = memberProfileImageUrl,
        memberThumbnailUrl = memberThumbnailUrl,
        content = content,
        likesCount = likesCount,
        isLiked = isLiked,
        createdAt = createdAt,
        taggedMember = taggedMember?.toModel(),
    )
}

fun TaggedMemberDTO.toModel(): TaggedMember {
    return TaggedMember(
        memberId = memberId,
        memberNickname = memberNickname,
    )
}

fun CreateCommentResponseDTO.toModel(): CreateCommentResult {
    return CreateCommentResult(
        commentId = commentId,
    )
}
