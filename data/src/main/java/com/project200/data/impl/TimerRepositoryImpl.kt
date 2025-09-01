package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.CustomTimerIdDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerIdDTO
import com.project200.data.dto.SimpleTimerRequest
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import com.project200.data.dto.PatchCustomTimerTitleRequest
import com.project200.data.dto.PostCustomTimerRequest
import com.project200.data.mapper.toDTO
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step


import javax.inject.Inject

class TimerRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): TimerRepository {

    /** 심플 타이머 */
    // 심플 타이머 전체 조회
    override suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getSimpleTimers() },
            mapper = { dto: GetSimpleTimersDTO? ->
                dto?.simpleTimers?.map { it.toModel() } ?: emptyList()
            }
        )
    }

    // 심플 타이머 수정
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

    /** 커스텀 타이머 */
    // 커스텀 타이머 전체 조회
    override suspend fun getCustomTimerList(): BaseResult<List<CustomTimer>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getCustomTimerList() },
            mapper = { dto: GetCustomTimerListDTO? ->
                dto?.toModel() ?: emptyList()
            }
        )
    }

    // 커스텀 타이머 상세 조회
    override suspend fun getCustomTimer(customTimerId: Long): BaseResult<CustomTimer> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getCustomTimer(customTimerId) },
            mapper = { dto: GetCustomTimerDetailDTO? ->
                dto?.toModel() ?: CustomTimer(-1L, "")
            }
        )
    }

    // 커스텀 타이머 생성
    override suspend fun createCustomTimer(
        title: String,
        steps: List<Step>
    ): BaseResult<Long> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                apiService.postCustomTimer(
                    PostCustomTimerRequest(
                        customTimerName = title,
                        customTimerSteps = steps.toDTO()
                    )
                )
            },
            mapper = { dto: CustomTimerIdDTO? ->
                dto?.customTimerId ?: throw IllegalStateException()
            }
        )
    }

    // 커스텀 타이머 삭제
    override suspend fun deleteCustomTimer(customTimerId: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteCustomTimer(customTimerId) },
            mapper = { Unit }
        )
    }

    // 커스텀 타이머 이름 수정
    override suspend fun editCustomTimerTitle(
        customTimerId: Long,
        title: String
    ): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.patchCustomTimerTitle(customTimerId, PatchCustomTimerTitleRequest(title)) },
            mapper = { Unit }
        )
    }

    // 커스텀 타이머 전체 수정
    override suspend fun editCustomTimer(
        customTimerId: Long,
        title: String,
        steps: List<Step>
    ): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                apiService.putCustomTimer(
                    customTimerId,
                    PostCustomTimerRequest(
                        customTimerName = title,
                        customTimerSteps = steps.toDTO()
                    )
                )
            },
            mapper = { Unit }
        )
    }
}