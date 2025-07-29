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
    val earnableScoreDays: List<LocalDate>
)

@JsonClass(generateAdapter = true)
data class ValidWindowDTO(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime
)

@JsonClass(generateAdapter = true)
data class PolicyGroupDTO(
    val groupName: String,
    val size: Int,
    val policies: List<PolicyDTO>
)

@JsonClass(generateAdapter = true)
data class PolicyDTO(
    val policyKey: String,
    val policyValue: String,
    val policyUnit: String,
    val policyDescription: String
)