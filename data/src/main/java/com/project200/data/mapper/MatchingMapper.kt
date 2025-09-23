package com.project200.data.mapper

import com.project200.data.dto.GetMatchingMembersDto
import com.project200.data.dto.LocationDto
import com.project200.domain.model.Location
import com.project200.domain.model.MatchingMember

fun GetMatchingMembersDto.toModel(): MatchingMember {
    return MatchingMember(
        memberId = this.memberId,
        profileThumbnailUrl = this.profileThumbnailUrl,
        nickname = this.nickname,
        gender = this.gender,
        birthDate = this.birthDate,
        locations = this.locations.map { it.toModel() },
    )
}

fun LocationDto.toModel(): Location {
    return Location(
        exerciseLocationName = this.exerciseLocationName,
        latitude = this.latitude,
        longitude = this.longitude,
    )
}
