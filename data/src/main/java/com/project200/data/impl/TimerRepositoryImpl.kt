package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerIdDTO
import com.project200.data.dto.SimpleTimerRequest
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class TimerRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): TimerRepository {
    override suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getSimpleTimers() },
            mapper = { dto: GetSimpleTimersDTO? ->
                dto?.simpleTimers?.map { it.toModel() } ?: emptyList()
            }
        )
    }

    override suspend fun editSimpleTimer(simpleTimer: SimpleTimer): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.patchSimpleTimer(simpleTimer.id, SimpleTimerRequest(simpleTimer.time)) },
            mapper = { Unit }
        )
    }

    override suspend fun addSimpleTimer(time: Int): BaseResult<Long> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postSimpleTimer(SimpleTimerRequest(time)) },
            mapper = { dto: SimpleTimerIdDTO? -> dto?.simpleTimerId ?: -1L }
        )
    }

    override suspend fun deleteSimpleTimer(id: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteSimpleTimer(id) },
            mapper = { Unit }
        )
    }


}