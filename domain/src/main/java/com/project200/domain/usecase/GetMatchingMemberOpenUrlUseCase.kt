package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MatchingRepository
import com.project200.domain.repository.MemberRepository
import javax.inject.Inject

class GetMatchingMemberOpenUrlUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
) {
    suspend operator fun invoke(memberId: String): BaseResult<String> {
        return matchingRepository.getMemberOpenUrl(memberId = memberId)
    }
}