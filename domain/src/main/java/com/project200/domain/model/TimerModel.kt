package com.project200.domain.model

/**
 * 커스텀 타이머입니다.
 *
 * 기기가 원본이라 행을 식별하는 값은 localId입니다. serverId는 아직 서버에 올리지 못한
 * 타이머에서 비어 있습니다
 *
 * @property isSyncPending 서버에 아직 반영되지 않은 변경이 있는지. 화면이 동기화 대기를 표시합니다
 */
data class CustomTimer(
    val localId: String,
    val serverId: Long? = null,
    val name: String,
    val steps: List<Step> = emptyList(),
    val isSyncPending: Boolean = false,
)

data class SimpleTimer(
    val localId: String,
    val serverId: Long? = null,
    val time: Int,
    val isSyncPending: Boolean = false,
)

/**
 * 커스텀 타이머의 스텝입니다.
 *
 * 서버 스텝 ID를 담지 않습니다. 서버 수정이 전체 교체라 스텝 ID가 왕복에서 유지된다는
 * 보장이 없습니다. 화면 안에서 항목을 구분하는 값은 order입니다
 */
data class Step(
    val order: Int,
    val time: Int,
    val name: String,
)
