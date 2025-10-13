package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.repository.MatchingRepository
import javax.inject.Inject

class GetMatchingProfileUseCase @Inject constructor(
    private val matchingRepository: MatchingRepository
){
    suspend operator fun invoke(memberId: String): BaseResult<MatchingMemberProfile> {
        return matchingRepository.getMatchingProfile(memberId)
    }
}