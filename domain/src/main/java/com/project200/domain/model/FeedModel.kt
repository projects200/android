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
    val feedTypeId: Long,
    val feedTypeName: String,
    val feedTypeDesc: String,
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

data class FeedType(
    val feedTypeId: Long,
    val feedTypeName: String,
    val feedTypeDesc: String,
)
