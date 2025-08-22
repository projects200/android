package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetCustomTimerDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class TimerRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): TimerRepository {

    override suspend fun getCustomTimers(): BaseResult<List<CustomTimer>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getCustomTimers() },
            mapper = { dto: GetCustomTimerDTO? ->
                dto?.toModel() ?: emptyList()
            }
        )
    }
}