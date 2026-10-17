package com.project200.data.impl

import android.content.Context
import androidx.core.net.toUri
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.datasource.FeatureFlagDataSource
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
        private val featureFlagDataSource: FeatureFlagDataSource,
        private val networkMonitor: NetworkMonitor,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationContext private val context: Context,
    ) : ExerciseRecordRepository {
        // 갱신 응답이 자신의 쓰기 뒤에 반영되어 새 값을 되돌리지 않도록 요청과 반영을 함께 직렬화합니다.
        private val mutex = Mutex()

        override suspend fun getExerciseCountByRange(
            startDate: LocalDate,
            endDate: LocalDate,
            refresh: Boolean,
            localOnly: Boolean,
        ): BaseResult<List<ExerciseCount>> =
            serialized {
                cachedRead(
                    refresh = refresh,
                    localOnly = localOnly,
                    read = { localDataSource.getCountsSnapshot(startDate, endDate, it) },
                    save = { memberId, data -> localDataSource.replaceCountsByRange(startDate, endDate, data, memberId) },
                    remote = {
                        apiCallBuilder(
                            ioDispatcher,
                            { remoteDataSource.getExerciseCountsByRange(startDate, endDate) },
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
            serialized {
                cachedRead(
                    refresh = refresh,
                    localOnly = localOnly,
                    read = { localDataSource.getListSnapshot(date, it) },
                    save = { memberId, data -> localDataSource.replaceSyncedListByDate(date, data, memberId) },
                    remote = {
                        apiCallBuilder(
                            ioDispatcher,
                            { remoteDataSource.getExerciseList(date) },
                            { dto: List<GetExerciseRecordListDto>? -> dto?.map { it.toModel() } ?: emptyList() },
                        )
                    },
                )
            }

        override suspend fun getExerciseDetail(recordId: String, refresh: Boolean, localOnly: Boolean): BaseResult<ExerciseRecord> =
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = if (enabled) localDataSource.currentMemberId() else null
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
                    remote = { serverId?.let { remoteDetail(it) } ?: localOnlyError() },
                )
            }

        private suspend fun <T> cachedRead(
            refresh: Boolean,
            localOnly: Boolean,
            read: suspend (String?) -> T?,
            save: suspend (String?, T) -> Unit,
            remote: suspend () -> BaseResult<T>,
            owner: String? = null,
        ): BaseResult<T> {
            if (!featureFlagDataSource.isOfflineCacheEnabled()) return remote()
            val memberId = owner ?: localDataSource.currentMemberId()
            if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
            val connected = networkMonitor.isCurrentlyConnected()
            try {
                localDataSource.prepareCache(memberId)
                if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
                if (!refresh || !connected || localOnly) {
                    val snapshot = read(memberId)
                    if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
                    if (snapshot != null) return BaseResult.Success(snapshot, isCachedSnapshot = true)
                    if (!connected || localOnly) return networkError()
                }
                val result = remote()
                if (!localDataSource.isCurrentMember(memberId)) return accountChangedError()
                return when (result) {
                    is BaseResult.Success -> {
                        save(memberId, result.data)
                        val snapshot = read(memberId)
                        if (!localDataSource.isCurrentMember(memberId)) {
                            accountChangedError()
                        } else {
                            snapshot?.let { BaseResult.Success(it) } ?: cacheError()
                        }
                    }
                    is BaseResult.Error -> {
                        val snapshot = read(memberId)
                        if (!localDataSource.isCurrentMember(memberId)) {
                            accountChangedError()
                        } else if (snapshot == null) {
                            result
                        } else {
                            BaseResult.Success(snapshot, refreshError = result, isCachedSnapshot = true)
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
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = writeMemberId()
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        { remoteDataSource.postExerciseRecord(record.toPostExerciseDTO()) },
                        { dto: PostExerciseResponseDTO? -> dto?.toModel() ?: throw NoSuchElementException("운동 기록 생성 응답 데이터가 없습니다.") },
                    )
                if (result !is BaseResult.Success) return@serialized result
                var id = result.data.recordId
                val error = cacheAfterWrite(memberId, enabled) {
                    id = localDataSource.reconcileRecord(id.toLong(), record, memberId) ?: id
                }
                BaseResult.Success(result.data.copy(recordId = id), error)
            }

        override suspend fun updateExerciseRecord(record: ExerciseRecord, recordId: String): BaseResult<String> =
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = writeMemberId()
                val id = serverId(recordId, memberId) ?: return@serialized localOnlyError()
                val previous = if (enabled) localDataSource.getDetailByServerId(id, memberId) else null
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        { remoteDataSource.patchExerciseRecord(id, record.toPatchExerciseDTO()) },
                        { dto: ExerciseIdDto? -> dto?.exerciseId ?: throw NoSuchElementException("운동 기록 수정 응답 데이터가 없습니다.") },
                    )
                when (result) {
                    is BaseResult.Error -> result
                    is BaseResult.Success -> {
                        val error = cacheAfterWrite(memberId, enabled) {
                            localDataSource.reconcileRecord(id, record.copy(pictures = record.pictures ?: previous?.pictures), memberId)
                        }
                        BaseResult.Success(recordId, error)
                    }
                }
            }

        override suspend fun uploadExerciseRecordImages(recordId: String, images: List<String>): BaseResult<String> =
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = writeMemberId()
                val id = serverId(recordId, memberId) ?: return@serialized localOnlyError()
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
                    return@serialized BaseResult.Error("CONVERSION_FAILED", "이미지 파일 변환에 실패했습니다.")
                }
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        { remoteDataSource.postExerciseImages(id, imageParts) },
                        { dto: ExerciseIdDto? -> dto?.exerciseId ?: throw NoSuchElementException("이미지 업로드 응답 데이터가 없습니다.") },
                    )
                when (result) {
                    is BaseResult.Error -> result
                    is BaseResult.Success -> {
                        val error = cacheAfterWrite(memberId, enabled) {
                            when (val detail = remoteDetail(id)) {
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

        override suspend fun deleteExerciseRecordImages(recordId: String, imageIds: List<Long>): BaseResult<Unit> =
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = writeMemberId()
                val id = serverId(recordId, memberId) ?: return@serialized localOnlyError()
                val previous = if (enabled) localDataSource.getDetailByServerId(id, memberId) else null
                val result = apiCallBuilder(ioDispatcher, { remoteDataSource.deleteExerciseImages(id, imageIds) }, { Unit })
                if (result !is BaseResult.Success) return@serialized result
                val error = cacheAfterWrite(memberId, enabled) {
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
            serialized {
                val enabled = featureFlagDataSource.isOfflineCacheEnabled()
                val memberId = writeMemberId()
                val id = serverId(recordId, memberId) ?: return@serialized localOnlyError()
                val result = apiCallBuilder(ioDispatcher, { remoteDataSource.deleteExerciseRecord(id) }, { Unit })
                if (result !is BaseResult.Success) return@serialized result
                val error = cacheAfterWrite(memberId, enabled) { localDataSource.reconcileDeletedRecord(id, memberId) }
                BaseResult.Success(Unit, error)
            }

        private suspend fun remoteDetail(serverId: Long): BaseResult<ExerciseRecord> =
            apiCallBuilder(
                ioDispatcher,
                { remoteDataSource.getExerciseRecordDetail(serverId) },
                { dto: GetExerciseRecordData? -> dto?.toModel() ?: throw NoSuchElementException("운동 상세 정보 응답 데이터가 없습니다.") },
            )

        private suspend fun serverId(recordId: String, memberId: String?): Long? =
            recordId.toLongOrNull()?.takeIf { it > 0 }
                ?: localDataSource.resolveServerId(recordId, memberId ?: localDataSource.currentMemberId())

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

        private suspend fun cacheAfterWrite(memberId: String?, enabled: Boolean, block: suspend () -> Unit): BaseResult.Error? {
            if (!enabled) return invalidateAfterWrite(memberId)
            return try {
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

        private suspend fun <T> serialized(block: suspend () -> T): T = withContext(ioDispatcher) { mutex.withLock { block() } }

        private fun networkError() = BaseResult.Error("NETWORK_ERROR", "네트워크 연결 오류가 발생했습니다.")

        private fun localOnlyError() = BaseResult.Error("LOCAL_ONLY_RECORD", "동기화 대기 중인 기록은 서버에서 수정하거나 삭제할 수 없습니다.")

        private fun accountChangedError() = BaseResult.Error("ACCOUNT_CHANGED", "계정이 변경되어 기록을 불러올 수 없습니다.")

        private fun cacheError(cause: Throwable? = null) = BaseResult.Error("CACHE_ERROR", "저장된 운동 기록을 불러오는데 실패했습니다.", cause)
    }
