package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.FeedDTO
import com.project200.data.dto.GetFeedsDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
import com.project200.domain.repository.FeedRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class FeedRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeedRepository {

    override suspend fun getFeeds(prevFeedId: Long?): BaseResult<FeedListResult> {
        val dummyFeeds = listOf(
            Feed(
                feedId = 1L,
                feedContent = "오늘 가슴 운동 빡세게 했네요! 다들 득근합시다. #오운완 #헬스타그램",
                feedLikesCount = 12,
                feedCommentsCount = 3,
                feedTypeId = 1L,
                feedTypeName = "오운완",
                feedTypeDesc = "오늘 운동 완료",
                feedCreatedAt = java.time.LocalDateTime.now().minusHours(2),
                feedIsLiked = false,
                feedHasCommented = false,
                memberId = "user1",
                nickname = "운동왕김근육",
                profileUrl = null,
                thumbnailUrl = null,
                feedPictures = listOf(
                    com.project200.domain.model.FeedPicture(1L, "https://picsum.photos/id/10/800/400"), // 가로로 긴 사진 (2:1)
                    com.project200.domain.model.FeedPicture(2L, "https://picsum.photos/id/20/400/800")  // 세로로 긴 사진 (1:2)
                )
            ),
            Feed(
                feedId = 2L,
                feedContent = "새로 산 스트랩 써봤는데 지지력 최고네요. 강추합니다!",
                feedLikesCount = 8,
                feedCommentsCount = 5,
                feedTypeId = 2L,
                feedTypeName = "장비자랑",
                feedTypeDesc = "장비 리뷰 및 자랑",
                feedCreatedAt = java.time.LocalDateTime.now().minusHours(5),
                feedIsLiked = true,
                feedHasCommented = false,
                memberId = "user2",
                nickname = "장비빨최고",
                profileUrl = null,
                thumbnailUrl = null,
                feedPictures = listOf(
                    com.project200.domain.model.FeedPicture(4L, "https://picsum.photos/id/30/1000/300") // 극단적으로 가로로 긴 사진
                )
            ),
            Feed(
                feedId = 3L,
                feedContent = "주말 등산 다녀왔습니다. 공기가 너무 좋네요~",
                feedLikesCount = 25,
                feedCommentsCount = 10,
                feedTypeId = 3L,
                feedTypeName = "일상",
                feedTypeDesc = "운동 외 일상",
                feedCreatedAt = java.time.LocalDateTime.now().minusDays(1),
                feedIsLiked = false,
                feedHasCommented = true,
                memberId = "user3",
                nickname = "산타는사람",
                profileUrl = null,
                thumbnailUrl = null,
                feedPictures = listOf(
                    com.project200.domain.model.FeedPicture(3L, "https://picsum.photos/id/40/300/1000") // 극단적으로 세로로 긴 사진
                )
            )
        )
        return BaseResult.Success(FeedListResult(hasNext = false, feeds = dummyFeeds))

        /* 
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getFeeds(prevFeedId) },
            mapper = { dto: GetFeedsDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("피드 목록 데이터가 없습니다.")
            },
        )
        */
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
}
