package com.project200.data.mapper

import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.GetScoreDTO
import com.project200.domain.model.PreferredExercise
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
