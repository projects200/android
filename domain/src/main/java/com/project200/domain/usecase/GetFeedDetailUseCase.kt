package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class GetFeedDetailUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(feedId: Long): BaseResult<Feed> {
        return feedRepository.getFeedDetail(feedId)
    }
}
