package com.project200.data.impl

import android.content.Context
import androidx.core.net.toUri
import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.GetScoreDTO
import com.project200.data.dto.PutProfileRequest
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toMultipartBodyPart
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score
import com.project200.domain.model.UserProfile
import com.project200.domain.repository.MemberRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class MemberRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationContext private val context: Context,
    ) : MemberRepository {
        override suspend fun getScore(): BaseResult<Score> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getScore() },
                mapper = { dto: GetScoreDTO? ->
                    dto?.toModel() ?: throw NoSuchElementException("점수 조회 데이터가 없습니다.")
                },
            )
        }

        override suspend fun getUserProfile(): BaseResult<UserProfile> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getProfile() },
                mapper = { dto: GetProfileDTO? ->
                    dto?.toModel() ?: throw NoSuchElementException("유저 프로필 데이터가 없습니다.")
                },
            )
        }

    override suspend fun editUserProfile(nickname: String, gender: String, introduction: String): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.editProfile(PutProfileRequest(nickname, gender, introduction)) },
            mapper = { Unit },
        )
    }

    override suspend fun addProfileImage(image: String): BaseResult<Unit> {
        val imagePart = image.toUri().toMultipartBodyPart(context, "my-profile")
        if (imagePart == null) {
            // Multipart 변환 실패
            return BaseResult.Error(IMAGE_PART_ERROR, "")
        }

        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                apiService.postProfileImage(imagePart) },
            mapper = { Unit },
        )
    }

    companion object {
        const val IMAGE_PART_ERROR = "IMAGE_PART_ERROR"
    }
}
