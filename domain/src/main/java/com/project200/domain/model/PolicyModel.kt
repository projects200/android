package com.project200.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class ScorePolicy(
    val policyKey: String,
    val policyValue: Int,
    val policyUnit: String
)


enum class PolicyType(val key: String) {
    // 점수 최소, 최대
    EXERCISE_SCORE_MAX_POINTS("EXERCISE_SCORE_MAX_POINTS"),
    EXERCISE_SCORE_MIN_POINTS("EXERCISE_SCORE_MIN_POINTS"),

    // 회원가입 시 부여받는 첫 점수
    SIGNUP_INITIAL_POINTS("SIGNUP_INITIAL_POINTS"),
    // 운동 1회 당 획득 점수
    POINTS_PER_EXERCISE("POINTS_PER_EXERCISE"),
    // 점수 획득이 가능한 운동 기록의 유효 기간
    EXERCISE_RECORD_VALIDITY_PERIOD("EXERCISE_RECORD_VALIDITY_PERIOD"),

    // 페널티가 시작되는 비활성 기준일 (이 기간 이상 운동 기록이 없을 경우)
    PENALTY_INACTIVITY_THRESHOLD_DAYS("PENALTY_INACTIVITY_THRESHOLD_DAYS"),
    // 비활성 상태일 때 매일 차감되는 점수
    PENALTY_SCORE_DECREMENT_POINTS("PENALTY_SCORE_DECREMENT_POINTS");

    companion object {
        fun fromKey(key: String): PolicyType? = entries.find { it.key == key }
    }
}

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
