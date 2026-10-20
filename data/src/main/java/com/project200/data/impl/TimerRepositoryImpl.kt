package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.datasource.ServerCustomTimer
import com.project200.data.datasource.ServerSimpleTimer
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.datasource.TimerSyncSnapshot
import com.project200.data.dto.DeleteTimerRequestDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.PostCustomTimerRequest
import com.project200.data.dto.PostCustomTimerStepDTO
import com.project200.data.dto.SimpleTimerRequest
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toCachedSteps
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toServerModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.manager.TimerSyncScheduler
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step
import com.project200.domain.model.TimerSyncResult
import com.project200.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import javax.inject.Inject

class TimerRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        private val localDataSource: TimerLocalDataSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val timerSyncScheduler: TimerSyncScheduler,
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
            timerSyncScheduler.schedule()
            return BaseResult.Success(localId)
        }

        override suspend fun editSimpleTimer(
            localId: String,
            time: Int,
        ): BaseResult<Unit> {
            localDataSource.updateSimpleTimer(localId, time)
            timerSyncScheduler.schedule()
            return BaseResult.Success(Unit)
        }

        override suspend fun deleteSimpleTimer(localId: String): BaseResult<Unit> {
            localDataSource.deleteSimpleTimer(localId)
            timerSyncScheduler.schedule()
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
            timerSyncScheduler.schedule()
            return BaseResult.Success(localId)
        }

        override suspend fun editCustomTimer(
            localId: String,
            title: String,
            steps: List<Step>,
        ): BaseResult<Unit> {
            localDataSource.updateCustomTimer(localId, title, steps.toCachedSteps())
            timerSyncScheduler.schedule()
            return BaseResult.Success(Unit)
        }

        override suspend fun deleteCustomTimer(localId: String): BaseResult<Unit> {
            localDataSource.deleteCustomTimer(localId)
            timerSyncScheduler.schedule()
            return BaseResult.Success(Unit)
        }

        override suspend fun syncPendingTimers(memberId: String): TimerSyncResult {
            if (!localDataSource.isCurrentMember(memberId)) return TimerSyncResult.Skipped
            for (state in listOf(SyncState.CREATE_PENDING, SyncState.UPDATE_PENDING, SyncState.DELETE_PENDING)) {
                val simpleRows =
                    localDataSource.getPendingSimpleTimers()
                        .filter { (it.inFlightState ?: it.syncState) == state }
                for (row in simpleRows) {
                    val snapshot = localDataSource.claimSimpleTimer(memberId, row.localId) ?: continue
                    when (val result = syncSimple(memberId, snapshot)) {
                        TimerSyncResult.Success -> Unit
                        TimerSyncResult.Skipped -> return result
                        TimerSyncResult.Retry -> return result
                        TimerSyncResult.Failure -> return result
                    }
                }

                val customRows =
                    localDataSource.getPendingCustomTimers()
                        .filter { (it.inFlightState ?: it.syncState) == state }
                for (row in customRows) {
                    val snapshot = localDataSource.claimCustomTimer(memberId, row.localId) ?: continue
                    when (val result = syncCustom(memberId, snapshot)) {
                        TimerSyncResult.Success -> Unit
                        TimerSyncResult.Skipped -> return result
                        TimerSyncResult.Retry -> return result
                        TimerSyncResult.Failure -> return result
                    }
                }
            }
            return TimerSyncResult.Success
        }

        private suspend fun syncSimple(
            memberId: String,
            snapshot: TimerSyncSnapshot.Simple,
        ): TimerSyncResult {
            if (!localDataSource.isCurrentMember(memberId)) return TimerSyncResult.Skipped
            if (snapshot.serverId == null && snapshot.state == SyncState.CREATE_PENDING) {
                val result =
                    apiCallBuilder(
                        ioDispatcher = ioDispatcher,
                        apiCall = {
                            apiService.postSimpleTimer(
                                SimpleTimerRequest(
                                    time = snapshot.time,
                                    createRequestId = UUID.fromString(snapshot.createRequestId),
                                    editedAt = snapshot.editedAt,
                                ),
                            )
                        },
                        mapper = { it?.simpleTimerId ?: error("simple_timer_id_missing") },
                    )
                return when (result) {
                    is BaseResult.Success -> {
                        localDataSource.completeSimple(snapshot, result.data)
                        TimerSyncResult.Success
                    }
                    is BaseResult.Error -> classify(snapshot, result)
                }
            }

            val serverId = snapshot.serverId ?: return TimerSyncResult.Failure
            val result =
                when (snapshot.state) {
                    SyncState.UPDATE_PENDING ->
                        apiCallBuilder(
                            ioDispatcher = ioDispatcher,
                            apiCall = {
                                apiService.patchSimpleTimer(
                                    serverId,
                                    SimpleTimerRequest(
                                        time = snapshot.time,
                                        editId = snapshot.editId?.let(UUID::fromString),
                                        editedAt = snapshot.editedAt,
                                    ),
                                )
                            },
                            mapper = { it?.updatedAt },
                        )
                    SyncState.DELETE_PENDING ->
                        apiCallBuilder(
                            ioDispatcher = ioDispatcher,
                            apiCall = {
                                apiService.deleteSimpleTimer(
                                    serverId,
                                    DeleteTimerRequestDTO(
                                        editId = snapshot.editId?.let(UUID::fromString),
                                        editedAt = snapshot.editedAt,
                                    ),
                                )
                            },
                            mapper = { it?.updatedAt },
                        )
                    else -> return TimerSyncResult.Failure
                }
            return handleMutationResult(memberId, result, snapshot)
        }

        private suspend fun syncCustom(
            memberId: String,
            snapshot: TimerSyncSnapshot.Custom,
        ): TimerSyncResult {
            if (!localDataSource.isCurrentMember(memberId)) return TimerSyncResult.Skipped
            if (snapshot.serverId == null && snapshot.state == SyncState.CREATE_PENDING) {
                val result =
                    apiCallBuilder(
                        ioDispatcher = ioDispatcher,
                        apiCall = {
                            apiService.postCustomTimer(
                                PostCustomTimerRequest(
                                    customTimerName = snapshot.name,
                                    customTimerSteps =
                                        snapshot.steps.map {
                                            PostCustomTimerStepDTO(it.name, it.order, it.time)
                                        },
                                    createRequestId = UUID.fromString(snapshot.createRequestId),
                                    editedAt = snapshot.editedAt,
                                ),
                            )
                        },
                        mapper = { it?.customTimerId ?: error("custom_timer_id_missing") },
                    )
                return when (result) {
                    is BaseResult.Success -> {
                        localDataSource.completeCustom(snapshot, result.data)
                        TimerSyncResult.Success
                    }
                    is BaseResult.Error -> classify(snapshot, result)
                }
            }

            val serverId = snapshot.serverId ?: return TimerSyncResult.Failure
            val result =
                when (snapshot.state) {
                    SyncState.UPDATE_PENDING ->
                        apiCallBuilder(
                            ioDispatcher = ioDispatcher,
                            apiCall = {
                                apiService.putCustomTimer(
                                    serverId,
                                    PostCustomTimerRequest(
                                        customTimerName = snapshot.name,
                                        customTimerSteps =
                                            snapshot.steps.map {
                                                PostCustomTimerStepDTO(it.name, it.order, it.time)
                                            },
                                        editId = snapshot.editId?.let(UUID::fromString),
                                        editedAt = snapshot.editedAt,
                                    ),
                                )
                            },
                            mapper = { it?.updatedAt },
                        )
                    SyncState.DELETE_PENDING ->
                        apiCallBuilder(
                            ioDispatcher = ioDispatcher,
                            apiCall = {
                                apiService.deleteCustomTimer(
                                    serverId,
                                    DeleteTimerRequestDTO(
                                        editId = snapshot.editId?.let(UUID::fromString),
                                        editedAt = snapshot.editedAt,
                                    ),
                                )
                            },
                            mapper = { it?.updatedAt },
                        )
                    else -> return TimerSyncResult.Failure
                }
            return handleMutationResult(memberId, result, snapshot)
        }

        private suspend fun handleMutationResult(
            memberId: String,
            result: BaseResult<*>,
            snapshot: TimerSyncSnapshot,
        ): TimerSyncResult {
            if (!localDataSource.isCurrentMember(memberId)) return TimerSyncResult.Skipped
            return when (result) {
                is BaseResult.Success -> {
                    when (snapshot) {
                        is TimerSyncSnapshot.Simple -> localDataSource.completeSimple(snapshot, snapshot.serverId!!)
                        is TimerSyncSnapshot.Custom -> localDataSource.completeCustom(snapshot, snapshot.serverId!!)
                    }
                    TimerSyncResult.Success
                }
                is BaseResult.Error -> {
                    when {
                        result.errorCode == TIMER_EDIT_CONFLICT_CODE -> resolveConflict(memberId, snapshot)
                        result.errorCode == TIMER_DELETED_CODE -> {
                            when (snapshot) {
                                is TimerSyncSnapshot.Simple -> localDataSource.resolveSimpleConflict(snapshot, null)
                                is TimerSyncSnapshot.Custom -> localDataSource.resolveCustomConflict(snapshot, null)
                            }
                            TimerSyncResult.Success
                        }
                        else -> classify(snapshot, result)
                    }
                }
            }
        }

        private suspend fun resolveConflict(
            memberId: String,
            snapshot: TimerSyncSnapshot,
        ): TimerSyncResult {
            if (!localDataSource.isCurrentMember(memberId)) return TimerSyncResult.Skipped
            return when (snapshot) {
                is TimerSyncSnapshot.Simple -> {
                    when (val result = fetchSimpleServer(memberId, snapshot.serverId!!)) {
                        is BaseResult.Success -> {
                            localDataSource.resolveSimpleConflict(snapshot, result.data)
                            TimerSyncResult.Success
                        }
                        is BaseResult.Error -> classify(snapshot, result)
                    }
                }
                is TimerSyncSnapshot.Custom -> {
                    when (val result = fetchCustomServer(memberId, snapshot.serverId!!)) {
                        is BaseResult.Success -> {
                            localDataSource.resolveCustomConflict(snapshot, result.data)
                            TimerSyncResult.Success
                        }
                        is BaseResult.Error -> classify(snapshot, result)
                    }
                }
            }
        }

        private suspend fun fetchSimpleServer(
            memberId: String,
            serverId: Long,
        ): BaseResult<ServerSimpleTimer?> {
            if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getSimpleTimers() },
                mapper = { dto: GetSimpleTimersDTO? ->
                    dto?.simpleTimers?.firstOrNull { it.simpleTimerId == serverId }?.let {
                        ServerSimpleTimer(it.simpleTimerId, it.time)
                    }
                },
            )
        }

        private suspend fun fetchCustomServer(
            memberId: String,
            serverId: Long,
        ): BaseResult<ServerCustomTimer?> {
            if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
            val list =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getCustomTimerList() },
                    mapper = { dto: GetCustomTimerListDTO? ->
                        dto?.customTimers?.firstOrNull { it.customTimerId == serverId }
                    },
                )
            if (list is BaseResult.Error) return list
            if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
            if ((list as BaseResult.Success).data == null) return BaseResult.Success(null)
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getCustomTimer(serverId) },
                mapper = { dto: GetCustomTimerDetailDTO? ->
                    dto?.let {
                        ServerCustomTimer(
                            serverId = it.customTimerId,
                            name = it.customTimerName,
                            steps =
                                it.customTimerSteps.map { step ->
                                    CachedTimerStep(
                                        step.customTimerStepOrder,
                                        step.customTimerStepName,
                                        step.customTimerStepTime,
                                    )
                                },
                        )
                    }
                },
            )
        }

        private suspend fun classify(
            snapshot: TimerSyncSnapshot,
            error: BaseResult.Error,
        ): TimerSyncResult {
            if (error.errorCode == ACCOUNT_CHANGED_CODE) return TimerSyncResult.Skipped
            localDataSource.recordSyncError(snapshot, error.errorCode)
            return classify(error)
        }

        private fun accountChangedError(): BaseResult.Error = BaseResult.Error(errorCode = ACCOUNT_CHANGED_CODE, message = "회원 계정이 변경되었습니다")

        private fun classify(error: BaseResult.Error): TimerSyncResult =
            when (error.errorCode) {
                ACCOUNT_CHANGED_CODE -> TimerSyncResult.Skipped
                NETWORK_ERROR_CODE, UNKNOWN_ERROR_CODE -> TimerSyncResult.Retry
                else -> TimerSyncResult.Failure
            }

        private fun noMemberIdError(): BaseResult.Error = BaseResult.Error(errorCode = "NO_MEMBER_ID", message = "회원ID가 없어 저장할 수 없습니다")

        private fun notFoundError(localId: String): BaseResult.Error =
            BaseResult.Error(errorCode = "NOT_FOUND", message = "$localId 타이머를 찾을 수 없습니다")

        companion object {
            private const val ACCOUNT_CHANGED_CODE = "ACCOUNT_CHANGED"
            private const val NETWORK_ERROR_CODE = "NETWORK_ERROR"
            private const val UNKNOWN_ERROR_CODE = "UNKNOWN_ERROR"
            private const val TIMER_DELETED_CODE = "TIMER_DELETED"
            private const val TIMER_EDIT_CONFLICT_CODE = "TIMER_EDIT_CONFLICT"
        }
    }
