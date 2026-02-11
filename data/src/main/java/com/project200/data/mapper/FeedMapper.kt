package com.project200.data.mapper

import com.project200.data.dto.CreateFeedRequestDTO
import com.project200.data.dto.FeedCreateResultDTO
import com.project200.data.dto.FeedDTO
import com.project200.data.dto.FeedPictureDTO
import com.project200.data.dto.FeedPictureUploadDTO
import com.project200.data.dto.GetFeedsDTO
import com.project200.data.dto.UpdateFeedRequestDTO
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.model.FeedListResult
import com.project200.domain.model.FeedPicture
import com.project200.domain.model.UpdateFeedModel
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

fun CreateFeedModel.toDTO(): CreateFeedRequestDTO {
    return CreateFeedRequestDTO(
        feedContent = feedContent,
        feedTypeId = feedTypeId,
    )
}

fun FeedCreateResultDTO.toModel(): FeedCreateResult {
    return FeedCreateResult(
        feedId = feedId,
    )
}

fun UpdateFeedModel.toDTO(): UpdateFeedRequestDTO {
    return UpdateFeedRequestDTO(
        feedContent = feedContent,
        feedTypeId = feedTypeId,
    )
}

fun FeedPictureUploadDTO.toModel(): FeedPicture {
    return FeedPicture(
        feedPictureId = pictureId,
        feedPictureUrl = pictureUrl,
    )
}
