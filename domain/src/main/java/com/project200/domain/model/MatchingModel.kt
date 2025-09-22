package com.project200.domain.model

data class MatchingMember(
    val memberId: String,
    val profileThumbnailUrl: String,
    val nickname: String,
    val gender: String, // 혹은 Enum 클래스로 정의하여 사용 가능
    val birthDate: String,
    val locations: List<Location>
)

data class Location(
    val exerciseLocationName: String,
    val latitude: String,
    val longitude: String
)