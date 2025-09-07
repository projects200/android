package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score
import com.project200.domain.model.UserProfile

interface MemberRepository {
    suspend fun getScore(): BaseResult<Score>
    suspend fun getUserProfile(): BaseResult<UserProfile>
}