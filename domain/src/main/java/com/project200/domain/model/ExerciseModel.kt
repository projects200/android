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
    val canModify: Boolean = true
)

data class ExerciseRecordPicture(
    val id: Long,
    val url: String
)

data class ExerciseListItem(
    val recordId: String,
    val title: String,
    val type: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val imageUrl: List<String>?,
    val syncPending: Boolean = false
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
    // 사진 모델 전환(5/9) 전에는 기존 사진의 양수 서버 ID만 받습니다
    val removedImageIds: Set<Long> = emptySet(),
)
