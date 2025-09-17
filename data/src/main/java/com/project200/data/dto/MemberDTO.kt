package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetScoreDTO(
    val memberId: String,
    val memberScore: Int,
    val policyMaxScore: Int,
    val policyMinScore: Int,
)

@JsonClass(generateAdapter = true)
data class GetProfileDTO(
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
data class PreferredExerciseDTO(
    val preferredExerciseId: Int,
    val name: String,
    val skillLevel: String,
    val daysOfWeek: List<Boolean>,
    val imageUrl: String,
)

@JsonClass(generateAdapter = true)
data class PutProfileRequest(
    val nickname: String,
    val gender: String,
    val bio: String,
)
