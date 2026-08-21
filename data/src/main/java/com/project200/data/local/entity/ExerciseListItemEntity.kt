package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 하루 운동 기록 목록의 한 줄입니다.
 *
 * date는 목록을 받아올 때 서버에 넘긴 조회 날짜입니다. 응답에는 들어 있지 않지만
 * startedAt에서 파생시키면 자정을 넘긴 기록이 서버가 묶어준 날과 어긋나서 컬럼으로 둡니다
 *
 * sortOrder는 서버가 준 응답 순서입니다. SQL은 순서를 보장하지 않아서
 * 이걸로 정렬해야 목록이 서버와 같은 차례로 보입니다
 */
@Entity(
    tableName = "exercise_list_item",
    primaryKeys = ["memberId", "recordId"],
    indices = [Index(value = ["memberId", "date"])],
)
data class ExerciseListItemEntity(
    val memberId: String,
    val recordId: Long,
    val date: LocalDate,
    val sortOrder: Int,
    val title: String,
    val personalType: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val thumbnailUrls: List<String>?,
)
