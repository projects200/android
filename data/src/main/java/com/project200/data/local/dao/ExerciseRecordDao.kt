package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExerciseRecordDao {
    @Query(
        "SELECT * FROM exercise_list_item " +
            "WHERE memberId = :memberId AND date = :date " +
            "ORDER BY sortOrder",
    )
    fun observeListByDate(
        memberId: String,
        date: LocalDate,
    ): Flow<List<ExerciseListItemEntity>>

    @Upsert
    suspend fun upsertListItems(items: List<ExerciseListItemEntity>)

    /** 서버에서 지워진 기록이 남지 않도록 그날 목록을 갈아끼울 때 씁니다 */
    @Query("DELETE FROM exercise_list_item WHERE memberId = :memberId AND date = :date")
    suspend fun deleteListByDate(
        memberId: String,
        date: LocalDate,
    )

    @Query("SELECT * FROM exercise_record_detail WHERE memberId = :memberId AND recordId = :recordId")
    fun observeDetail(
        memberId: String,
        recordId: Long,
    ): Flow<ExerciseRecordDetailEntity?>

    @Upsert
    suspend fun upsertDetail(detail: ExerciseRecordDetailEntity)

    @Query("DELETE FROM exercise_record_detail WHERE memberId = :memberId AND recordId = :recordId")
    suspend fun deleteDetail(
        memberId: String,
        recordId: Long,
    )

    @Query("DELETE FROM exercise_list_item WHERE memberId = :memberId AND recordId = :recordId")
    suspend fun deleteListItem(
        memberId: String,
        recordId: Long,
    )
}
