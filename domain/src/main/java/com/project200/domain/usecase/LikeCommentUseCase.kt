package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

class LikeCommentUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(commentId: Long, liked: Boolean): BaseResult<Unit> {
        return feedRepository.likeComment(commentId, liked)
    }
}
