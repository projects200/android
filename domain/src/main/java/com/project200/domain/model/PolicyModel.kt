package com.project200.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Policy(
    val policyKey: String,
    val policyValue: String,
    val policyUnit: String,
    val policyDescription: String
)

data class PolicyGroup(
    val groupName: String,
    val size: Int,
    val policies: List<Policy>
)

// 예상 획득 점수
data class ExpectedScoreInfo(
    val pointsPerExercise: Int,
    val currentUserScore: Int,
    val maxScore: Int,
    val validWindow: ValidWindow,
    val earnableScoreDays: List<LocalDate>
)

data class ValidWindow(
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime
)
