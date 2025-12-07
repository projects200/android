package com.project200.data.mapper

import com.project200.data.dto.GetBlockedMemberDTO
import com.project200.data.dto.GetOpenChatUrlDTO
import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.GetPreferredExerciseTypeDTO
import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.GetProfileImageResponseDto
import com.project200.data.dto.GetScoreDTO
import com.project200.domain.model.BlockedMember
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.OpenUrl
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.ProfileImage
import com.project200.domain.model.ProfileImageList
import com.project200.domain.model.Score
import com.project200.domain.model.UserProfile

fun GetScoreDTO.toModel(): Score {
    return Score(
        score = memberScore,
        maxScore = policyMaxScore,
        minScore = policyMinScore,
    )
}

fun GetProfileDTO.toModel(): UserProfile {
    return UserProfile(
        profileThumbnailUrl = this.profileThumbnailUrl,
        profileImageUrl = this.profileImageUrl,
        nickname = this.nickname,
        gender = this.gender,
        birthDate = this.birthDate,
        bio = this.bio,
        yearlyExerciseDays = this.yearlyExerciseDays,
        exerciseCountInLast30Days = this.exerciseCountInLast30Days,
        exerciseScore = this.exerciseScore,
        preferredExercises = this.preferredExercises.map { it.toModel() },
    )
}

fun GetPreferredExerciseDTO.toModel(): PreferredExercise {
    return PreferredExercise(
        preferredExerciseId = this.preferredExerciseId,
        exerciseTypeId = this.exerciseTypeId,
        name = this.name,
        skillLevel = this.skillLevel,
        daysOfWeek = this.daysOfWeek,
        imageUrl = this.imageUrl,
    )
}

fun GetPreferredExerciseTypeDTO.toModel(): ExerciseType {
    return ExerciseType(
        id =  this.exerciseTypeId,
        name = this.name,
        imageUrl = this.imageUrl,
    )
}


fun GetProfileImageResponseDto.toModel(): ProfileImageList {
    return ProfileImageList(
        thumbnail =
            this.representativeProfileImage?.let {
                ProfileImage(
                    id = this.representativeProfileImage.profileImageId,
                    url = this.representativeProfileImage.profileImageUrl,
                )
            },
        images =
            this.profileImages.map {
                ProfileImage(
                    id = it.profileImageId,
                    url = it.profileImageUrl,
                )
            },
    )
}

fun GetOpenChatUrlDTO.toModel(): OpenUrl {
    return OpenUrl(
        id = this.openChatroomId,
        url = this.openChatroomUrl,
    )
}

fun GetBlockedMemberDTO.toModel(): BlockedMember {
    return BlockedMember(
        memberBlockId = this.memberBlockId,
        memberId = this.memberId,
        nickname = this.nickname,
        profileImageUrl = this.profileImageUrl,
        thumbnailImageUrl = this.thumbnailImageUrl,
    )
}
