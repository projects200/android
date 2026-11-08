package com.project200.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class ExerciseRecord(
    val title: String,
    val detail: String,
    val personalType: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val location: String,
    val pictures: List<ExerciseRecordPicture>?,
    val syncPending: Boolean = false,
    val canModify: Boolean = true,
    val serverVersion: Long? = null,
    val editedAt: Long? = null,
)

enum class ExerciseImageSyncStatus { PENDING, SYNCED, FAILED }

data class ExerciseRecordPicture(
    val localId: String,
    val serverId: Long?,
    val source: String,
    val syncStatus: ExerciseImageSyncStatus,
)

data class ExerciseListItem(
    val recordId: String,
    val title: String,
    val type: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val imageUrl: List<String>?,
    val syncPending: Boolean = false,
    val serverVersion: Long? = null,
)

data class ExerciseCount(
    val date: LocalDate,
    val count: Int,
    val syncPending: Boolean = false
)

data class LocalExerciseSaveResult(
    val recordId: String,
)

data class ExerciseWriteInput(
    val record: ExerciseRecord,
    val addedImageUris: List<String> = emptyList(),
    val removedImageLocalIds: Set<String> = emptySet(),
    /** 폼의 최초 스냅샷과 비교합니다. 사진만 저장할 때 오래된 본문에 새 시각을 붙이지 않습니다. */
    val bodyEdited: Boolean = true,
)
