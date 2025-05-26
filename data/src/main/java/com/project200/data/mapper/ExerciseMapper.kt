package com.project200.data.mapper

import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun GetExerciseRecordData.toModel(): ExerciseRecord {
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

fun ExerciseRecord.toPostExerciseDTO(): PostExerciseRequestDto {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    return PostExerciseRequestDto(
        exerciseTitle = this.title,
        exercisePersonalType = this.personalType,
        exerciseLocation = this.location,
        exerciseDetail = this.detail,
        exerciseStartedAt = this.startedAt.format(formatter),
        exerciseEndedAt = this.endedAt.format(formatter)
    )
}

fun GetExerciseRecordListDto.toModel(): ExerciseListItem {
    return ExerciseListItem(
            recordId = this.exerciseId,
            title = this.exerciseTitle,
            type = this.exercisePersonalType,
            startTime = this.exerciseStartedAt,
            endTime = this.exerciseEndedAt,
            imageUrl = this.pictureUrl
        )
}