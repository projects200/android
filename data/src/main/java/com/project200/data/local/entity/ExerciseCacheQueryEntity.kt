package com.project200.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

/** 행이 없는 성공 응답과 미조회 날짜를 구분합니다. 계정 정리와 날짜별 보존 정책을 함께 적용합니다. */
@Entity(tableName = "exercise_cache_query", primaryKeys = ["memberId", "queryType", "date"])
data class ExerciseCacheQueryEntity(
    val memberId: String,
    val queryType: String,
    val date: LocalDate,
)
