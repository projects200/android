package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.datasource.ServerCustomTimer
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.mapper.toCachedSteps
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toServerModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step
import com.project200.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class TimerRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        private val localDataSource: TimerLocalDataSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : TimerRepository {
        override suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>> {
            val listResult =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getSimpleTimers() },
                    mapper = { dto: GetSimpleTimersDTO? ->
                        dto?.simpleTimers?.map { it.toServerModel() } ?: emptyList()
                    },
                )
            return when (listResult) {
                is BaseResult.Success -> {
                    localDataSource.replaceSyncedSimpleTimers(listResult.data)
                    getLocalSimpleTimers()
                }
                is BaseResult.Error -> {
                    if (listResult.errorCode == NETWORK_ERROR_CODE) getLocalSimpleTimers() else listResult
                }
            }
        }

        override suspend fun getLocalSimpleTimers(): BaseResult<List<SimpleTimer>> {
            return BaseResult.Success(localDataSource.getSimpleTimers().map { it.toModel() })
        }

        override suspend fun addSimpleTimer(time: Int): BaseResult<String> {
            val localId = localDataSource.createSimpleTimer(time) ?: return noMemberIdError()
            return BaseResult.Success(localId)
        }

        override suspend fun editSimpleTimer(
            localId: String,
            time: Int,
        ): BaseResult<Unit> {
            localDataSource.updateSimpleTimer(localId, time)
            return BaseResult.Success(Unit)
        }

        override suspend fun deleteSimpleTimer(localId: String): BaseResult<Unit> {
            localDataSource.deleteSimpleTimer(localId)
            return BaseResult.Success(Unit)
        }

        /**
         * 목록 GET은 스텝을 주지 않아 반영할 항목마다 상세 GET을 따로 불러 스텝을 채웁니다.
         *
         * 오프라인 실행에 스텝이 있어야 해서 가져오는 것이고, 목록 크기가 작아 N+1 호출을 감수합니다.
         * 상세 조회가 실패한 항목은 반영에서 빼고, 로컬에 이미 값이 있으면 그 값으로 채워 지워지지
         * 않게 합니다
         */
        override suspend fun getCustomTimerList(): BaseResult<List<CustomTimer>> {
            val listResult =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getCustomTimerList() },
                    mapper = { dto: GetCustomTimerListDTO? -> dto?.customTimers ?: emptyList() },
                )
            return when (listResult) {
                is BaseResult.Success -> {
                    val cachedByServerId =
                        localDataSource.getCustomTimers()
                            .mapNotNull { entity -> entity.serverId?.let { it to entity } }
                            .toMap()
                    val reflected =
                        listResult.data.mapNotNull { summary ->
                            fetchCustomTimerDetail(summary.customTimerId, cachedByServerId[summary.customTimerId])
                        }
                    localDataSource.replaceSyncedCustomTimers(reflected)
                    getLocalCustomTimerList()
                }
                is BaseResult.Error -> {
                    if (listResult.errorCode == NETWORK_ERROR_CODE) getLocalCustomTimerList() else listResult
                }
            }
        }

        // 실패한 항목만 조용히 빠지고, 캐시된 값이 있으면 그 값으로 자리를 지킵니다
        private suspend fun fetchCustomTimerDetail(
            serverId: Long,
            cached: CustomTimerEntity?,
        ): ServerCustomTimer? {
            val detailResult =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getCustomTimer(serverId) },
                    mapper = { dto: GetCustomTimerDetailDTO? ->
                        (dto ?: throw IllegalStateException("커스텀 타이머 상세가 없습니다")).toServerModel()
                    },
                )
            return when (detailResult) {
                is BaseResult.Success -> detailResult.data
                is BaseResult.Error -> cached?.let { ServerCustomTimer(serverId, it.name, it.steps) }
            }
        }

        override suspend fun getLocalCustomTimerList(): BaseResult<List<CustomTimer>> {
            return BaseResult.Success(localDataSource.getCustomTimers().map { it.toModel() })
        }

        override suspend fun getCustomTimer(localId: String): BaseResult<CustomTimer> {
            val entity = localDataSource.getCustomTimer(localId) ?: return notFoundError(localId)
            return BaseResult.Success(entity.toModel())
        }

        override suspend fun createCustomTimer(
            title: String,
            steps: List<Step>,
        ): BaseResult<String> {
            val localId =
                localDataSource.createCustomTimer(title, steps.toCachedSteps()) ?: return noMemberIdError()
            return BaseResult.Success(localId)
        }

        override suspend fun editCustomTimer(
            localId: String,
            title: String,
            steps: List<Step>,
        ): BaseResult<Unit> {
            localDataSource.updateCustomTimer(localId, title, steps.toCachedSteps())
            return BaseResult.Success(Unit)
        }

        override suspend fun deleteCustomTimer(localId: String): BaseResult<Unit> {
            localDataSource.deleteCustomTimer(localId)
            return BaseResult.Success(Unit)
        }

        private fun noMemberIdError(): BaseResult.Error = BaseResult.Error(errorCode = "NO_MEMBER_ID", message = "회원ID가 없어 저장할 수 없습니다")

        private fun notFoundError(localId: String): BaseResult.Error =
            BaseResult.Error(errorCode = "NOT_FOUND", message = "$localId 타이머를 찾을 수 없습니다")

        companion object {
            private const val NETWORK_ERROR_CODE = "NETWORK_ERROR"
        }
    }
