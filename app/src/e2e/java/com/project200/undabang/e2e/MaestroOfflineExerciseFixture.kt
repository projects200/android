package com.project200.undabang.e2e

import androidx.room.withTransaction
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.SyncState
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** e2e 전용 관찰입니다. API·저장소 갱신·Room 쓰기 없이 실제 로컬 생성과 취소만 검증합니다. */
internal class MaestroOfflineExerciseFixture
    @Inject
    constructor(
        private val database: UndabangDatabase,
        private val preferences: PreferenceManager,
    ) {
        suspend fun baseline(
            memberId: String,
            run: MaestroOfflineRun,
        ): JSONArray =
            database.withTransaction {
                verifyMember(memberId)
                // 날짜 밖·상세만 남은 같은 실행 제목도 기준 ID에 포함합니다.
                val dao = database.exerciseRecordDao()
                val date = run.startedAt.toLocalDate()
                val ids =
                    (dao.getListByDate(memberId, date) + dao.getPendingInRange(memberId, date, date))
                        .map { it.localId }.toSet() + ownedLocalIds(memberId, run)
                verifyMember(memberId)
                JSONArray(ids.toList())
            }

        suspend fun observeLocal(
            memberId: String,
            run: MaestroOfflineRun,
            saved: JSONObject,
            stage: String,
        ) {
            if (stage !in LOCAL_STAGES) throw MaestroSetupFailure("invalid_request")
            val localId =
                database.withTransaction {
                    verifyMember(memberId)
                    val owned = ownedLocalIds(memberId, run)
                    val captured = saved.optString(LOCAL_ID).takeIf { it.isNotBlank() }
                    val dao = database.exerciseRecordDao()
                    if (stage == "exercise-local-deleted") {
                        val id = captured ?: failIdentity()
                        if (
                            owned.isNotEmpty() || dao.getListItem(memberId, id) != null ||
                            dao.getDetailByLocalId(memberId, id) != null || database.exerciseSyncDao().get(memberId, id) != null ||
                            database.exerciseImageDao().getByRecord(memberId, id).isNotEmpty()
                        ) {
                            failLocal()
                        }
                        verifyMember(memberId)
                        return@withTransaction id
                    }
                    if (captured != null && owned.any { it != captured }) failIdentity()
                    val id = owned.singleOrNull()?.takeIf { it.isNotBlank() } ?: failLocal()
                    val baseline = saved.getJSONArray("baselineExerciseLocalIds")
                    if ((0 until baseline.length()).any { baseline.getString(it) == id }) failLocal()
                    if (captured != null && captured != id) failIdentity()
                    if (captured == null && stage != "exercise-local-created") failIdentity()
                    if (!localMatches(memberId, run, id)) failLocal()
                    verifyMember(memberId)
                    id
                }
            // 모든 읽기와 회원 검증을 통과한 원 ID만 private snapshot에 기록합니다.
            saved.put(LOCAL_ID, localId)
        }

        /** 미전송·불확실 행은 취소하지 않습니다. 서버 정리와 앱 데이터 제거를 먼저 막습니다. */
        suspend fun requireSafeCleanup(
            memberId: String,
            run: MaestroOfflineRun,
            saved: JSONObject?,
        ) {
            database.withTransaction {
                verifyMember(memberId)
                val ids = ownedLocalIds(memberId, run).toMutableSet()
                val captured = saved?.optString(LOCAL_ID)?.takeIf { it.isNotBlank() }
                if (captured != null && ids.any { it != captured }) failCleanup()
                if (captured != null) ids.add(captured)
                val dao = database.exerciseRecordDao()
                for (id in ids) {
                    val item = dao.getListItem(memberId, id)
                    val detail = dao.getDetailByLocalId(memberId, id)
                    if (
                        database.exerciseSyncDao().get(memberId, id) != null || dao.isProtected(memberId, id) ||
                        database.exerciseImageDao().getByRecord(memberId, id).isNotEmpty()
                    ) {
                        failCleanup()
                    }
                    if (
                        item != null &&
                        (
                            (item.serverId ?: 0) <= 0 || item.syncState != SyncState.SYNCED ||
                                item.title != run.exerciseTitle || item.date != run.startedAt.toLocalDate() ||
                                item.startedAt != run.startedAt || item.endedAt != run.endedAt
                        )
                    ) {
                        failCleanup()
                    }
                    if (
                        detail != null &&
                        (
                            (detail.serverId ?: 0) <= 0 || detail.syncState != SyncState.SYNCED ||
                                detail.title != run.exerciseTitle || detail.startedAt != run.startedAt || detail.endedAt != run.endedAt ||
                                !detail.pictures.isNullOrEmpty()
                        )
                    ) {
                        failCleanup()
                    }
                    if (item != null && detail != null && item.serverId != detail.serverId) failCleanup()
                }
                verifyMember(memberId)
            }
        }

        private suspend fun localMatches(
            memberId: String,
            run: MaestroOfflineRun,
            localId: String,
        ): Boolean {
            val dao = database.exerciseRecordDao()
            val item = dao.getListItem(memberId, localId) ?: return false
            val detail = dao.getDetailByLocalId(memberId, localId) ?: return false
            val journal = database.exerciseSyncDao().get(memberId, localId) ?: return false
            return item.serverId == null && detail.serverId == null && journal.serverId == null &&
                item.date == run.startedAt.toLocalDate() && item.title == run.exerciseTitle && detail.title == run.exerciseTitle &&
                item.startedAt == run.startedAt && detail.startedAt == run.startedAt &&
                item.endedAt == run.endedAt && detail.endedAt == run.endedAt &&
                item.syncState == SyncState.CREATE_PENDING && detail.syncState == SyncState.CREATE_PENDING &&
                journal.operation == SyncState.CREATE_PENDING && (journal.revision ?: 0) > 0 &&
                !journal.operationId.isNullOrBlank() && !journal.createRequestId.isNullOrBlank() &&
                journal.currentDate == item.date && journal.recoveryState == ExerciseRecoveryState.READY && journal.lastError == null &&
                item.serverVersion == null && detail.serverVersion == null && journal.serverVersion == null &&
                item.confirmedDate == null && detail.confirmedDate == null && journal.confirmedDate == null &&
                journal.inFlightOperation == null && journal.inFlightRevision == null && journal.inFlightOperationId == null &&
                journal.inFlightRequestId == null && journal.inFlightBody == null && journal.inFlightBaseVersion == null &&
                item.thumbnailUrls.isNullOrEmpty() && detail.pictures.isNullOrEmpty() &&
                database.exerciseImageDao().getByRecord(memberId, localId).isEmpty()
        }

        /** 날짜나 목록 표시 상태로 소유권 검색을 제한하면 orphan과 tombstone을 놓칩니다. */
        private fun ownedLocalIds(
            memberId: String,
            run: MaestroOfflineRun,
        ): Set<String> =
            database.openHelper.readableDatabase.query(
                "SELECT localId FROM exercise_list_item WHERE memberId = ? AND title = ? " +
                    "UNION SELECT localId FROM exercise_record_detail WHERE memberId = ? AND title = ?",
                arrayOf(memberId, run.exerciseTitle, memberId, run.exerciseTitle),
            ).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

        private fun verifyMember(memberId: String) {
            if (preferences.getMemberId() != memberId) throw MaestroSetupFailure("member_mismatch")
        }

        private fun failLocal(): Nothing = throw MaestroSetupFailure("offline_exercise_local_mismatch")

        private fun failIdentity(): Nothing = throw MaestroSetupFailure("offline_exercise_identity_changed")

        private fun failCleanup(): Nothing = throw MaestroSetupFailure("offline_exercise_cleanup_unsafe")

        companion object {
            private const val LOCAL_ID = "exerciseLocalId"
            val LOCAL_STAGES = setOf("exercise-local-created", "exercise-local-created-relaunch", "exercise-local-deleted")
        }
    }
