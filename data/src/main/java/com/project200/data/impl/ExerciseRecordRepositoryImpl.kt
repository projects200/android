package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.datasource.PreparedExerciseImage
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import com.project200.domain.repository.ExerciseRecordRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class ExerciseRecordRepositoryImpl
    @Inject
    constructor(
        private val remoteDataSource: ExerciseRecordRemoteDataSource,
        private val localDataSource: ExerciseRecordLocalDataSource,
        private val networkMonitor: NetworkMonitor,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val imageFileStore: ExerciseImageFileStore,
    ) : ExerciseRecordRepository {
        // 네트워크는 상태 락 밖에서 기다리고, 쓰기 시작과 종료가 이전 조회를 무효화합니다
        private val stateMutex = Mutex()
        private val refreshMutex = Mutex()
        private var generation = 0L

        override suspend fun getExerciseCountByRange(
            startDate: LocalDate,
            endDate: LocalDate,
            refresh: Boolean,
            localOnly: Boolean,
        ): BaseResult<List<ExerciseCount>> =
            withContext(ioDispatcher) {
                cachedRead(
                    refresh = refresh,
                    localOnly = localOnly,
                    read = { localDataSource.getCountsSnapshot(startDate, endDate, it) },
                    save = { memberId, data -> localDataSource.replaceCountsByRange(startDate, endDate, data, memberId) },
                    remote = { memberId ->
                        apiCallBuilder(
                            ioDispatcher,
                            {
                                requireCurrentMember(memberId)
                                remoteDataSource.getExerciseCountsByRange(startDate, endDate)
                            },
                            { dto: List<GetExerciseCountByRangeDTO>? ->
                                dto?.map { it.toModel() } ?: throw NoSuchElementException("구간별 운동 횟수 조회 응답 데이터가 없습니다.")
                            },
                        )
                    },
                )
            }

        override suspend fun getExerciseRecordList(
            date: LocalDate,
            refresh: Boolean,
            localOnly: Boolean,
        ): BaseResult<List<ExerciseListItem>> =
            withContext(ioDispatcher) {
                cachedRead(
                    refresh = refresh,
                    localOnly = localOnly,
                    read = { localDataSource.getListSnapshot(date, it) },
                    save = { memberId, data -> localDataSource.replaceSyncedListByDate(date, data, memberId) },
                    remote = { memberId ->
                        apiCallBuilder(
                            ioDispatcher,
                            {
                                requireCurrentMember(memberId)
                                remoteDataSource.getExerciseList(date)
                            },
                            { dto: List<GetExerciseRecordListDto>? -> dto?.map { it.toModel() } ?: emptyList() },
                        )
                    },
                )
            }

        override suspend fun getExerciseDetail(
            recordId: String,
            refresh: Boolean,
            localOnly: Boolean,
        ): BaseResult<ExerciseRecord> =
            withContext(ioDispatcher) {
                val memberId = localDataSource.currentMemberId()
                if (!localDataSource.isCurrentMember(memberId)) return@withContext accountChangedError()
                val serverId = serverId(recordId, memberId)
                if (localDataSource.isDeleted(recordId, serverId, memberId)) return@withContext recordDeletedError()
                cachedRead(
                    refresh = refresh && serverId != null,
                    localOnly = localOnly || serverId == null,
                    owner = memberId,
                    read = {
                        localDataSource.getDetailByLocalId(recordId, it)
                            ?: serverId?.let { id -> localDataSource.getDetailByServerId(id, it) }
                    },
                    save = { owner, data ->
                        localDataSource.saveSyncedDetail(serverId!!, data, owner, recordId.takeIf { it.toLongOrNull() == null })
                    },
                    remote = { owner -> serverId?.let { remoteDetail(it, owner) } ?: localOnlyError() },
                    missError = { if (serverId == null) localOnlyError() else networkError() },
                )
            }

        private suspend fun <T> cachedRead(
            refresh: Boolean,
            localOnly: Boolean,
            read: suspend (String?) -> T?,
            save: suspend (String?, T) -> Unit,
            remote: suspend (String?) -> BaseResult<T>,
            owner: String? = null,
            missError: () -> BaseResult.Error = { networkError() },
        ): BaseResult<T> {
            val memberId = owner ?: localDataSource.currentMemberId()
            try {
                val initial =
                    stateMutex.withLock {
                        if (!localDataSource.isCurrentMember(memberId)) return@withLock accountChangedError()
                        localDataSource.prepareCache(memberId)
                        if (!localDataSource.isCurrentMember(memberId)) return@withLock accountChangedError()
                        val connected = networkMonitor.isCurrentlyConnected()
                        if (!refresh || !connected || localOnly) {
                            val snapshot = read(memberId)
                            if (!localDataSource.isCurrentMember(memberId)) return@withLock accountChangedError()
                            if (snapshot != null) return@withLock BaseResult.Success(snapshot, isCachedSnapshot = true)
                            if (!connected || localOnly) return@withLock missError()
                        }
                        null
                    }
                if (initial != null) return initial
                return refreshMutex.withLock refresh@{
                    val revision =
                        stateMutex.withLock revision@{
                            if (!localDataSource.isCurrentMember(memberId)) return@revision null
                            generation
                        } ?: return@refresh accountChangedError()
                    if (!localDataSource.isCurrentMember(memberId)) return@refresh accountChangedError()
                    val result = remote(memberId)
                    stateMutex.withLock apply@{
                        if (!localDataSource.isCurrentMember(memberId)) return@apply accountChangedError()
                        val stale = generation != revision
                        localDataSource.prepareCache(memberId)
                        if (result is BaseResult.Success && !stale) save(memberId, result.data)
                        val snapshot = read(memberId)
                        when {
                            !localDataSource.isCurrentMember(memberId) -> accountChangedError()
                            snapshot != null ->
                                BaseResult.Success(
                                    snapshot,
                                    refreshError = (result as? BaseResult.Error)?.takeUnless { stale },
                                    isCachedSnapshot = stale || result is BaseResult.Error,
                                )
                            stale -> cacheError()
                            result is BaseResult.Error -> result
                            else -> cacheError()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "운동 기록 캐시 조회 실패")
                return cacheError(e)
            }
        }

        override suspend fun createExerciseRecord(input: ExerciseWriteInput): BaseResult<LocalExerciseSaveResult> =
            imageWrite(input) { memberId, prepared -> localDataSource.createRecord(input, memberId, prepared) }

        override suspend fun updateExerciseRecord(
            input: ExerciseWriteInput,
            recordId: String,
        ): BaseResult<LocalExerciseSaveResult> =
            imageWrite(input, recordId) { memberId, prepared ->
                val id = requireNotNull(localDataSource.resolveExistingLocalId(recordId, memberId)) { "RECORD_NOT_FOUND" }
                localDataSource.updateRecord(id, input, memberId, prepared)
            }

        private suspend fun imageWrite(
            input: ExerciseWriteInput,
            recordId: String? = null,
            save: suspend (String, List<PreparedExerciseImage>) -> LocalExerciseSaveResult,
        ): BaseResult<LocalExerciseSaveResult> =
            localWrite(recordId) { memberId ->
                val prepared = imageFileStore.prepare(input.addedImageUris)
                try {
                    requireCurrentMember(memberId)
                    currentCoroutineContext().ensureActive()
                    save(memberId, prepared)
                } finally {
                    // Room 복귀 시 취소됐어도 실제 commit 여부를 확인합니다. 확인 실패 시 파일을 보존합니다.
                    withContext(NonCancellable) {
                        removeUnreferencedFiles(prepared.map { it.path })
                    }
                }
            }

        override suspend fun deleteExerciseRecord(recordId: String): BaseResult<Unit> =
            localWrite(recordId) { memberId ->
                val id = requireNotNull(localDataSource.resolveExistingLocalId(recordId, memberId)) { "RECORD_NOT_FOUND" }
                localDataSource.deleteRecord(id, memberId)
            }

        private suspend fun remoteDetail(
            serverId: Long,
            memberId: String?,
        ): BaseResult<ExerciseRecord> =
            apiCallBuilder(
                ioDispatcher,
                {
                    requireCurrentMember(memberId)
                    remoteDataSource.getExerciseRecordDetail(serverId)
                },
                { dto: GetExerciseRecordData? -> dto?.toModel() ?: throw NoSuchElementException("운동 상세 정보 응답 데이터가 없습니다.") },
            )

        private suspend fun serverId(
            recordId: String,
            memberId: String?,
        ): Long? =
            recordId.toLongOrNull()?.takeIf { it > 0 }
                ?: localDataSource.resolveServerId(recordId, memberId)

        private fun requireCurrentMember(memberId: String?) {
            check(localDataSource.isCurrentMember(memberId)) { "ACCOUNT_CHANGED" }
        }

        private fun writeMemberId(): String? =
            try {
                localDataSource.currentMemberId()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "운동 기록 쓰기 계정 조회 실패")
                null
            }

        private suspend fun <T> localWrite(
            recordId: String? = null,
            block: suspend (String) -> T,
        ): BaseResult<T> =
            withContext(ioDispatcher) {
                val memberId = writeMemberId()
                val sessionRevision = imageFileStore.sessionRevision
                stateMutex.withLock {
                    imageFileStore.withLock files@{
                        if (
                            memberId == null || !localDataSource.isCurrentMember(memberId) ||
                            sessionRevision != imageFileStore.sessionRevision
                        ) {
                            return@files accountChangedError()
                        }
                        generation++
                        var previousPaths = emptyList<String>()
                        try {
                            previousPaths = recordId?.let { localDataSource.imagePaths(it, memberId) }.orEmpty()
                            val result = block(memberId)
                            if (localDataSource.isCurrentMember(memberId)) BaseResult.Success(result) else accountChangedError()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            cacheError(e)
                        } finally {
                            withContext(NonCancellable) { removeUnreferencedFiles(previousPaths) }
                            generation++
                        }
                    }
                }
            }

        private suspend fun removeUnreferencedFiles(paths: List<String>) {
            paths.distinct().forEach { path ->
                try {
                    if (!localDataSource.isImagePathReferenced(path)) imageFileStore.deleteOwned(path)
                } catch (e: Exception) {
                    // DB 상태를 확인하지 못한 파일은 유실 대신 보존합니다. 원래 저장 오류를 가리지 않습니다.
                    Timber.w(e, "운동 사진 파일 정리 보류")
                }
            }
        }

        private fun networkError() = BaseResult.Error("NETWORK_ERROR", "네트워크 연결 오류가 발생했습니다.")

        private fun localOnlyError() = BaseResult.Error("LOCAL_ONLY_RECORD", "저장된 운동 기록의 상세 정보를 불러올 수 없습니다.")

        private fun recordDeletedError() = BaseResult.Error("RECORD_DELETED", "삭제한 운동 기록입니다.")

        private fun accountChangedError() = BaseResult.Error("ACCOUNT_CHANGED", "계정이 변경되어 기록을 불러올 수 없습니다.")

        private fun cacheError(cause: Throwable? = null) = BaseResult.Error("CACHE_ERROR", "저장된 운동 기록을 불러오는데 실패했습니다.", cause)
    }
