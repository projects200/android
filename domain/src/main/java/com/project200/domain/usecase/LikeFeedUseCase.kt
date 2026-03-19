package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class LikeFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(feedId: Long, liked: Boolean): BaseResult<Unit> {
        return feedRepository.likeFeed(feedId, liked)
    }
}
