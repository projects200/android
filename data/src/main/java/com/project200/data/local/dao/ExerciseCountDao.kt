package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.project200.data.local.entity.ExerciseCountEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExerciseCountDao {
    @Query(
        "SELECT * FROM exercise_count " +
            "WHERE memberId = :memberId AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date",
    )
    fun observeRange(
        memberId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<ExerciseCountEntity>>

    @Upsert
    suspend fun upsertAll(counts: List<ExerciseCountEntity>)

    /** 서버 응답에는 운동한 날만 들어 있어서, 0건이 된 날을 지우려면 구간을 먼저 비워야 합니다 */
    @Query(
        "DELETE FROM exercise_count " +
            "WHERE memberId = :memberId AND date BETWEEN :startDate AND :endDate",
    )
    suspend fun deleteRange(
        memberId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    )
}
