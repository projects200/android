package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.common.utils.NetworkMonitor
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.datasource.ExerciseRequestKind
import com.project200.data.datasource.ExerciseSyncLocalDataSource
import com.project200.data.datasource.ExerciseSyncSnapshot
import com.project200.data.datasource.PreparedExerciseImage
import com.project200.data.dto.ExercisePictureMappingDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.data.utils.ExerciseSyncAccount
import com.project200.data.utils.ExerciseSyncAccountGuard
import com.project200.data.utils.ExerciseSyncAuthenticationException
import com.project200.data.utils.apiCallBuilder
import com.project200.data.utils.idTokenSubject
import com.project200.domain.manager.ExerciseSyncScheduler
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseSyncResult
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import com.project200.domain.repository.ExerciseRecordRepository
import com.project200.undabang.oauth.AuthStateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
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
        private val syncLocalDataSource: ExerciseSyncLocalDataSource,
        private val syncAccountGuard: ExerciseSyncAccountGuard,
        private val syncScheduler: ExerciseSyncScheduler,
        private val apiService: ApiService,
        private val preferences: PreferenceManager,
        private val authStateManager: AuthStateManager,
    ) : ExerciseRecordRepository {
        // 네트워크는 상태 락 밖에서 기다리고, 쓰기 시작과 종료가 이전 조회를 무효화합니다
        private val stateMutex = Mutex()
        private val refreshMutex = Mutex()
        private val syncMutex = Mutex()
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

        private suspend fun <T> localWrite(recordId: String? = null, block: suspend (String) -> T): BaseResult<T> =
            withContext(ioDispatcher) {
                val memberId = writeMemberId()
                val sessionRevision = imageFileStore.sessionRevision
                val committed = stateMutex.withLock {
                    imageFileStore.withLock files@{
                        if (
                            memberId == null || !localDataSource.isCurrentMember(memberId) ||
                            sessionRevision != imageFileStore.sessionRevision
                        ) return@files accountChangedError()
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
                // 로컬 commit 뒤 예약 실패를 저장 실패로 돌려주면 사용자가 중복 생성하게 됩니다
                if (committed is BaseResult.Success) {
                    try {
                        syncScheduler.schedule()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "운동 기록 동기화 예약 보류")
                    }
                }
                committed
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

        override suspend fun syncPendingExercises(memberId: String): ExerciseSyncResult =
            withContext(ioDispatcher) {
                try {
                    syncMutex.withLock sync@{
                        bindSyncMemberSubject(memberId)
                        val account = syncAccountGuard.capture(memberId) ?: return@sync ExerciseSyncResult.Skipped
                        var retry = false
                        val ids = syncState(account) { syncLocalDataSource.pendingIds(memberId) }
                            ?: return@sync ExerciseSyncResult.Skipped
                        for (localId in ids) {
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val snapshot = syncState(account) { syncLocalDataSource.claim(memberId, localId) } ?: break
                                if (!syncAccountGuard.isCurrent(account)) return@sync ExerciseSyncResult.Skipped
                                val response = sendSnapshot(snapshot, account)
                                if (!syncAccountGuard.isCurrent(account)) return@sync ExerciseSyncResult.Skipped
                                when (response) {
                                    is BaseResult.Success -> {
                                        val applied = syncState(account, localId) {
                                            if (snapshot.kind == ExerciseRequestKind.BODY) {
                                                syncLocalDataSource.applyBody(snapshot, response.data.serverId, response.data.version, response.data.date)
                                            } else {
                                                syncLocalDataSource.applyPhoto(snapshot, response.data.version, response.data.picture)
                                            }
                                        }
                                        if (applied != true) break
                                    }
                                    is BaseResult.Error -> {
                                        val auth = isAuthenticationError(response)
                                        val transient = auth || isRetryable(response)
                                        val deletionReady = syncState(account) {
                                            syncLocalDataSource.fail(snapshot, transient, errorClass(response, auth, transient))
                                        }
                                        if (auth) return@sync ExerciseSyncResult.Skipped
                                        if (deletionReady == true) continue
                                        retry = retry || transient
                                        break
                                    }
                                }
                            }
                        }
                        if (!syncAccountGuard.isCurrent(account)) return@sync ExerciseSyncResult.Skipped
                        when {
                            retry -> ExerciseSyncResult.Retry
                            syncState(account) { syncLocalDataSource.hasRecovery(memberId) } == true -> ExerciseSyncResult.Failure
                            else -> ExerciseSyncResult.Success
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 트랜잭션/파일 확인 실패는 원본을 남기고 다음 진입에서 다시 시도합니다
                    ExerciseSyncResult.Retry
                }
            }

        private suspend fun bindSyncMemberSubject(memberId: String) {
            if (preferences.getMemberSubject() != null) return
            val revision = preferences.exerciseSessionRevision()
            val subjectBefore = idTokenSubject(authStateManager.getCurrent().idToken)
            // 기존 로그인도 서버 회원ID를 확인해야 이관된 계정을 subject와 연결할 수 있습니다
            val response = apiService.getIsRegistered()
            imageFileStore.withLock {
                currentCoroutineContext().ensureActive()
                val subjectAfter = idTokenSubject(authStateManager.getCurrent().idToken)
                if (
                    response.succeed && response.data?.isRegistered == true &&
                    response.data.memberId == memberId && preferences.getMemberId() == memberId &&
                    preferences.exerciseSessionRevision() == revision &&
                    preferences.getMemberSubject() == null &&
                    subjectBefore != null && subjectBefore == subjectAfter
                ) {
                    preferences.saveMemberId(memberId, subjectBefore)
                }
            }
        }

        private suspend fun <T> syncState(
            account: ExerciseSyncAccount,
            localId: String? = null,
            block: suspend () -> T,
        ): T? = stateMutex.withLock {
            imageFileStore.withLock files@{
                currentCoroutineContext().ensureActive()
                if (!syncAccountGuard.isCurrent(account)) return@files null
                generation++
                val paths = localId?.let { localDataSource.imagePaths(it, account.memberId) }.orEmpty()
                try {
                    block()
                } finally {
                    withContext(NonCancellable) { removeUnreferencedFiles(paths) }
                    generation++
                }
            }
        }

        private data class SyncAcknowledgment(
            val serverId: Long,
            val version: Long,
            val date: LocalDate? = null,
            val picture: ExercisePictureMappingDto? = null,
        )

        private suspend fun sendSnapshot(
            snapshot: ExerciseSyncSnapshot,
            account: ExerciseSyncAccount,
        ): BaseResult<SyncAcknowledgment> {
            val mutation = when (snapshot.kind) {
                ExerciseRequestKind.BODY -> when (snapshot.operation) {
                    SyncState.CREATE_PENDING -> syncCall(
                        { remoteDataSource.postExerciseRecord(snapshot.createRequest(), account) },
                        { dto -> requireNotNull(dto).let { acknowledgment(it.exerciseId, it.version) } },
                    )
                    SyncState.UPDATE_PENDING -> syncCall(
                        { remoteDataSource.patchExerciseRecord(requireNotNull(snapshot.serverId), snapshot.editRequest(), account) },
                        { dto ->
                            val ack = requireNotNull(dto).let { acknowledgment(it.exerciseId, it.version, snapshot.serverId) }
                            val baseVersion = requireNotNull(snapshot.baseVersion)
                            require(ack.version in baseVersion..baseVersion + 1)
                            ack
                        },
                    )
                    SyncState.DELETE_PENDING -> syncCall(
                        { remoteDataSource.deleteExerciseRecord(requireNotNull(snapshot.serverId), snapshot.requestId, account) },
                        { dto -> requireNotNull(dto).let { acknowledgment(it.exerciseId, it.version, snapshot.serverId) } },
                    )
                    SyncState.SYNCED -> error("INVALID_BODY_OPERATION")
                }
                ExerciseRequestKind.UPLOAD -> syncCall(
                    {
                        val file = File(requireNotNull(snapshot.filePath))
                        require(file.isFile && file.length() == snapshot.byteLength && ExerciseSyncLocalDataSource.hash(file) == snapshot.sha256)
                        val part = MultipartBody.Part.createFormData(
                            "pictures", snapshot.filename,
                            file.asRequestBody(requireNotNull(snapshot.mimeType).toMediaType()),
                        )
                        remoteDataSource.postExerciseImages(
                            requireNotNull(snapshot.serverId), listOf(part), snapshot.requestId,
                            listOf(requireNotNull(snapshot.imageId)), account,
                        )
                    },
                    { dto ->
                        val data = requireNotNull(dto)
                        val ack = acknowledgment(data.exerciseId, data.version, snapshot.serverId)
                        val picture = requireNotNull(data.pictures).single()
                        require(picture.clientImageId == snapshot.imageId && picture.pictureId > 0)
                        val uri = java.net.URI(picture.pictureUrl)
                        require((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank())
                        ack.copy(picture = picture)
                    },
                )
                ExerciseRequestKind.DELETE_PHOTO -> syncCall(
                    {
                        remoteDataSource.deleteExerciseImages(
                            requireNotNull(snapshot.serverId), listOf(requireNotNull(snapshot.pictureId)), snapshot.requestId, account,
                        )
                    },
                    { dto -> requireNotNull(dto).let { acknowledgment(it.exerciseId, it.version, snapshot.serverId) } },
                )
            }
            if (mutation is BaseResult.Error) {
                // 경로 404가 아니라 서버가 이름 붙인 부재/삭제만 원하는 전체 삭제를 확정합니다
                if (snapshot.kind == ExerciseRequestKind.BODY && snapshot.operation == SyncState.DELETE_PENDING &&
                    mutation.errorCode in setOf("EXERCISE_NOT_FOUND", "EXERCISE_DELETED")
                ) return BaseResult.Success(SyncAcknowledgment(requireNotNull(snapshot.serverId), requireNotNull(snapshot.baseVersion)))
                return mutation
            }
            mutation as BaseResult.Success
            if (snapshot.kind != ExerciseRequestKind.BODY || snapshot.operation == SyncState.DELETE_PENDING) return mutation
            if (!syncAccountGuard.isCurrent(account)) return accountChangedError()
            val confirmed = confirmDate(snapshot, mutation.data.serverId, account)
            return when (confirmed) {
                is BaseResult.Error -> confirmed
                is BaseResult.Success -> BaseResult.Success(mutation.data.copy(date = confirmed.data))
            }
        }

        private fun acknowledgment(id: Long, version: Long?, expectedId: Long? = null): SyncAcknowledgment {
            require(id > 0 && (expectedId == null || id == expectedId) && version != null && version >= 0)
            return SyncAcknowledgment(id, version)
        }

        private suspend fun confirmDate(
            snapshot: ExerciseSyncSnapshot,
            serverId: Long,
            account: ExerciseSyncAccount,
        ): BaseResult<LocalDate> {
            val hint = LocalDate.parse(requireNotNull(snapshot.submittedDate))
            suspend fun membership(date: LocalDate): BaseResult<Boolean> = syncCall(
                { remoteDataSource.getExerciseList(date, account) },
                { list -> requireNotNull(list).any { it.exerciseId == serverId } },
            )
            val first = membership(hint)
            if (first is BaseResult.Error) return first
            if ((first as BaseResult.Success).data) return BaseResult.Success(hint)
            if (!syncAccountGuard.isCurrent(account)) return accountChangedError()
            val detail = syncCall(
                { remoteDataSource.getExerciseRecordDetail(serverId, account) },
                { requireNotNull(it).exerciseStartedAt.toLocalDate() },
            )
            if (detail is BaseResult.Error) return detail
            val nextHint = (detail as BaseResult.Success).data
            if (nextHint != hint) {
                val second = membership(nextHint)
                if (second is BaseResult.Error) return second
                if ((second as BaseResult.Success).data) return BaseResult.Success(nextHint)
            }
            return BaseResult.Error("DATE_NOT_CONFIRMED", "서버 목록에서 운동 날짜를 확인하지 못했습니다.")
        }

        private suspend fun <DTO, T> syncCall(
            call: suspend () -> com.project200.data.dto.BaseResponse<DTO>,
            map: (DTO?) -> T,
        ): BaseResult<T> = try {
            val response = call()
            if (!response.succeed) {
                BaseResult.Error(response.code, "운동 기록 요청을 완료하지 못했습니다.")
            } else {
                try {
                    BaseResult.Success(map(response.data))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    BaseResult.Error("MAPPING_ERROR", "운동 기록 응답 계약이 일치하지 않습니다.", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val code = try {
                e.response()?.errorBody()?.string()?.let { org.json.JSONObject(it).optString("code").takeIf(String::isNotBlank) }
            } catch (_: Exception) {
                null
            }
            BaseResult.Error(code ?: e.code().toString(), "운동 기록 요청을 완료하지 못했습니다.", e)
        } catch (e: com.squareup.moshi.JsonDataException) {
            BaseResult.Error("MAPPING_ERROR", "운동 기록 응답 계약이 일치하지 않습니다.", e)
        } catch (e: com.squareup.moshi.JsonEncodingException) {
            BaseResult.Error("MAPPING_ERROR", "운동 기록 응답 계약이 일치하지 않습니다.", e)
        } catch (e: java.io.IOException) {
            BaseResult.Error("NETWORK_ERROR", "운동 기록 전송을 다시 시도합니다.", e)
        } catch (e: Exception) {
            BaseResult.Error("UNKNOWN_ERROR", "운동 기록 요청을 확인하지 못했습니다.", e)
        }

        private fun isAuthenticationError(error: BaseResult.Error): Boolean =
            error.errorCode in setOf("AUTHENTICATION_FAILED", "AUTHORIZATION_DENIED", "401", "403", "ACCOUNT_CHANGED") ||
                error.cause is ExerciseSyncAuthenticationException || (error.cause as? HttpException)?.code() in setOf(401, 403)

        private fun isRetryable(error: BaseResult.Error): Boolean {
            if (error.cause is com.squareup.moshi.JsonDataException ||
                error.cause is com.squareup.moshi.JsonEncodingException || error.cause is IllegalArgumentException
            ) return false
            val status = (error.cause as? HttpException)?.code() ?: error.errorCode?.toIntOrNull()
            if (status == 429 || status != null && status >= 500) return true
            return error.errorCode in setOf("NETWORK_ERROR", "DATE_NOT_CONFIRMED", "UNKNOWN_ERROR")
        }

        private fun errorClass(error: BaseResult.Error, auth: Boolean, transient: Boolean): ExerciseSyncError = when {
            auth -> ExerciseSyncError.AUTHENTICATION_REQUIRED
            transient -> ExerciseSyncError.NETWORK_ERROR
            error.errorCode == "EXERCISE_REQUEST_ID_REUSED" -> ExerciseSyncError.REQUEST_ID_REUSED
            error.errorCode == "EXERCISE_EDIT_CONFLICT" -> ExerciseSyncError.CONFLICT
            error.errorCode in setOf("EXERCISE_NOT_FOUND", "EXERCISE_DELETED") -> ExerciseSyncError.REMOTE_DELETED
            error.errorCode == "MAPPING_ERROR" || error.cause is com.squareup.moshi.JsonDataException ||
                error.cause is com.squareup.moshi.JsonEncodingException || error.cause is IllegalArgumentException -> ExerciseSyncError.CONTRACT_ERROR
            else -> ExerciseSyncError.PERMANENT_ERROR
        }

        private fun networkError() = BaseResult.Error("NETWORK_ERROR", "네트워크 연결 오류가 발생했습니다.")

        private fun localOnlyError() = BaseResult.Error("LOCAL_ONLY_RECORD", "저장된 운동 기록의 상세 정보를 불러올 수 없습니다.")

        private fun recordDeletedError() = BaseResult.Error("RECORD_DELETED", "삭제한 운동 기록입니다.")

        private fun accountChangedError() = BaseResult.Error("ACCOUNT_CHANGED", "계정이 변경되어 기록을 불러올 수 없습니다.")

        private fun cacheError(cause: Throwable? = null) = BaseResult.Error("CACHE_ERROR", "저장된 운동 기록을 불러오는데 실패했습니다.", cause)
    }
