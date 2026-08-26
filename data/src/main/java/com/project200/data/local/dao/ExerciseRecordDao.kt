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

    /** 서버 목록을 반영할 때 이미 있는 행의 localId를 이어 쓰기 위해 씁니다 */
    @Query(
        "SELECT serverId, localId FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date AND serverId IS NOT NULL",
    )
    suspend fun getServerIdToLocalId(
        memberId: String,
        date: LocalDate,
    ): List<ServerIdLocalId>

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

    @Query("SELECT localId FROM exercise_list_item WHERE memberId = :memberId AND serverId = :serverId")
    suspend fun findListItemLocalId(
        memberId: String,
        serverId: Long,
    ): String?

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
