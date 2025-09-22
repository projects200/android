package com.project200.data.dto

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class GetMatchingMembersDto(
    val memberId: String,
    val profileThumbnailUrl: String,
    val nickname: String,
    val gender: String,
    val birthDate: String,
    val locations: List<LocationDto>
)

@JsonClass(generateAdapter = true)
data class LocationDto(
    val exerciseLocationName: String,
    val latitude: Double,
    val longitude: Double
)