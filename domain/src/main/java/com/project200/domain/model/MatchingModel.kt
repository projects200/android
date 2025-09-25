package com.project200.domain.model

data class MatchingMember(
    val memberId: String,
    val profileThumbnailUrl: String,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val locations: List<Location>
)

data class Location(
    val exerciseLocationName: String,
    val latitude: Double,
    val longitude: Double
)

data class MapPosition(
    val latitude: Double,
    val longitude: Double,
    val zoomLevel: Int,
)