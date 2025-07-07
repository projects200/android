package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ScorePolicy
import com.project200.domain.repository.MemberRepository
import com.project200.domain.repository.PolicyRepository
import javax.inject.Inject

class GetScorePolicyUseCase @Inject constructor(
   private val policyRepository: PolicyRepository
) {
    suspend operator fun invoke(): BaseResult<List<ScorePolicy>> {
        return policyRepository.getScorePolicy()
    }
}