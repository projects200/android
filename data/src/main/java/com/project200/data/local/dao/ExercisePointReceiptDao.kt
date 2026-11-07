package com.project200.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.project200.data.local.entity.ExercisePointReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExercisePointReceiptDao {
    @Query("SELECT * FROM exercise_point_receipt WHERE memberId = :memberId ORDER BY createRequestId")
    abstract suspend fun getByMember(memberId: String): List<ExercisePointReceiptEntity>

    @Query("SELECT * FROM exercise_point_receipt WHERE memberId = :memberId ORDER BY createRequestId")
    abstract fun observeByMember(memberId: String): Flow<List<ExercisePointReceiptEntity>>

    @Query("SELECT * FROM exercise_point_receipt WHERE memberId = :memberId AND createRequestId = :createRequestId")
    abstract suspend fun get(
        memberId: String,
        createRequestId: String,
    ): ExercisePointReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(receipt: ExercisePointReceiptEntity): Long

    @Transaction
    open suspend fun acknowledge(receipt: ExercisePointReceiptEntity) {
        require(receipt.earnedPoints >= 0 && receipt.serverId > 0)
        val previous = get(receipt.memberId, receipt.createRequestId)
        if (previous == null) {
            check(insert(receipt) != -1L)
        } else {
            require(
                previous.recordLocalId == receipt.recordLocalId && previous.serverId == receipt.serverId &&
                    previous.earnedPoints == receipt.earnedPoints,
            ) { "RECEIPT_CONTRACT_CHANGED" }
        }
    }

    @Query(
        "UPDATE exercise_point_receipt SET noticeConsumed = 1 WHERE memberId = :memberId " +
            "AND createRequestId = :createRequestId AND noticeConsumed = 0",
    )
    abstract suspend fun consume(
        memberId: String,
        createRequestId: String,
    ): Int

    @Query("DELETE FROM exercise_point_receipt WHERE memberId = :memberId AND recordLocalId = :localId")
    abstract suspend fun deleteByRecord(
        memberId: String,
        localId: String,
    )
}
