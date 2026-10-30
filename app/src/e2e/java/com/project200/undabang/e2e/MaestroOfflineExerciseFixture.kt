package com.project200.undabang.e2e

import androidx.room.withTransaction
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.SyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** 관찰은 Room과 독립 GET만 사용하며 저장·Worker 실행·캐시 갱신을 하지 않습니다. */
internal class MaestroOfflineExerciseFixture
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val preferences: PreferenceManager,
        private val api: ApiService,
    ) {
        suspend fun baseline(
            memberId: String,
            run: MaestroOfflineRun,
        ): JSONArray =
            database.withTransaction {
                verifyMember(memberId)
                JSONArray(rows(memberId, run).map { it.localId })
            }

        suspend fun observeLocal(
            memberId: String,
            run: MaestroOfflineRun,
            saved: JSONObject,
            stage: String,
        ) {
            val state =
                when (stage) {
                    "exercise-local-created", "exercise-local-created-relaunch" -> SyncState.CREATE_PENDING
                    "exercise-local-updated", "exercise-local-updated-relaunch" -> SyncState.UPDATE_PENDING
                    "exercise-local-deleted", "exercise-local-deleted-relaunch" -> SyncState.DELETE_PENDING
                    else -> throw MaestroSetupFailure("invalid_request")
                }
            database.withTransaction {
                verifyMember(memberId)
                val owned = rows(memberId, run).filter { it.title in run.exerciseTitles }
                val baseline = saved.getJSONArray("baselineExerciseLocalIds")
                val baselineIds = (0 until baseline.length()).map { baseline.getString(it) }.toSet()
                val item = owned.singleOrNull() ?: failLocal()
                if (ownedLocalIds(memberId, run).any { it != item.localId }) failIdentity()
                if (item.localId in baselineIds) failLocal()
                if (saved.has(LOCAL_ID)) {
                    if (saved.getString(LOCAL_ID) != item.localId) failIdentity()
                } else if (stage != "exercise-local-created") {
                    failIdentity()
                }
                val serverId = if (state == SyncState.CREATE_PENDING) null else requiredServerId(saved)
                if (!localMatches(memberId, run, item.localId, serverId, state)) failLocal()
                saved.put(LOCAL_ID, item.localId)
                verifyMember(memberId)
            }
        }

        suspend fun observeServer(
            memberId: String,
            run: MaestroOfflineRun,
            saved: JSONObject,
            stage: String,
        ) {
            if (stage !in SERVER_STAGES) throw MaestroSetupFailure("invalid_request")
            val localId = saved.optString(LOCAL_ID).takeIf { it.isNotBlank() } ?: failIdentity()
            val expectedId =
                if (stage == "exercise-created-synced" && !saved.has(SERVER_ID)) null else requiredServerId(saved)
            val observed =
                withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    var matched = false
                    while (!matched) {
                        verifyMember(memberId)
                        val records = records(run)
                        val owned = records.filter { it.exerciseTitle in run.exerciseTitles }
                        if (owned.size > 1) throw MaestroSetupFailure("offline_exercise_server_mismatch")
                        val deleted = stage == "exercise-deleted-synced"
                        val record = owned.singleOrNull()
                        val serverMatches =
                            if (deleted) {
                                owned.isEmpty() && records.none { it.exerciseId == expectedId }
                            } else if (record == null) {
                                false
                            } else {
                                if (expectedId != null && record.exerciseId != expectedId) failIdentity()
                                val title = if (stage == "exercise-created-synced") run.exerciseTitle else run.editedExerciseTitle
                                val startedAt = if (stage == "exercise-created-synced") run.startedAt else run.editedStartedAt
                                val detail = read(api.getExerciseRecordDetail(record.exerciseId))
                                record.exerciseTitle == title && record.exerciseStartedAt == startedAt &&
                                    record.exerciseEndedAt == run.endedAt && detail.exerciseTitle == title &&
                                    detail.exerciseStartedAt == startedAt && detail.exerciseEndedAt == run.endedAt
                            }
                        val serverId = expectedId ?: record?.exerciseId
                        val localMatches =
                            database.withTransaction {
                                verifyMember(memberId)
                                if (ownedLocalIds(memberId, run).any { it != localId }) failIdentity()
                                val matches =
                                    if (deleted) {
                                        val dao = database.exerciseRecordDao()
                                        val deletedServerId = checkNotNull(expectedId)
                                        dao.getListItem(memberId, localId) == null &&
                                            dao.getDetailByLocalId(memberId, localId) == null &&
                                            database.exerciseSyncDao().get(memberId, localId) == null &&
                                            database.exerciseImageDao().getByRecord(memberId, localId).isEmpty() &&
                                            dao.findListItemLocalId(memberId, deletedServerId) == null &&
                                            dao.findDetailLocalId(memberId, deletedServerId) == null &&
                                            database.exerciseSyncDao().findLocalId(memberId, deletedServerId) == null
                                    } else if (serverId == null) {
                                        false
                                    } else {
                                        localMatches(memberId, run, localId, serverId, SyncState.SYNCED, stage)
                                    }
                                verifyMember(memberId)
                                matches
                            }
                        matched = serverMatches && localMatches
                        if (matched && !deleted) saved.put(SERVER_ID, checkNotNull(serverId))
                        if (!matched) delay(POLL_INTERVAL_MS)
                    }
                    true
                }
            if (observed != true) throw MaestroSetupFailure("offline_upload_not_observed")
        }

        /** 한 행이라도 전송·사진·복구 상태가 불확실하면 서버 정리와 앱 데이터 제거를 막습니다. */
        suspend fun requireSafeCleanup(
            memberId: String,
            run: MaestroOfflineRun,
            saved: JSONObject?,
            serverId: Long? = null,
        ) {
            database.withTransaction {
                verifyMember(memberId)
                val dao = database.exerciseRecordDao()
                val ids = ownedLocalIds(memberId, run).toMutableSet()
                val savedLocalId = saved?.optString(LOCAL_ID)?.takeIf { it.isNotBlank() }
                val savedServerId = saved?.takeIf { it.has(SERVER_ID) }?.getLong(SERVER_ID)
                if (
                    (savedLocalId != null && ids.any { it != savedLocalId }) ||
                    (serverId != null && savedServerId != null && serverId != savedServerId)
                ) {
                    throw MaestroSetupFailure("offline_exercise_cleanup_unsafe")
                }
                saved?.optString(LOCAL_ID)?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
                val serverIds = listOfNotNull(serverId, saved?.takeIf { it.has(SERVER_ID) }?.getLong(SERVER_ID))
                for (id in serverIds) {
                    dao.findListItemLocalId(memberId, id)?.let { ids.add(it) }
                    dao.findDetailLocalId(memberId, id)?.let { ids.add(it) }
                    database.exerciseSyncDao().findLocalId(memberId, id)?.let { ids.add(it) }
                }
                for (id in ids) {
                    val item = dao.getListItem(memberId, id)
                    val detail = dao.getDetailByLocalId(memberId, id)
                    if (
                        (item != null && (item.serverId == null || item.date != run.startedAt.toLocalDate())) ||
                        (detail != null && (detail.serverId == null || detail.startedAt.toLocalDate() != run.startedAt.toLocalDate())) ||
                        (item != null && detail != null && item.serverId != detail.serverId)
                    ) {
                        throw MaestroSetupFailure("offline_exercise_cleanup_unsafe")
                    }
                    if (
                        savedServerId != null &&
                        (item?.serverId?.let { it != savedServerId } == true || detail?.serverId?.let { it != savedServerId } == true)
                    ) {
                        throw MaestroSetupFailure("offline_exercise_cleanup_unsafe")
                    }
                    if (
                        (item != null && item.title !in run.exerciseTitles) ||
                        (detail != null && detail.title !in run.exerciseTitles) || dao.isProtected(memberId, id)
                    ) {
                        throw MaestroSetupFailure("offline_exercise_cleanup_unsafe")
                    }
                }
                verifyMember(memberId)
            }
        }

        private suspend fun localMatches(
            memberId: String,
            run: MaestroOfflineRun,
            localId: String,
            serverId: Long?,
            state: SyncState,
            stage: String = "",
        ): Boolean {
            val dao = database.exerciseRecordDao()
            val item = dao.getListItem(memberId, localId) ?: return false
            val detail = dao.getDetailByLocalId(memberId, localId) ?: return false
            val journal = database.exerciseSyncDao().get(memberId, localId)
            val edited = state == SyncState.UPDATE_PENDING || state == SyncState.DELETE_PENDING || stage == "exercise-updated-synced"
            val title = if (edited) run.editedExerciseTitle else run.exerciseTitle
            val startedAt = if (edited) run.editedStartedAt else run.startedAt
            if (item.serverId != serverId || detail.serverId != serverId) {
                // 미완료 생성 ACK는 기다리되 이미 확인한 서버 ID 교체는 허용하지 않습니다.
                if (item.serverId != null && item.serverId != serverId || detail.serverId != null && detail.serverId != serverId) {
                    failIdentity()
                }
                return false
            }
            if (
                item.date != run.startedAt.toLocalDate() || item.title != title || detail.title != title ||
                item.startedAt != startedAt || detail.startedAt != startedAt ||
                item.endedAt != run.endedAt || detail.endedAt != run.endedAt ||
                item.syncState != state || detail.syncState != state
            ) {
                return false
            }
            val images = database.exerciseImageDao().getByRecord(memberId, localId)
            if (images.isNotEmpty() || !detail.pictures.isNullOrEmpty()) return false
            if (state == SyncState.SYNCED) {
                return journal == null && !dao.isProtected(memberId, localId) &&
                    item.serverVersion != null && item.serverVersion == detail.serverVersion &&
                    item.confirmedDate == item.date && detail.confirmedDate == item.date
            }
            return journal != null && journal.serverId == serverId && journal.operation == state &&
                journal.revision != null && journal.revision > 0 && !journal.operationId.isNullOrBlank() &&
                (state != SyncState.CREATE_PENDING || !journal.createRequestId.isNullOrBlank()) && journal.currentDate == item.date &&
                journal.recoveryState == ExerciseRecoveryState.READY && journal.lastError == null &&
                journal.inFlightOperation == null && journal.inFlightRevision == null &&
                journal.inFlightOperationId == null && journal.inFlightRequestId == null &&
                journal.inFlightBody == null && journal.inFlightBaseVersion == null
        }

        private suspend fun rows(
            memberId: String,
            run: MaestroOfflineRun,
        ): List<ExerciseListItemEntity> {
            val dao = database.exerciseRecordDao()
            val date = run.startedAt.toLocalDate()
            return (dao.getListByDate(memberId, date) + dao.getPendingInRange(memberId, date, date)).distinctBy { it.localId }
        }

        /** e2e 전용 읽기입니다. 날짜 밖·상세만 남은 불확실 행도 정리 후보에서 누락하지 않습니다. */
        private fun ownedLocalIds(
            memberId: String,
            run: MaestroOfflineRun,
        ): Set<String> =
            database.openHelper.readableDatabase.query(
                "SELECT localId FROM exercise_list_item WHERE memberId = ? AND title IN (?, ?) " +
                    "UNION SELECT localId FROM exercise_record_detail WHERE memberId = ? AND title IN (?, ?)",
                arrayOf(memberId, run.exerciseTitle, run.editedExerciseTitle, memberId, run.exerciseTitle, run.editedExerciseTitle),
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

        private suspend fun records(run: MaestroOfflineRun): List<GetExerciseRecordListDto> =
            read(api.getExerciseList(run.startedAt.toLocalDate())).also { records ->
                if (
                    records.any { it.exerciseId <= 0 || it.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate() } ||
                    records.map { it.exerciseId }.toSet().size != records.size
                ) {
                    throw MaestroSetupFailure("offline_exercise_server_mismatch")
                }
            }

        private fun verifyMember(memberId: String) {
            if (preferences.getMemberId() != memberId) throw MaestroSetupFailure("member_mismatch")
        }

        private fun requiredServerId(saved: JSONObject): Long =
            saved.optLong(SERVER_ID).takeIf { it > 0 } ?: failIdentity()

        private fun <T : Any> read(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("fixture_read_failed")
        }

        private fun failLocal(): Nothing = throw MaestroSetupFailure("offline_exercise_local_mismatch")

        private fun failIdentity(): Nothing = throw MaestroSetupFailure("offline_exercise_identity_changed")

        companion object {
            const val LOCAL_ID = "exerciseLocalId"
            const val SERVER_ID = "exerciseServerId"
            val LOCAL_STAGES =
                setOf(
                    "exercise-local-created",
                    "exercise-local-created-relaunch",
                    "exercise-local-updated",
                    "exercise-local-updated-relaunch",
                    "exercise-local-deleted",
                    "exercise-local-deleted-relaunch",
                )
            val SERVER_STAGES = setOf("exercise-created-synced", "exercise-updated-synced", "exercise-deleted-synced")
            private const val UPLOAD_TIMEOUT_MS = 120_000L
            private const val POLL_INTERVAL_MS = 2_000L
        }
    }
