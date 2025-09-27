package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.KakaoPlaceInfo
import com.project200.domain.model.MapPosition
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.MatchingMemberProfile
import java.time.LocalDate

interface MatchingRepository {
    suspend fun getMembers(): BaseResult<List<MatchingMember>>

    // 마지막 위치 저장
    suspend fun saveLastMapPosition(mapPosition: MapPosition)
    // 마지막 위치 불러오기
    suspend fun getLastMapPosition(): MapPosition?

    // 매칭 멤버 프로필 및 운동 기록
    suspend fun getMatchingProfile(memberId: String): BaseResult<MatchingMemberProfile>
    suspend fun getMemberExerciseDates(memberId: String, startDate: LocalDate, endDate: LocalDate): BaseResult<List<ExerciseCount>>

    // 운동 장소 리스트
    suspend fun getExercisePlaces(): BaseResult<List<ExercisePlace>>
    // 운동 장소 삭제
    suspend fun deleteExercisePlace(placeId: Long): BaseResult<Unit>
    // 운동 장소 등록
    suspend fun addExercisePlace(placeInfo: ExercisePlace): BaseResult<Unit>
    // 운동 장소 수정
    suspend fun editExercisePlace(placeInfo: ExercisePlace): BaseResult<Unit>
}