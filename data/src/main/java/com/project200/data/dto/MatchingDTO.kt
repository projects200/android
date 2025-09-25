package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetMatchingMembersDto(
    val memberId: String,
    val profileThumbnailUrl: String,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val locations: List<LocationDto>,
)

@JsonClass(generateAdapter = true)
data class LocationDto(
    val exerciseLocationName: String,
    val latitude: Double,
    val longitude: Double,
)

@JsonClass(generateAdapter = true)
data class GetMatchingProfileDTO(
    val profileThumbnailUrl: String?,
    val profileImageUrl: String?,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val bio: String?,
    val yearlyExerciseDays: Int,
    val exerciseCountInLast30Days: Int,
    val exerciseScore: Int,
    val preferredExercises: List<PreferredExerciseDTO>,
)

@JsonClass(generateAdapter = true)
data class GetExercisePlaceDTO(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class DeleteExercisePlaceDTO(
    val id: Long,
)