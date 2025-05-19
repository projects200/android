package com.project200.data.mapper

import com.project200.data.dto.GetExerciseRecordResult
import com.project200.domain.model.ExerciseRecord

fun GetExerciseRecordResult.toDomain(): ExerciseRecord {
    return ExerciseRecord(
        title = exerciseTitle,
        detail = exerciseDetail,
        personalType = exercisePersonalType,
        startedAt = exerciseStartedAt,
        endedAt = exerciseEndedAt,
        location = exerciseLocation,
        pictureUrls = exercisePictureUrls
    )
}
