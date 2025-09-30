package com.project200.domain.repository

import com.project200.domain.model.BaseResult
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
    suspend fun getOpenUrl(): BaseResult<String>
}