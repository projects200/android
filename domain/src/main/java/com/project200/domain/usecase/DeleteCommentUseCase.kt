package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class DeleteCommentUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(feedId: Long, commentId: Long): BaseResult<Unit> {
        return feedRepository.deleteComment(feedId, commentId)
    }
}
