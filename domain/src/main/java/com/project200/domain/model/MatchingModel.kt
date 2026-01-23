package com.project200.domain.model

data class MatchingMember(
    val memberId: String,
    val profileThumbnailUrl: String?,
    val profileImageUrl: String?,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val locations: List<Location>
)

data class Location(
    val placeId: Long,
    val placeName: String,
    val latitude: Double,
    val longitude: Double
)

data class MapPosition(
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int,
)

data class MatchingMemberProfile(
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

data class ExercisePlace(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)