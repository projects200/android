package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetExerciseRecordData(
    val exerciseTitle: String,
    val exerciseDetail: String,
    val exercisePersonalType: String,
    val exerciseStartedAt: LocalDateTime,
    val exerciseEndedAt: LocalDateTime,
    val exerciseLocation: String,
    val pictureDataList: List<PictureData>?
)

data class PictureData(
    val pictureId: Long,
    val pictureUrl: String,
    val pictureName: String,
    val pictureExtension: String
)

@JsonClass(generateAdapter = true)
data class PostExerciseRequestDto(
    val exerciseTitle: String?,
    val exercisePersonalType: String?,
    val exerciseLocation: String?,
    val exerciseDetail: String?,
    val exerciseStartedAt: String?,
    val exerciseEndedAt: String?
)

@JsonClass(generateAdapter = true)
data class GetExerciseRecordListDto(
    val exerciseId: Long,
    val exerciseTitle: String,
    val exercisePersonalType: String,
    val exerciseStartedAt: LocalDateTime,
    val exerciseEndedAt: LocalDateTime,
    val pictureUrl: String?
)