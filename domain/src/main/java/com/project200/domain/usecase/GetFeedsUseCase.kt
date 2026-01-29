package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.FeedListResult
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class GetFeedsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(prevFeedId: Long? = null): BaseResult<FeedListResult> {
        return feedRepository.getFeeds(prevFeedId)
    }
}
