package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.project200.data.local.entity.ExerciseCountEntity
import java.time.LocalDate

@Dao
interface ExerciseCountDao {
    /** 화면은 진입할 때 한 번 읽습니다. 지속 구독을 두지 않아 그린 뒤 저절로 바뀌지 않습니다 */
    @Query(
        "SELECT * FROM exercise_count " +
            "WHERE memberId = :memberId AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date",
    )
    suspend fun getRange(
        memberId: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ExerciseCountEntity>

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
