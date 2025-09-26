package com.project200.data.mapper

import com.project200.data.dto.GetMatchingMembersDto
import com.project200.data.dto.GetMatchingProfileDTO
import com.project200.data.dto.LocationDto
import com.project200.domain.model.Location
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.model.PreferredExercise

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

fun GetMatchingProfileDTO.toModel(): MatchingMemberProfile {
    return MatchingMemberProfile(
        profileThumbnailUrl = this.profileThumbnailUrl,
        profileImageUrl = this.profileImageUrl,
        nickname = this.nickname,
        gender = this.gender,
        birthDate = this.birthDate,
        bio = this.bio,
        yearlyExerciseDays = this.yearlyExerciseDays,
        exerciseCountInLast30Days = this.exerciseCountInLast30Days,
        exerciseScore = this.exerciseScore,
        preferredExercises =
            this.preferredExercises.map {
                PreferredExercise(
                    preferredExerciseId = it.preferredExerciseId,
                    name = it.name,
                    skillLevel = it.skillLevel,
                    daysOfWeek = it.daysOfWeek,
                    imageUrl = it.imageUrl,
                )
            },
    )
}
