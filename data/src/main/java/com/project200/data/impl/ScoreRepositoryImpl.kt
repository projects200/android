package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.mapper.toDomain
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.repository.ScoreRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class ScoreRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ScoreRepository {
        override suspend fun getExpectedScoreInfo(): BaseResult<ExpectedScoreInfo> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getExpectedScoreInfo() },
                mapper = { it?.toDomain() ?: throw NoSuchElementException("예상 획득 점수 정보가 없습니다.") },
            )
        }
    }
