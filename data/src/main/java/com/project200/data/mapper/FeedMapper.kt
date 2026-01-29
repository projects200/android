package com.project200.data.mapper

import com.project200.data.dto.FeedDTO
import com.project200.data.dto.FeedPictureDTO
import com.project200.data.dto.GetFeedsDTO
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
import com.project200.domain.model.FeedPicture
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun GetFeedsDTO.toModel(): FeedListResult {
    return FeedListResult(
        hasNext = hasNext,
        feeds = feeds.map { it.toModel() },
    )
}

fun FeedDTO.toModel(): Feed {
    return Feed(
        feedId = feedId,
        feedContent = feedContent,
        feedLikesCount = feedLikesCount,
        feedCommentsCount = feedCommentsCount,
        feedTypeId = feedTypeId,
        feedTypeName = feedTypeName,
        feedTypeDesc = feedTypeDesc,
        feedCreatedAt = feedCreatedAt,
        feedIsLiked = feedIsLiked,
        feedHasCommented = feedHasCommented,
        memberId = memberId,
        nickname = nickname,
        profileUrl = profileUrl,
        thumbnailUrl = thumbnailUrl,
        feedPictures = feedPictures.map { it.toModel() },
    )
}

fun FeedPictureDTO.toModel(): FeedPicture {
    return FeedPicture(
        feedPictureId = feedPictureId,
        feedPictureUrl = feedPictureUrl,
    )
}