package com.project200.data.mapper

import com.project200.data.dto.ExpectedScoreInfoDTO
import com.project200.data.dto.ScorePolicyDTO
import com.project200.data.dto.ValidWindowDTO
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.ScorePolicy
import com.project200.domain.model.ValidWindow

fun ScorePolicyDTO.toDomainModel(): ScorePolicy {
    return ScorePolicy(
        policyKey = this.policyKey,
        policyValue = this.policyValue,
        policyUnit = this.policyUnit,
    )
}

fun ExpectedScoreInfoDTO.toDomain(): ExpectedScoreInfo {
    return ExpectedScoreInfo(
        pointsPerExercise = this.pointsPerExercise,
        currentUserScore = this.currentUserScore,
        maxScore = this.maxScore,
        validWindow = this.validWindow.toDomain(),
        earnableScoreDays = this.earnableScoreDays
    )
}

fun ValidWindowDTO.toDomain(): ValidWindow {
    return ValidWindow(
        startDateTime = this.startDateTime,
        endDateTime = this.endDateTime
    )
}