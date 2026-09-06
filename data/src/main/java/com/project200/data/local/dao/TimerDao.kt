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
}
