package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.mapper.toDomain
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.PolicyGroup
import com.project200.domain.model.ValidWindow
import com.project200.domain.repository.PolicyRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class PolicyRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PolicyRepository {

    override suspend fun getPolicyGroup(groupName: String): BaseResult<PolicyGroup> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getPolicyGroup(groupName) },
            mapper = { it?.toDomain() ?: throw NoSuchElementException("정책 데이터가 없습니다.") }
        )
    }
}