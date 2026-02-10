package com.project200.domain.model

import java.time.LocalDateTime

data class FeedListResult(
    val hasNext: Boolean,
    val feeds: List<Feed>,
)

data class Feed(
    val feedId: Long,
    val feedContent: String,
    val feedLikesCount: Int,
    val feedCommentsCount: Int,
    val feedTypeId: Long?,
    val feedTypeName: String?,
    val feedTypeDesc: String?,
    val feedCreatedAt: LocalDateTime,
    val feedIsLiked: Boolean,
    val feedHasCommented: Boolean,
    val memberId: String,
    val nickname: String,
    val profileUrl: String?,
    val thumbnailUrl: String?,
    val feedPictures: List<FeedPicture>,
)

data class FeedPicture(
    val feedPictureId: Long,
    val feedPictureUrl: String,
)

data class CreateFeedModel(
    val feedContent: String,
    val feedTypeId: Long?,
)

data class UpdateFeedModel(
    val feedId: Long,
    val feedContent: String,
    val feedTypeId: Long?,
)

data class FeedCreateResult(
    val feedId: Long,
)

data class Comment(
    val commentId: Long,
    val memberId: String,
    val memberNickname: String,
    val memberProfileImageUrl: String?,
    val memberThumbnailUrl: String?,
    val content: String,
    val likesCount: Int,
    val isLiked: Boolean,
    val createdAt: LocalDateTime,
    val children: List<Reply>,
)

data class Reply(
    val commentId: Long,
    val memberId: String,
    val memberNickname: String,
    val memberProfileImageUrl: String?,
    val memberThumbnailUrl: String?,
    val content: String,
    val likesCount: Int,
    val isLiked: Boolean,
    val createdAt: LocalDateTime,
)

data class CreateCommentResult(
    val commentId: Long,
)
