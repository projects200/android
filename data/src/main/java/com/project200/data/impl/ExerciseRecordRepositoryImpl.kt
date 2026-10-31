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
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
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

        // 사진 영속 저장(5/9) 전에는 사진이 있는 요청만 기존 온라인 경로를 사용합니다
        override suspend fun createExerciseRecord(input: ExerciseWriteInput): BaseResult<LocalExerciseSaveResult> {
            if (input.removedImageIds.isNotEmpty()) return invalidImagesError()
            if (input.addedImageUris.isEmpty()) {
                return localWrite { memberId -> localDataSource.createRecord(input, memberId) }
            }
            return write { memberId ->
                val result =
                    apiCallBuilder(
                        ioDispatcher,
                        {
                            requireCurrentMember(memberId)
                            remoteDataSource.postExerciseRecord(input.record.toPostExerciseDTO())
                        },
                        { dto: PostExerciseResponseDTO? ->
                            dto?.exerciseId ?: throw NoSuchElementException("운동 기록 생성 응답 데이터가 없습니다.")
                        },
                    )
                when (result) {
                    is BaseResult.Error -> result
                    is BaseResult.Success -> {
                        var id = result.data.toString()
                        val error =
                            cacheAfterWrite(memberId) {
                                id = localDataSource.reconcileRecord(result.data, input.record, memberId, newRecord = true) ?: id
                            }
                        BaseResult.Success(LocalExerciseSaveResult(id), error)
                    }
                }
            }
        }

        override suspend fun updateExerciseRecord(
            input: ExerciseWriteInput,
            recordId: String,
        ): BaseResult<LocalExerciseSaveResult> {
            if (input.removedImageIds.any { it <= 0 }) return invalidImagesError()
            if (input.addedImageUris.isEmpty() && input.removedImageIds.isEmpty()) {
                return localWrite { memberId ->
                    val id = requireNotNull(localDataSource.resolveExistingLocalId(recordId, memberId)) { "RECORD_NOT_FOUND" }
                    localDataSource.updateRecord(id, input, memberId)
                }
            }
            return write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write photoNotReadyError()
                if (localDataSource.hasPendingServerRecord(id, memberId)) return@write photoNotReadyError()
                val previous = localDataSource.getDetailByServerId(id, memberId)
                val record = input.record
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
                            cacheAfterOnlineWrite(memberId, id) {
                                // 구경로 응답에는 새 version·확정 날짜가 없으므로 이전 값을 새 ACK로 쓰지 않습니다
                                localDataSource.reconcileRecord(
                                    id,
                                    record.copy(pictures = record.pictures ?: previous?.pictures),
                                    memberId,
                                    bodyChanged = true,
                                )
                            }
                        BaseResult.Success(LocalExerciseSaveResult(recordId), error)
                    }
                }
            }
        }

        override suspend fun uploadExerciseRecordImages(
            recordId: String,
            images: List<String>,
        ): BaseResult<String> =
            write { memberId ->
                val id = serverId(recordId, memberId) ?: return@write photoNotReadyError()
                if (localDataSource.hasPendingServerRecord(id, memberId)) return@write photoNotReadyError()
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
                            cacheAfterOnlineWrite(memberId, id) {
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
                if (imageIds.isEmpty() || imageIds.any { it <= 0 }) return@write invalidImagesError()
                val id = serverId(recordId, memberId) ?: return@write photoNotReadyError()
                if (localDataSource.hasPendingServerRecord(id, memberId)) return@write photoNotReadyError()
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
                    cacheAfterOnlineWrite(memberId, id) {
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
            localWrite { memberId ->
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

        private suspend fun cacheAfterOnlineWrite(
            memberId: String,
            serverId: Long,
            block: suspend () -> Unit,
        ): BaseResult.Error? =
            cacheAfterWrite(memberId) {
                if (localDataSource.hasPendingServerRecord(serverId, memberId)) {
                    localDataSource.markOnlineConflict(serverId, memberId)
                    throw IllegalStateException("ONLINE_WRITE_CONFLICT")
                }
                block()
            }

        private suspend fun <T> localWrite(block: suspend (String) -> T): BaseResult<T> =
            withContext(ioDispatcher) {
                val memberId = writeMemberId()
                stateMutex.withLock {
                    if (memberId == null || !localDataSource.isCurrentMember(memberId)) return@withLock accountChangedError()
                    generation++
                    try {
                        val result = block(memberId)
                        if (localDataSource.isCurrentMember(memberId)) BaseResult.Success(result) else accountChangedError()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        cacheError(e)
                    } finally {
                        generation++
                    }
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

        private fun localOnlyError() = BaseResult.Error("LOCAL_ONLY_RECORD", "저장된 운동 기록의 상세 정보를 불러올 수 없습니다.")

        private fun recordDeletedError() = BaseResult.Error("RECORD_DELETED", "삭제한 운동 기록입니다.")

        private fun photoNotReadyError() = BaseResult.Error("PHOTO_NOT_READY", "동기화 대기 중인 기록의 사진은 변경할 수 없습니다.")

        private fun invalidImagesError() = BaseResult.Error("INVALID_IMAGE_IDS", "삭제할 사진의 서버 ID가 올바르지 않습니다.")

        private fun accountChangedError() = BaseResult.Error("ACCOUNT_CHANGED", "계정이 변경되어 기록을 불러올 수 없습니다.")

        private fun cacheError(cause: Throwable? = null) = BaseResult.Error("CACHE_ERROR", "저장된 운동 기록을 불러오는데 실패했습니다.", cause)
    }
