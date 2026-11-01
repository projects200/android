package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDate
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetExerciseRecordData(
    val exerciseTitle: String,
    val exerciseDetail: String,
    val exercisePersonalType: String,
    val exerciseStartedAt: LocalDateTime,
    val exerciseEndedAt: LocalDateTime,
    val exerciseLocation: String,
    val pictureDataList: List<PictureData>?,
    val version: Long? = null,
)

data class PictureData(
    val pictureId: Long,
    val pictureUrl: String,
    val pictureName: String,
    val pictureExtension: String,
)

@JsonClass(generateAdapter = true)
data class PostExerciseRequestDto(
    val exerciseTitle: String?,
    val exercisePersonalType: String?,
    val exerciseLocation: String?,
    val exerciseDetail: String?,
    val exerciseStartedAt: String?,
    val exerciseEndedAt: String?,
    val createRequestId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PostExerciseResponseDTO(
    val exerciseId: Long,
    val earnedPoints: Int,
    val version: Long? = null,
)

@JsonClass(generateAdapter = true)
data class PatchExerciseRequestDto(
    val exerciseTitle: String?,
    val exercisePersonalType: String?,
    val exerciseLocation: String?,
    val exerciseDetail: String?,
    val exerciseStartedAt: String?,
    val exerciseEndedAt: String?,
    val editId: String? = null,
    val baseVersion: Long? = null,
)

@JsonClass(generateAdapter = true)
data class GetExerciseRecordListDto(
    val exerciseId: Long,
    val exerciseTitle: String,
    val exercisePersonalType: String,
    val exerciseStartedAt: LocalDateTime,
    val exerciseEndedAt: LocalDateTime,
    val pictureUrl: List<String>?,
    val version: Long? = null,
)

@JsonClass(generateAdapter = true)
data class ExerciseIdDto(
    val exerciseId: Long,
    val version: Long? = null,
)

@JsonClass(generateAdapter = true)
data class ExercisePicturesDto(
    val exerciseId: Long,
    val version: Long? = null,
    val pictures: List<ExercisePictureMappingDto>? = null,
)

@JsonClass(generateAdapter = true)
data class ExercisePictureMappingDto(
    val clientImageId: String,
    val pictureId: Long,
    val pictureUrl: String,
)

@JsonClass(generateAdapter = true)
data class GetExerciseCountByRangeDTO(
    val date: LocalDate,
    val exerciseCount: Int,
)
