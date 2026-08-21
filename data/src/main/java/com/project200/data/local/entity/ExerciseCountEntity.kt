package com.project200.data.local.entity

import androidx.room.Entity
import java.time.LocalDate

/**
 * 캘린더에 찍히는 날짜별 운동 횟수입니다.
 *
 * 로컬 기록 행을 세지 않고 서버 응답을 그대로 담습니다
 * 캘린더 API는 개수만 주고 기록 자체를 주지 않아서, 세는 방식이면
 * 캘린더만 열어본 달이 오프라인에서 빈 달로 보입니다
 */
@Entity(
    tableName = "exercise_count",
    primaryKeys = ["memberId", "date"],
)
data class ExerciseCountEntity(
    val memberId: String,
    val date: LocalDate,
    val count: Int,
)
