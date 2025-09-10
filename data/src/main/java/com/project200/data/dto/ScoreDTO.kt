package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class ExpectedScoreInfoDTO(
    val pointsPerExercise: Int,
    val currentUserScore: Int,
    val maxScore: Int,
    val validWindow: ValidWindowDTO,
    val earnableScoreDates: List<LocalDate>,
)

@JsonClass(generateAdapter = true)
data class ValidWindowDTO(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
)
