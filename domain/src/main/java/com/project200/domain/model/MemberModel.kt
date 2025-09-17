package com.project200.domain.model


data class Score(
    val score: Int,
    val maxScore: Int = 0,
    val minScore: Int = 100
)

data class UserProfile(
    val profileThumbnailUrl: String?,
    val profileImageUrl: String?,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val bio: String?,
    val yearlyExerciseDays: Int,
    val exerciseCountInLast30Days: Int,
    val exerciseScore: Int,
    val preferredExercises: List<PreferredExercise>
)

data class PreferredExercise(
    val preferredExerciseId: Int,
    val name: String,
    val skillLevel: String,
    val daysOfWeek: List<Boolean>,
    val imageUrl: String
)

data class ProfileImageList(
    val thumbnail: ProfileImage?,
    val images: List<ProfileImage>
)

data class ProfileImage(
    val id: Long,
    val url: String
)