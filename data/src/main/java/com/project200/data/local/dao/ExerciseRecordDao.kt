package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SyncState
import java.time.LocalDate

@Dao
interface ExerciseRecordDao {
    /** 화면은 진입할 때 한 번 읽습니다. 지속 구독을 두지 않아 그린 뒤 저절로 바뀌지 않습니다 */
    @Query(
        "SELECT * FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date AND syncState != :excluded " +
            "ORDER BY sortOrder",
    )
    suspend fun getListByDate(
        memberId: String,
        date: LocalDate,
        excluded: SyncState = SyncState.DELETE_PENDING,
    ): List<ExerciseListItemEntity>

    /**
     * 그날 캐시돼 있는 서버 기록입니다. 서버 목록에서 사라진 기록을 골라내는 데 씁니다.
     * 대기 행은 서버가 모르는 로컬 변경이라 빼고 봅니다
     */
    @Query(
        "SELECT serverId, localId FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date " +
            "AND serverId IS NOT NULL AND syncState = :syncState",
    )
    suspend fun getSyncedByDate(
        memberId: String,
        date: LocalDate,
        syncState: SyncState = SyncState.SYNCED,
    ): List<ServerIdLocalId>

    /**
     * 서버 목록에 있는 기록 중 이미 캐시된 것의 localId입니다.
     * 날짜를 가리지 않아서 서버에서 기록의 날짜가 바뀌어도 localId를 이어 쓸 수 있습니다
     */
    @Query(
        "SELECT serverId, localId FROM exercise_list_item " +
            "WHERE memberId = :memberId AND serverId IN (:serverIds) AND syncState = :syncState",
    )
    suspend fun getSyncedByServerIds(
        memberId: String,
        serverIds: List<Long>,
        syncState: SyncState = SyncState.SYNCED,
    ): List<ServerIdLocalId>

    /** 아직 못 올린 로컬 변경이 걸려 있는 기록입니다. 서버 값으로 덮으면 안 됩니다 */
    @Query(
        "SELECT serverId FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date " +
            "AND serverId IS NOT NULL AND syncState != :syncState",
    )
    suspend fun getPendingServerIds(
        memberId: String,
        date: LocalDate,
        syncState: SyncState = SyncState.SYNCED,
    ): List<Long>

    @Upsert
    suspend fun upsertListItems(items: List<ExerciseListItemEntity>)

    /**
     * 서버에서 지워진 기록이 남지 않도록 그날 목록을 갈아끼울 때 씁니다.
     * 아직 못 올린 로컬 변경이 사라지면 안 되므로 전송 대기 행은 건드리지 않습니다
     */
    @Query(
        "DELETE FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date AND syncState = :syncState",
    )
    suspend fun deleteSyncedListByDate(
        memberId: String,
        date: LocalDate,
        syncState: SyncState = SyncState.SYNCED,
    )

    @Query("SELECT * FROM exercise_record_detail WHERE memberId = :memberId AND localId = :localId")
    suspend fun getDetailByLocalId(
        memberId: String,
        localId: String,
    ): ExerciseRecordDetailEntity?

    /** 화면이 아직 서버 ID로 상세에 들어옵니다. localId 기준 진입은 #584에서 바뀝니다 */
    @Query("SELECT * FROM exercise_record_detail WHERE memberId = :memberId AND serverId = :serverId")
    suspend fun getDetailByServerId(
        memberId: String,
        serverId: Long,
    ): ExerciseRecordDetailEntity?

    @Query("SELECT localId FROM exercise_record_detail WHERE memberId = :memberId AND serverId = :serverId")
    suspend fun findDetailLocalId(
        memberId: String,
        serverId: Long,
    ): String?

    /** 같은 serverId가 여러 날짜에 남아 있어도 한 행만 돌려주도록 최신 날짜를 집습니다 */
    @Query(
        "SELECT localId FROM exercise_list_item " +
            "WHERE memberId = :memberId AND serverId = :serverId " +
            "ORDER BY date DESC LIMIT 1",
    )
    suspend fun findListItemLocalId(
        memberId: String,
        serverId: Long,
    ): String?

    /** 서버에서 기록의 날짜가 바뀌면 옛 날짜에 남은 행을 지웁니다. 같은 serverId가 두 행이 되는 걸 막습니다 */
    @Query(
        "DELETE FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date != :date " +
            "AND serverId IN (:serverIds) AND syncState = :syncState",
    )
    suspend fun deleteSyncedOnOtherDates(
        memberId: String,
        date: LocalDate,
        serverIds: List<Long>,
        syncState: SyncState = SyncState.SYNCED,
    )

    /** 서버 목록에서 사라진 기록의 상세를 함께 정리합니다 */
    @Query("DELETE FROM exercise_record_detail WHERE memberId = :memberId AND localId IN (:localIds)")
    suspend fun deleteDetailsByLocalIds(
        memberId: String,
        localIds: List<String>,
    )

    @Upsert
    suspend fun upsertDetail(detail: ExerciseRecordDetailEntity)

    @Query("DELETE FROM exercise_record_detail WHERE memberId = :memberId AND localId = :localId")
    suspend fun deleteDetail(
        memberId: String,
        localId: String,
    )

    @Query("DELETE FROM exercise_list_item WHERE memberId = :memberId AND localId = :localId")
    suspend fun deleteListItem(
        memberId: String,
        localId: String,
    )
}

/** 서버 ID와 로컬 ID의 짝입니다 */
data class ServerIdLocalId(
    val serverId: Long,
    val localId: String,
)
