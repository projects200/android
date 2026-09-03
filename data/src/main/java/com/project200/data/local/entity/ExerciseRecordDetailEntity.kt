package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDateTime

/**
 * 운동 기록 상세입니다.
 *
 * 목록과 컬럼이 겹치지만 테이블을 나눕니다. 한 테이블에 담으면 목록 응답을 저장할 때
 * detail과 location이, 상세를 저장할 때 date와 thumbnailUrls가 서로 덮여 사라집니다
 *
 * 목록과 같은 기록이면 localId를 공유합니다. 상세 응답에는 기록 ID가 없어서
 * serverId는 조회할 때 넘긴 값으로 채웁니다
 */
@Entity(
    tableName = "exercise_record_detail",
    primaryKeys = ["memberId", "localId"],
    indices = [Index(value = ["memberId", "serverId"])],
)
data class ExerciseRecordDetailEntity(
    val memberId: String,
    val localId: String,
    val serverId: Long?,
    val syncState: SyncState,
    val title: String,
    val detail: String,
    val personalType: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val location: String,
    val pictures: List<CachedPicture>?,
)
