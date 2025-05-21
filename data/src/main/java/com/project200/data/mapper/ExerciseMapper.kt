package com.project200.data.mapper

import com.project200.data.dto.GetExerciseRecordData
import com.project200.domain.model.ExerciseRecord

fun GetExerciseRecordData.toDomain(): ExerciseRecord {
    return ExerciseRecord(
        title = exerciseTitle,
        detail = exerciseDetail,
        personalType = exercisePersonalType,
        startedAt = exerciseStartedAt,
        endedAt = exerciseEndedAt,
        location = exerciseLocation,
        pictureUrls = pictureDataList?.map { it.pictureUrl } ?: emptyList()
    )
}
