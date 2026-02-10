package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.model.CreateCommentResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.model.FeedListResult

interface FeedRepository {
    suspend fun getFeeds(prevFeedId: Long?, size: Int? = null): BaseResult<FeedListResult>

    suspend fun getFeedDetail(feedId: Long): BaseResult<Feed>

    suspend fun createFeed(createFeedModel: CreateFeedModel): BaseResult<FeedCreateResult>

    suspend fun deleteFeed(feedId: Long): BaseResult<Unit>

    suspend fun getComments(feedId: Long): BaseResult<List<Comment>>

    suspend fun createComment(feedId: Long, content: String, parentCommentId: Long?): BaseResult<CreateCommentResult>

    suspend fun likeComment(feedId: Long, commentId: Long): BaseResult<Unit>

    suspend fun unlikeComment(feedId: Long, commentId: Long): BaseResult<Unit>

    suspend fun deleteComment(commentId: Long): BaseResult<Unit>

    suspend fun likeFeed(feedId: Long): BaseResult<Unit>

    suspend fun unlikeFeed(feedId: Long): BaseResult<Unit>
}
