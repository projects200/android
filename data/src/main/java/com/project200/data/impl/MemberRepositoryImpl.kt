package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
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
            return BaseResult.Success(
                UserProfile(
                    profileThumbnailUrl = "",
                    profileImageUrl = "",
                    nickname = "운다방",
                    gender = "M",
                    birthDate = "1990-01-01",
                    bio = "안녕하세요! 운동하는 개발자입니다.",
                    yearlyExerciseDays = 120,
                    exerciseCountInLast30Days = 15,
                    exerciseScore = 85,
                    preferredExercises = listOf(),
                ),
            )
        }
    }
