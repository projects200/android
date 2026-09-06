package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 심플 타이머입니다. 기기가 원본입니다.
 *
 * 행을 식별하는 값은 localId입니다. 오프라인에서 만든 타이머는 서버 ID를 받기 전이라
 * serverId가 비어 있습니다
 *
 * 목록 정렬은 화면이 시간 오름차순과 내림차순을 토글하므로 여기서는 정하지 않습니다
 *
 * @property createRequestId 생성 요청 식별자. 재시도에도 같은 값을 보내 중복 생성을 막습니다
 * @property pendingEditId 수정과 삭제 요청 식별자. 저장할 때마다 새로 발급합니다
 * @property editedAt 기기가 저장 시점에 찍은 편집 시각(epoch millis). 충돌 판정 기준입니다
 */
@Entity(
    tableName = "simple_timer",
    primaryKeys = ["memberId", "localId"],
    indices = [Index(value = ["memberId", "serverId"])],
)
data class SimpleTimerEntity(
    val memberId: String,
    val localId: String,
    val serverId: Long?,
    val syncState: SyncState,
    val time: Int,
    val createRequestId: String,
    val pendingEditId: String?,
    val editedAt: Long,
)
