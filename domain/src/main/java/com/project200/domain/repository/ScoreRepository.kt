package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo

interface ScoreRepository {
    suspend fun getExpectedScoreInfo(): BaseResult<ExpectedScoreInfo>
}