package com.project200.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** 경로만 보존하며 파일 복사·삭제나 업로드는 수행하지 않습니다 */
@Entity(
    tableName = "exercise_image",
    primaryKeys = ["memberId", "recordLocalId", "localId"],
    indices = [Index(value = ["memberId", "recordLocalId", "serverPictureId"])],
)
data class ExerciseImageEntity(
    val memberId: String,
    val recordLocalId: String,
    val localId: String,
    val serverPictureId: Long?,
    val localFilePath: String?,
    val serverUrl: String?,
    val state: ExerciseImageState,
    val requestId: String?,
    val sortOrder: Int,
)

enum class ExerciseImageState {
    ADD_PENDING,
    DELETE_PENDING,
    SYNCED,
}
