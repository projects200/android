package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.UserProfile
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(): BaseResult<UserProfile> {
        return memberRepository.getUserProfile()
    }
}