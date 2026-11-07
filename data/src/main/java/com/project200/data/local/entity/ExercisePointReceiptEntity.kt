package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "exercise_point_receipt",
    primaryKeys = ["memberId", "createRequestId"],
    indices = [Index(value = ["memberId", "recordLocalId"])],
)
data class ExercisePointReceiptEntity(
    val memberId: String,
    val createRequestId: String,
    val recordLocalId: String,
    val serverId: Long,
    val earnedPoints: Int,
    val noticeConsumed: Boolean,
)
