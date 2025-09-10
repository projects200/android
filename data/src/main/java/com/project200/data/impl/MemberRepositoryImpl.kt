package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.GetScoreDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Score
import com.project200.domain.model.UserProfile
import com.project200.domain.repository.MemberRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class MemberRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
    }
