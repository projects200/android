package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score

interface MemberRepository {
    suspend fun getScore(): BaseResult<Score>
}