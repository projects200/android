package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class ChangeThumbnailUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(pictureId: Long): BaseResult<Unit> {
        return memberRepository.changeThumbnail(pictureId = pictureId)
    }
}