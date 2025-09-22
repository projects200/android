package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.MatchingMember

interface MatchingRepository {
    suspend fun getMembers(): BaseResult<List<MatchingMember>>
}