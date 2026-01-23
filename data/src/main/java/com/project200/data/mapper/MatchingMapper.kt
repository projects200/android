package com.project200.data.mapper

import com.project200.data.dto.GetExercisePlaceDTO
import com.project200.data.dto.GetMatchingMembersDto
import com.project200.data.dto.GetMatchingProfileDTO
import com.project200.data.dto.LocationDto
import com.project200.data.dto.PostExercisePlaceDTO
import com.project200.domain.model.ExercisePlace
import com.project200.domain.model.Location
import com.project200.domain.model.MatchingMember
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.model.PreferredExercise

fun GetMatchingMembersDto.toModel(): MatchingMember {
    return MatchingMember(
        memberId = this.memberId,
        profileThumbnailUrl = this.profileThumbnailUrl,
        profileImageUrl = this.profileImageUrl,
        nickname = this.nickname,
        gender = this.gender,
        birthDate = this.birthDate,
        memberScore = this.memberScore,
        locations = this.locations.map { it.toModel() },
        preferredExercises =
            this.preferredExercises.map {
                PreferredExercise(
                    preferredExerciseId = it.preferredExerciseId,
                    name = it.name,
                    skillLevel = it.skillLevel,
                    daysOfWeek = it.daysOfWeek,
                    imageUrl = it.imageUrl,
                    exerciseTypeId = -1
                )
            },
    )
}

fun LocationDto.toModel(): Location {
    return Location(
        placeId = this.exerciseLocationId,
        placeName = this.exerciseLocationName,
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
            this.preferredExercises.map { it.toModel() },
    )
}

fun GetExercisePlaceDTO.toModel(): ExercisePlace {
    return ExercisePlace(
        id = this.id,
        name = this.name,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
    )
}

fun ExercisePlace.toDTO(): PostExercisePlaceDTO {
    return PostExercisePlaceDTO(
        name = this.name,
        address = this.address,
        latitude = this.latitude,
        longitude = this.longitude,
    )
}
