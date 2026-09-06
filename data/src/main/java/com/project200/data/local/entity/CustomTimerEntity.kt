package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 커스텀 타이머입니다. 기기가 원본입니다.
 *
 * 이름과 스텝 목록을 묶어 타이머 하나가 판정 단위입니다. 스텝만 따로 조회할 일이 없고
 * 서버 수정도 전체 교체라서 스텝을 JSON 컬럼에 담습니다
 *
 * 목록 정렬은 editedAt으로 합니다. 서버 응답 순서를 그대로 보존해야 하면
 * 서버 목록 반영을 붙이는 이슈에서 순서 컬럼을 추가합니다
 */
@Entity(
    tableName = "custom_timer",
    primaryKeys = ["memberId", "localId"],
    indices = [Index(value = ["memberId", "serverId"])],
)
data class CustomTimerEntity(
    val memberId: String,
    val localId: String,
    val serverId: Long?,
    val syncState: SyncState,
    val name: String,
    val steps: List<CachedTimerStep>,
    val createRequestId: String,
    val pendingEditId: String?,
    val editedAt: Long,
)
