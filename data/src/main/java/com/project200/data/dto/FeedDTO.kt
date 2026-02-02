package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetFeedsDTO(
    val hasNext: Boolean,
    val feeds: List<FeedDTO>,
)

@JsonClass(generateAdapter = true)
data class FeedDTO(
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
    val feedPictures: List<FeedPictureDTO>,
)

@JsonClass(generateAdapter = true)
data class FeedPictureDTO(
    val feedPictureId: Long,
    val feedPictureUrl: String,
)

@JsonClass(generateAdapter = true)
data class CreateFeedRequestDTO(
    val feedContent: String,
    val feedTypeId: Long?,
)

@JsonClass(generateAdapter = true)
data class FeedCreateResultDTO(
    val feedId: Long,
)
