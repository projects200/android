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
 *
 * @property inFlightEditId 전송을 시작한 시점의 pendingEditId 고정값. 재시도에도 행의
 * 현재 값을 다시 읽지 않고 이 고정값을 그대로 보내서 생성이 둘 되거나 오래된 수정이
 * 늦게 도달하는 일을 막습니다
 * @property inFlightEditedAt 전송을 시작한 시점의 editedAt 고정값
 * @property inFlightOperation 전송을 시작한 시점의 syncState 고정값. 전송 중에 사용자가
 * 행을 수정하거나 삭제해서 syncState가 바뀌어도 보내는 연산은 이 값 그대로입니다
 * @property lastSyncError 가장 최근 전송 실패 사유. 재시도가 성공하면 지웁니다
 * @property inFlightName 전송을 시작한 시점의 name 고정값
 * @property inFlightSteps 전송을 시작한 시점의 steps 고정값
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
    val inFlightEditId: String?,
    val inFlightEditedAt: Long?,
    val inFlightOperation: SyncState?,
    val lastSyncError: String?,
    val inFlightName: String?,
    val inFlightSteps: List<CachedTimerStep>?,
)
