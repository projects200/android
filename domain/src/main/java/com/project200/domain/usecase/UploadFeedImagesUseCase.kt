package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.FeedPicture
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class UploadFeedImagesUseCase @Inject constructor(
    private val feedRepository: FeedRepository
) {
    suspend operator fun invoke(feedId: Long, imageUris: List<String>): BaseResult<List<FeedPicture>> {
        return feedRepository.uploadFeedImages(feedId, imageUris)
    }
}
