package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.OpenUrl
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetOpenUrlUseCase @Inject constructor(
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(): BaseResult<OpenUrl> {
        return memberRepository.getOpenUrl()
    }
}