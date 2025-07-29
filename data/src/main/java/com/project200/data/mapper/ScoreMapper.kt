package com.project200.data.mapper

import com.project200.data.dto.ExpectedScoreInfoDTO
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.ValidWindow

fun ExpectedScoreInfoDTO.toDomain(): ExpectedScoreInfo {
    return ExpectedScoreInfo(
        pointsPerExercise = this.pointsPerExercise,
        currentUserScore = this.currentUserScore,
        maxScore = this.maxScore,
        validWindow = ValidWindow(
            startDateTime = this.validWindow.startDateTime,
            endDateTime = this.validWindow.endDateTime
        ),
        earnableScoreDays = this.earnableScoreDates
    )
}