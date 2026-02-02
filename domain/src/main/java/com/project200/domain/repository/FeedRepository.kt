package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.model.FeedListResult

interface FeedRepository {
    suspend fun getFeeds(prevFeedId: Long?): BaseResult<FeedListResult>

    suspend fun getFeedDetail(feedId: Long): BaseResult<Feed>

    suspend fun createFeed(createFeedModel: CreateFeedModel): BaseResult<FeedCreateResult>
}
