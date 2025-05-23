package com.project200.data.mapper

import com.project200.data.dto.GetExerciseRecordData
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture

fun GetExerciseRecordData.toDomain(): ExerciseRecord {
    return ExerciseRecord(
        title = exerciseTitle,
        detail = exerciseDetail,
        personalType = exercisePersonalType,
        startedAt = exerciseStartedAt,
        endedAt = exerciseEndedAt,
        location = exerciseLocation,
        pictures = pictureDataList?.map { ExerciseRecordPicture(it.pictureId, it.pictureUrl) } ?: emptyList()
    )
}
