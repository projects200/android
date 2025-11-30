package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.BlockedMember
import com.project200.domain.model.ExerciseType
import com.project200.domain.model.OpenUrl
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.ProfileImageList
import com.project200.domain.model.Score
import com.project200.domain.model.UserProfile

interface MemberRepository {
    suspend fun getScore(): BaseResult<Score>
    suspend fun getUserProfile(): BaseResult<UserProfile>
    suspend fun getProfileImages(): BaseResult<ProfileImageList>
    suspend fun changeThumbnail(pictureId: Long): BaseResult<Unit>
    suspend fun deleteProfileImages(pictureId: Long): BaseResult<Unit>
    suspend fun editUserProfile(nickname: String, gender: String, introduction: String): BaseResult<Unit>
    suspend fun addProfileImage(uri: String): BaseResult<Unit>
    suspend fun getOpenUrl(): BaseResult<OpenUrl>
    suspend fun addOpenUrl(url: String): BaseResult<Unit>
    suspend fun editOpenUrl(id: Long, url: String): BaseResult<Unit>
    suspend fun blockMember(memberId: String): BaseResult<Unit>
    suspend fun unblockMember(memberId: String): BaseResult<Unit>
    suspend fun getBlockedMembers(): BaseResult<List<BlockedMember>>
    suspend fun getPreferredExercises(): BaseResult<List<PreferredExercise>>
    suspend fun getPreferredExerciseTypes(): BaseResult<List<ExerciseType>>
    suspend fun createPreferredExercise(preferredExercises: List<PreferredExercise>): BaseResult<Unit>
    suspend fun editPreferredExercise(preferredExercises: List<PreferredExercise>): BaseResult<Unit>
    suspend fun deletePreferredExercise(preferredExerciseIds: List<Long>): BaseResult<Unit>
}