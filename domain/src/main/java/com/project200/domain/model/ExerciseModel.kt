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
    val pictures: List<ExerciseRecordPicture>?
)

data class ExerciseRecordPicture(
    val id: Long,
    val url: String
)

data class ExerciseListItem(
    val recordId: Long,
    val title: String,
    val type: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val imageUrl: List<String>?
)

data class ExerciseCount(
    val date: LocalDate,
    val count: Int
)

data class ExerciseRecordCreationResult(
    val recordId: Long,
    val earnedPoints: Int
)