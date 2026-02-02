package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.FeedCreateResultDTO
import com.project200.data.dto.FeedDTO
import com.project200.data.dto.GetFeedsDTO
import com.project200.data.mapper.toDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.model.FeedListResult
import com.project200.domain.repository.FeedRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeedRepository {

    override suspend fun getFeeds(prevFeedId: Long?, size: Int?): BaseResult<FeedListResult> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getFeeds(prevFeedId, size) },
            mapper = { dto: GetFeedsDTO? ->
                dto?.toModel() ?: FeedListResult(hasNext = false, feeds = emptyList())
            },
        )
    }

    override suspend fun getFeedDetail(feedId: Long): BaseResult<Feed> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getFeedDetail(feedId) },
            mapper = { dto: FeedDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("피드 상세 데이터가 없습니다.")
            },
        )
    }

    override suspend fun createFeed(createFeedModel: CreateFeedModel): BaseResult<FeedCreateResult> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postFeed(createFeedModel.toDTO()) },
            mapper = { dto: FeedCreateResultDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("피드 생성 결과 데이터가 없습니다.")
            },
        )
    }
}
