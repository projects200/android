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
    val preferredExerciseId: Long,
    val exerciseTypeId: Long,
    val name: String,
    val skillLevel: String,
    val daysOfWeek: List<Boolean>,
    val imageUrl: String
)

data class ExerciseType(
    val id: Long,
    val name: String,
    val imageUrl: String
) {
    fun toEmptyPreferredExercise(): PreferredExercise {
        return PreferredExercise(
            preferredExerciseId = -1,
            exerciseTypeId = id,
            name = name,
            skillLevel = "",
            daysOfWeek = List(7) { false },
            imageUrl = imageUrl
        )
    }
}

data class ProfileImageList(
    val thumbnail: ProfileImage?,
    val images: List<ProfileImage>
)

data class ProfileImage(
    val id: Long,
    val url: String
)

data class OpenUrl(
    val id: Long,
    val url: String
)

data class BlockedMember(
    val memberBlockId: Long,
    val memberId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?
)