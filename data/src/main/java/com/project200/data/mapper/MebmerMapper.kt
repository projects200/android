package com.project200.data.mapper

import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetScoreDTO
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.Score


fun GetScoreDTO.toModel(): Score {
    return Score(
        score = memberScore,
        maxScore = policyMaxScore,
        minScore = policyMinScore
    )
}