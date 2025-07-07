package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.repository.PolicyRepository
import javax.inject.Inject

class GetExpectedScoreInfoUseCase @Inject constructor(
    private val policyRepository: PolicyRepository
) {
    suspend operator fun invoke(): BaseResult<ExpectedScoreInfo> {
        return policyRepository.getExpectedScoreInfo()
    }
}