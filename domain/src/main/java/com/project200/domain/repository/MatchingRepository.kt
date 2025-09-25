package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember

interface MatchingRepository {
    suspend fun getMembers(): BaseResult<List<MatchingMember>>

    // 마지막 위치 저장
    suspend fun saveLastMapPosition(mapPosition: MapPosition)
    // 마지막 위치 불러오기
    suspend fun getLastMapPosition(): MapPosition?
}