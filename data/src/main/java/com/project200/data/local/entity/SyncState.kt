package com.project200.data.local.entity

/**
 * 행 하나의 전송 상태입니다.
 *
 * 연산을 쌓지 않고 행마다 현재 상태만 둡니다. 오프라인에서 여러 번 고쳐도
 * 전송은 마지막 상태 한 번입니다
 */
enum class SyncState {
    SYNCED,
    CREATE_PENDING,
    UPDATE_PENDING,
    DELETE_PENDING,
}
