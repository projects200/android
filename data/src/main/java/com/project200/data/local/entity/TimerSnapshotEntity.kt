package com.project200.data.local.entity

import androidx.room.Entity

/** 빈 목록도 조회한 스냅샷으로 보관하며, 수정 이후 도착한 이전 응답을 구분합니다 */
@Entity(tableName = "timer_snapshot", primaryKeys = ["memberId", "timerType"])
data class TimerSnapshotEntity(
    val memberId: String,
    val timerType: String,
    val hasSnapshot: Boolean,
    val revision: Long,
) {
    companion object {
        const val SIMPLE = "simple"
        const val CUSTOM = "custom"
    }
}
