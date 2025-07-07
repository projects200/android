package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.ScorePolicy

interface PolicyRepository {
    suspend fun getScorePolicy(): BaseResult<List<ScorePolicy>>
    suspend fun getExpectedScoreInfo(): BaseResult<ExpectedScoreInfo>
}