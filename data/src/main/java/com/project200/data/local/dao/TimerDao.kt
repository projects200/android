package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState

@Dao
interface TimerDao {
    /** 삭제 대기 행은 화면에서 이미 사라진 타이머라 조회에서 뺍니다 */
    @Query(
        "SELECT * FROM simple_timer " +
            "WHERE memberId = :memberId AND syncState != :excluded " +
            "ORDER BY time",
    )
    suspend fun getSimpleTimers(
        memberId: String,
        excluded: SyncState = SyncState.DELETE_PENDING,
    ): List<SimpleTimerEntity>

    /** 최대 6개 제한을 로컬에서 판정할 때 씁니다 */
    @Query(
        "SELECT COUNT(*) FROM simple_timer " +
            "WHERE memberId = :memberId AND syncState != :excluded",
    )
    suspend fun countSimpleTimers(
        memberId: String,
        excluded: SyncState = SyncState.DELETE_PENDING,
    ): Int

    @Query("SELECT * FROM simple_timer WHERE memberId = :memberId AND localId = :localId")
    suspend fun getSimpleTimer(
        memberId: String,
        localId: String,
    ): SimpleTimerEntity?

    @Upsert
    suspend fun upsertSimpleTimer(timer: SimpleTimerEntity)

    @Query("DELETE FROM simple_timer WHERE memberId = :memberId AND localId = :localId")
    suspend fun deleteSimpleTimer(
        memberId: String,
        localId: String,
    )

    @Query(
        "SELECT * FROM custom_timer " +
            "WHERE memberId = :memberId AND syncState != :excluded " +
            "ORDER BY editedAt",
    )
    suspend fun getCustomTimers(
        memberId: String,
        excluded: SyncState = SyncState.DELETE_PENDING,
    ): List<CustomTimerEntity>

    @Query("SELECT * FROM custom_timer WHERE memberId = :memberId AND localId = :localId")
    suspend fun getCustomTimer(
        memberId: String,
        localId: String,
    ): CustomTimerEntity?

    @Upsert
    suspend fun upsertCustomTimer(timer: CustomTimerEntity)

    @Query("DELETE FROM custom_timer WHERE memberId = :memberId AND localId = :localId")
    suspend fun deleteCustomTimer(
        memberId: String,
        localId: String,
    )

    /** 대기 행이 걸린 서버ID입니다. 서버 목록 반영에서 건드리면 안 됩니다 */
    @Query(
        "SELECT serverId FROM simple_timer " +
            "WHERE memberId = :memberId AND serverId IS NOT NULL AND syncState != :synced",
    )
    suspend fun getPendingSimpleTimerServerIds(
        memberId: String,
        synced: SyncState = SyncState.SYNCED,
    ): List<Long>

    /** 서버 목록에 있는 항목 중 이미 캐시된 것의 localId를 이어 쓰는 데 씁니다 */
    @Query(
        "SELECT serverId, localId FROM simple_timer " +
            "WHERE memberId = :memberId AND serverId IN (:serverIds) AND syncState = :synced",
    )
    suspend fun getSyncedSimpleTimersByServerIds(
        memberId: String,
        serverIds: List<Long>,
        synced: SyncState = SyncState.SYNCED,
    ): List<ServerIdLocalId>

    @Upsert
    suspend fun upsertSimpleTimers(timers: List<SimpleTimerEntity>)

    /** 서버 목록에서 사라진 동기화 완료 행을 지웁니다. 대기 행은 건드리지 않습니다 */
    @Query(
        "DELETE FROM simple_timer " +
            "WHERE memberId = :memberId AND syncState = :synced AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedSimpleTimersNotIn(
        memberId: String,
        serverIds: List<Long>,
        synced: SyncState = SyncState.SYNCED,
    )

    /** 전송할 대기 행입니다. 워커가 씁니다 */
    @Query(
        "SELECT * FROM simple_timer " +
            "WHERE memberId = :memberId AND syncState != :synced " +
            "ORDER BY editedAt",
    )
    suspend fun getPendingSimpleTimers(
        memberId: String,
        synced: SyncState = SyncState.SYNCED,
    ): List<SimpleTimerEntity>

    @Query(
        "SELECT * FROM custom_timer " +
            "WHERE memberId = :memberId AND syncState != :synced " +
            "ORDER BY editedAt",
    )
    suspend fun getPendingCustomTimers(
        memberId: String,
        synced: SyncState = SyncState.SYNCED,
    ): List<CustomTimerEntity>

    /** 대기 행이 걸린 서버ID입니다. 서버 목록 반영에서 건드리면 안 됩니다 */
    @Query(
        "SELECT serverId FROM custom_timer " +
            "WHERE memberId = :memberId AND serverId IS NOT NULL AND syncState != :synced",
    )
    suspend fun getPendingCustomTimerServerIds(
        memberId: String,
        synced: SyncState = SyncState.SYNCED,
    ): List<Long>

    /** 서버 목록에 있는 항목 중 이미 캐시된 것의 localId를 이어 쓰는 데 씁니다 */
    @Query(
        "SELECT serverId, localId FROM custom_timer " +
            "WHERE memberId = :memberId AND serverId IN (:serverIds) AND syncState = :synced",
    )
    suspend fun getSyncedCustomTimersByServerIds(
        memberId: String,
        serverIds: List<Long>,
        synced: SyncState = SyncState.SYNCED,
    ): List<ServerIdLocalId>

    @Upsert
    suspend fun upsertCustomTimers(timers: List<CustomTimerEntity>)

    /** 서버 목록에서 사라진 동기화 완료 행을 지웁니다. 대기 행은 건드리지 않습니다 */
    @Query(
        "DELETE FROM custom_timer " +
            "WHERE memberId = :memberId AND syncState = :synced AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedCustomTimersNotIn(
        memberId: String,
        serverIds: List<Long>,
        synced: SyncState = SyncState.SYNCED,
    )
}
