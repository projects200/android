package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.PolicyGroup

interface PolicyRepository {
    suspend fun getPolicyGroup(groupName: String): BaseResult<PolicyGroup>
    suspend fun getExpectedScoreInfo(): BaseResult<ExpectedScoreInfo>
}