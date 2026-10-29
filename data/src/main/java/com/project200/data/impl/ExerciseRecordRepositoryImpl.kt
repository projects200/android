package com.project200.data.impl

import android.content.Context
import androidx.core.net.toUri
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toMultipartBodyPart
import com.project200.data.mapper.toPatchExerciseDTO
import com.project200.data.mapper.toPostExerciseDTO
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordCreationResult
import com.project200.domain.repository.ExerciseRecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
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
        @ApplicationContext private val context: Context,
    ) : ExerciseRecordRepository {
        // 네트워크는 상태 락 밖에서 기다리고, 쓰기 시작과 종료가 이전 조회를 무효화합니다
        private val stateMutex = Mutex()
        private val writeMutex = Mutex()
        private val refreshMutex = Mutex()
        private var generation = 0L
        private var activeWrites = 0

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
                )
            }

        private suspend fun <T> cachedRead(
            refresh: Boolean,
            localOnly: Boolean,
            read: suspend (String?) -> T?,
            save: suspend (String?, T) -> Unit,
            remote: suspend (String?) -> BaseResult<T>,
            owner: String? = null,
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
                            if (!connected || localOnly) return@withLock networkError()
                        }
                        null
                    }
                if (initial != null) return initial
                return refreshMutex.withLock refresh@{
                    val revision =
                        stateMutex.withLock revision@{
                            if (!localDataSource.isCurrentMember(memberId)) return@revision null
                            generation to (activeWrites > 0)
                        } ?: return@refresh accountChangedError()
                    if (!localDataSource.isCurrentMember(memberId)) return@refresh accountChangedError()
                    val result = remote(memberId)
                    stateMutex.withLock apply@{
                        if (!localDataSource.isCurrentMember(memberId)) return@apply accountChangedError()
                        val stale = generation != revision.first || revision.second || activeWrites > 0
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

        override suspend fun createExerciseRecord(record: ExerciseRecord): BaseResult<ExerciseRecordCreationResult> =
            write { memberId ->
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.postExerciseRecord(record.toPostExerciseDTO())
                        },
                        { dto: PostExerciseResponseDTO? -> dto?.toModel() ?: throw NoSuchElementException("운동 기록 생성 응답 데이터가 없습니다.") },
                    )
                if (result !is BaseResult.Success) return@write result
                var id = result.data.recordId
                val error =
                    cacheAfterWrite(memberId) {
                        id = localDataSource.reconcileRecord(id.toLong(), record, memberId) ?: id
                    }
                BaseResult.Success(result.data.copy(recordId = id), error)
            }

        override suspend fun updateExerciseRecord(
            record: ExerciseRecord,
            recordId: String,
        ): BaseResult<String> =
            write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write localOnlyError()
                val previous = localDataSource.getDetailByServerId(id, memberId)
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.patchExerciseRecord(id, record.toPatchExerciseDTO())
                        },
                        { dto: ExerciseIdDto? -> dto?.exerciseId ?: throw NoSuchElementException("운동 기록 수정 응답 데이터가 없습니다.") },
                    )
                when (result) {
                    is BaseResult.Error -> result
                    is BaseResult.Success -> {
                        val error =
                            cacheAfterWrite(memberId) {
                                localDataSource.reconcileRecord(id, record.copy(pictures = record.pictures ?: previous?.pictures), memberId)
                            }
                        BaseResult.Success(recordId, error)
                    }
                }
            }

        override suspend fun uploadExerciseRecordImages(
            recordId: String,
            images: List<String>,
        ): BaseResult<String> =
            write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write localOnlyError()
                val imageUris =
                    images.mapNotNull {
                        try {
                            it.toUri()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Invalid URI string: $it")
                            null
                        }
                    }
                val imageParts = imageUris.mapNotNull { it.toMultipartBodyPart(context, "pictures") }
                if (imageParts.isEmpty() && imageUris.isNotEmpty()) {
                    return@write BaseResult.Error("CONVERSION_FAILED", "이미지 파일 변환에 실패했습니다.")
                }
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.postExerciseImages(id, imageParts)
                        },
                        { dto: ExerciseIdDto? -> dto?.exerciseId ?: throw NoSuchElementException("이미지 업로드 응답 데이터가 없습니다.") },
                    )
                when (result) {
                    is BaseResult.Error -> result
                    is BaseResult.Success -> {
                        val detail = remoteDetail(id, memberId)
                        val error =
                            cacheAfterWrite(memberId) {
                                when (detail) {
                                    is BaseResult.Success -> localDataSource.reconcileRecord(id, detail.data, memberId)
                                    is BaseResult.Error -> {
                                        localDataSource.invalidateImages(id, memberId)
                                        throw IllegalStateException(detail.message, detail.cause)
                                    }
                                }
                            }
                        BaseResult.Success(recordId, error)
                    }
                }
            }

        override suspend fun deleteExerciseRecordImages(
            recordId: String,
            imageIds: List<Long>,
        ): BaseResult<Unit> =
            write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write localOnlyError()
                val previous = localDataSource.getDetailByServerId(id, memberId)
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.deleteExerciseImages(id, imageIds)
                        },
                        { Unit },
                    )
                if (result !is BaseResult.Success) return@write result
                val error =
                    cacheAfterWrite(memberId) {
                        if (previous != null) {
                            localDataSource.reconcileRecord(
                                id,
                                previous.copy(pictures = previous.pictures?.filterNot { it.id in imageIds }),
                                memberId,
                            )
                        } else {
                            localDataSource.invalidateImages(id, memberId)
                        }
                    }
                BaseResult.Success(Unit, error)
            }

        override suspend fun deleteExerciseRecord(recordId: String): BaseResult<Unit> =
            write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write localOnlyError()
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.deleteExerciseRecord(id)
                        },
                        { Unit },
                    )
                if (result !is BaseResult.Success) return@write result
                val error = cacheAfterWrite(memberId) { localDataSource.reconcileDeletedRecord(id, memberId) }
                BaseResult.Success(Unit, error)
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

        private fun invalidateAfterWrite(memberId: String?): BaseResult.Error? =
            try {
                memberId?.let { localDataSource.invalidateCache(it) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "운동 기록 캐시 무효 상태 저장 실패")
                cacheError(e)
            }

        private suspend fun cacheAfterWrite(
            memberId: String?,
            block: suspend () -> Unit,
        ): BaseResult.Error? =
            stateMutex.withLock {
                if (!localDataSource.isCurrentMember(memberId)) return@withLock accountChangedError()
                try {
                    block()
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    invalidateAfterWrite(memberId)
                    Timber.e(e, "서버 쓰기 후 운동 기록 캐시 반영 실패")
                    cacheError(e)
                }
            }

        private suspend fun <T> write(block: suspend (String) -> BaseResult<T>): BaseResult<T> =
            withContext(ioDispatcher) {
                writeMutex.withLock writer@{
                    val memberId = writeMemberId()
                    if (memberId == null || !localDataSource.isCurrentMember(memberId)) return@writer accountChangedError()
                    stateMutex.withLock {
                        generation++
                        activeWrites++
                    }
                    try {
                        if (!localDataSource.isCurrentMember(memberId)) return@writer accountChangedError()
                        val result = block(memberId)
                        if (localDataSource.isCurrentMember(memberId)) result else accountChangedError()
                    } finally {
                        withContext(NonCancellable) {
                            stateMutex.withLock {
                                generation++
                                activeWrites--
                            }
                        }
                    }
                }
            }

        private fun networkError() = BaseResult.Error("NETWORK_ERROR", "네트워크 연결 오류가 발생했습니다.")

        private fun localOnlyError() = BaseResult.Error("LOCAL_ONLY_RECORD", "동기화 대기 중인 기록은 서버에서 수정하거나 삭제할 수 없습니다.")

        private fun accountChangedError() = BaseResult.Error("ACCOUNT_CHANGED", "계정이 변경되어 기록을 불러올 수 없습니다.")

        private fun cacheError(cause: Throwable? = null) = BaseResult.Error("CACHE_ERROR", "저장된 운동 기록을 불러오는데 실패했습니다.", cause)
    }
