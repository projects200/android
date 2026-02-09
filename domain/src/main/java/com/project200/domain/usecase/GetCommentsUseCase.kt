package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class GetCommentsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(feedId: Long): BaseResult<List<Comment>> {
        return feedRepository.getComments(feedId)
    }
}
