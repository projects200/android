package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class CreateFeedUseCase @Inject constructor(
    private val feedRepository: FeedRepository
) {
    suspend operator fun invoke(createFeedModel: CreateFeedModel): BaseResult<FeedCreateResult> {
        return feedRepository.createFeed(createFeedModel)
    }
}
