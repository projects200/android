package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step
import com.project200.domain.model.TimerSyncResult

/**
 * 타이머는 기기가 원본입니다. 쓰기는 로컬에 먼저 반영되고 전송은 워커가 맡습니다.
 *
 * 읽기는 로컬 스냅샷 우선입니다. 미조회 목록과 명시적 refresh만 서버를 기다립니다
 * 화면은 캐시를 먼저 표시한 뒤 별도 갱신하며 지속 구독을 두지 않습니다
 *
 * 인자로 받는 식별자는 모두 localId입니다
 */
interface TimerRepository {
    /** refresh=true만 서버 갱신을 기다립니다. 전송 대기 행은 서버 값으로 덮지 않습니다 */
    suspend fun getSimpleTimers(refresh: Boolean = false): BaseResult<List<SimpleTimer>>

    /** 화면 복귀나 내 쓰기 직후에 씁니다. 서버를 보지 않습니다 */
    suspend fun getLocalSimpleTimers(): BaseResult<List<SimpleTimer>>

    suspend fun addSimpleTimer(time: Int): BaseResult<String>

    suspend fun editSimpleTimer(
        localId: String,
        time: Int,
    ): BaseResult<Unit>

    suspend fun deleteSimpleTimer(localId: String): BaseResult<Unit>

    suspend fun getCustomTimerList(refresh: Boolean = false): BaseResult<List<CustomTimer>>

    suspend fun getLocalCustomTimerList(): BaseResult<List<CustomTimer>>

    /** 스텝까지 로컬에 있습니다. 서버 상세를 다시 받지 않습니다 */
    suspend fun getCustomTimer(localId: String): BaseResult<CustomTimer>

    suspend fun createCustomTimer(
        title: String,
        steps: List<Step>,
    ): BaseResult<String>

    suspend fun editCustomTimer(
        localId: String,
        title: String,
        steps: List<Step>,
    ): BaseResult<Unit>

    suspend fun deleteCustomTimer(localId: String): BaseResult<Unit>
    suspend fun syncPendingTimers(memberId: String): TimerSyncResult
}
