package com.project200.undabang.e2e

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.undabang.exercise.ExerciseSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/** UI owns all normal writes. This fixture only observes and mutates an explicitly frozen remote conflict/deletion. */
internal class MaestroOfflineExerciseCases
    @Inject
    constructor(
        private val api: ApiService,
        private val database: UndabangDatabase,
        @ApplicationContext private val context: Context,
        private val transport: MaestroOfflineTransport,
        private val preferences: PreferenceManager,
    ) {
        private val media = MaestroOfflinePhotoMedia(context)

        suspend fun prepare(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ): Map<String, String> {
            requireFlow(request)
            requireMember(request)
            if (saved.has(NAMESPACE)) fail("offline_snapshot_identity_invalid")
            saved.put(
                NAMESPACE,
                JSONObject()
                    .put("expectedInitialDescription", initialDescription(run))
                    .put("expectedEditedDescription", editedDescription(run))
                    .put("expectedRemoteDescription", remoteDescription(run)),
            )
            return media.prepare(run, saved) +
                mapOf(
                    "EXERCISE_DESCRIPTION" to initialDescription(run),
                    "EXERCISE_EDITED_DESCRIPTION" to editedDescription(run),
                    "EXERCISE_REMOTE_DESCRIPTION" to remoteDescription(run),
                )
        }

        suspend fun observe(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
            stage: String,
        ): Map<String, String> {
            requireFlow(request)
            if (stage !in LOCAL_STAGES && stage !in SERVER_STAGES) fail("invalid_request")
            val ext = extension(saved)
            when (stage) {
                "exercise-case-local-created" -> {
                    requireMember(request)
                    database.withTransaction {
                        val id = identify(request, run, saved)
                        requireBody(request, run, id, edited = false, remote = false, state = SyncState.CREATE_PENDING)
                        val journal = journal(request, id)
                        val editedAt = journal.desiredEditedAt
                        if (
                            journal.operation != SyncState.CREATE_PENDING || (journal.revision ?: 0) <= 0 ||
                            journal.operationId.isNullOrBlank() || journal.confirmedDate != null
                        ) {
                            fail("offline_exercise_local_mismatch")
                        }
                        if (
                            journal.serverId != null || journal.serverVersion != null || journal.createRequestId.isNullOrBlank() ||
                            editedAt == null || editedAt <= 0
                        ) {
                            fail("offline_exercise_local_mismatch")
                        }
                        requirePhotos(request, saved, id, "A", pending = true)
                        if (receipts(request, id).isNotEmpty()) fail("offline_points_premature")
                        ext.put("createRequestId", journal.createRequestId)
                        capture(request, saved, id, "firstPending")
                    }
                }
                "exercise-case-local-edited" -> {
                    requireMember(request)
                    val id = localId(saved)
                    database.withTransaction {
                        requireBody(request, run, id, edited = true, remote = false, state = SyncState.UPDATE_PENDING)
                        val journal = journal(request, id)
                        if (journal.operation != SyncState.UPDATE_PENDING || journal.serverId != serverId(saved)) {
                            fail("offline_exercise_local_mismatch")
                        }
                        val photoCase = request.flow == "offline-photo-create"
                        requirePhotos(request, saved, id, if (photoCase) "B" else "A", pending = photoCase)
                        if (photoCase) {
                            val removed = ext.optLong("photoAId", -1)
                            if (
                                removed <= 0 ||
                                database.exerciseImageDao().getByRecord(request.memberId, id).none {
                                    it.serverPictureId == removed && it.state == ExerciseImageState.DELETE_PENDING
                                }
                            ) {
                                fail("offline_photo_delete_intent_missing")
                            }
                        }
                        ext.put("localEditClock", journal.desiredEditedAt ?: fail("offline_local_clock_prerequisite"))
                        ext.put("localEditOperationId", journal.operationId ?: fail("offline_exercise_local_mismatch"))
                        ext.put("localEditVersion", journal.serverVersion ?: fail("offline_server_version_prerequisite"))
                        ext.put("localEditJournal", journal.toString())
                        capture(request, saved, id, "editedPending")
                    }
                    if (request.flow == "offline-conflict-local") {
                        MaestroOfflineExercisePolicy.requireOrdering(
                            ext.getLong("localEditClock"),
                            ext.getJSONObject("remoteMutation").getLong("editedAt"),
                            true,
                        )
                    }
                }
                "exercise-case-retained" -> assertRetained(request, run, saved)
                "exercise-case-baseline-synced" -> {
                    awaitMatch("offline_upload_not_observed") { complete(request, run, saved, initial = true) }
                    val images = database.exerciseImageDao().getByRecord(request.memberId, localId(saved))
                    ext.put("photoAId", images.single().serverPictureId ?: fail("offline_photo_mapping_mismatch"))
                    ext.put(
                        "baselineVersion",
                        database.exerciseRecordDao().getDetailByLocalId(request.memberId, localId(saved))?.serverVersion,
                    )
                    ext.put("baselineSynced", true)
                }
                "exercise-case-photos-pending" -> {
                    awaitMatch("offline_photo_pending_not_observed") { photosPending(request, run, saved) }
                    val proof = if (ext.has("editedPending")) "retainedBodyAckEdited" else "retainedBodyAckInitial"
                    database.withTransaction { capture(request, saved, localId(saved), proof) }
                }
                "exercise-case-complete" -> {
                    if (request.flow.startsWith("offline-conflict-") && !ext.has("remoteMutation")) fail("offline_conflict_not_prepared")
                    awaitMatch("offline_upload_not_observed") {
                        complete(request, run, saved, initial = request.flow.startsWith("offline-session-"))
                    }
                    ext.put("completed", true)
                }
                "exercise-case-retry-exhausted" -> retryExhausted(request, run, saved)
                "exercise-case-remote-deleted" -> {
                    if (request.flow != "offline-remote-delete" || !ext.optBoolean("remoteDeleted")) fail("invalid_request")
                    awaitMatch("offline_remote_delete_not_observed") {
                        requireMember(request)
                        val journal = database.exerciseSyncDao().get(request.memberId, localId(saved))
                        journal?.lastError == ExerciseSyncError.REMOTE_DELETED &&
                            journal.recoveryState == ExerciseRecoveryState.RECOVERY_REQUIRED
                    }
                    database.withTransaction {
                        requireBody(request, run, localId(saved), edited = true, remote = false, state = SyncState.UPDATE_PENDING)
                        requirePhotos(request, saved, localId(saved), "A", pending = false)
                        capture(request, saved, localId(saved), "remoteDeletedPending")
                    }
                }
                "exercise-case-discard-cancelled" -> {
                    if (!ext.has("remoteDeletedPending")) fail("offline_remote_delete_not_observed")
                    assertRetained(request, run, saved)
                    ext.put("discardCancelled", true)
                }
                "exercise-case-discarded" -> {
                    if (!ext.optBoolean("discardCancelled") || !ext.optBoolean("remoteDeleted")) {
                        fail("offline_discard_confirmation_not_observed")
                    }
                    assertErased(request, run, saved)
                    requireRemoteAbsent(request, run, saved)
                    ext.put("discarded", true)
                }
                "exercise-case-points-consumed", "exercise-case-points-stable" -> {
                    requireMember(request)
                    if (!ext.optBoolean("completed") && !ext.optBoolean("baselineSynced")) fail("offline_points_not_ready")
                    val receipt = receipts(request, localId(saved)).singleOrNull() ?: fail("offline_points_duplicate")
                    requireReceipt(request, saved, receipt.createRequestId, receipt.serverId, receipt.earnedPoints)
                    if (!receipt.noticeConsumed) fail("offline_points_consumption_not_observed")
                    if (stage.endsWith("points-stable")) {
                        if (ext.optString("consumedReceipt") != receipt.toString()) fail("offline_points_duplicate")
                    } else {
                        ext.put("consumedReceipt", receipt.toString())
                    }
                }
                "exercise-case-ui-deleted-synced" -> {
                    val session = saved.optJSONObject("session")
                    val approved =
                        session?.optBoolean("approvedErasure") == true && session.optBoolean("cleanBaseline") &&
                            ext.has("retainedBodyAckInitial")
                    if (!ext.optBoolean("completed") && !ext.optBoolean("baselineSynced") && !approved) fail("offline_cleanup_not_observed")
                    awaitMatch("offline_delete_ack_not_observed") {
                        requireMember(request)
                        if (!absent(request, saved)) false else remoteAbsent(request, run, saved)
                    }
                    assertErased(request, run, saved)
                    ext.put("uiDeleted", true)
                }
            }
            return emptyMap()
        }

        suspend fun control(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
            action: String,
        ): Map<String, String> {
            requireFlow(request)
            if (action !in CONTROL_ACTIONS) fail("invalid_request")
            val ext = extension(saved)
            when (action) {
                "media-delete-originals" -> media.deleteOriginals(run, saved, requireCopies = true)
                "block-body", "block-photos" -> {
                    requireMember(request)
                    val id = localId(saved)
                    if (action == "block-body" && !ext.optBoolean("baselineSynced")) fail("offline_baseline_not_observed")
                    if (action == "block-body") freezeRemote(request, run, saved)
                    if (request.flow == "offline-body-retry") {
                        awaitMatch("offline_retry_scheduler_not_idle") {
                            WorkManager.getInstance(context).getWorkInfosForUniqueWork(ExerciseSyncWorker.WORK_NAME).await().all {
                                it.state.isFinished
                            }
                        }
                        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ExerciseSyncWorker.WORK_NAME).await()
                        ext.put("baselineWorkIds", JSONArray(work.map { it.id.toString() }))
                    }
                    transport.arm(
                        request.memberId,
                        run.id,
                        id,
                        if (action == "block-body") MaestroOfflineTransport.Mode.BODY else MaestroOfflineTransport.Mode.PHOTOS,
                    )
                    ext.put("faultCleared", false)
                    ext.put("faultMode", if (action == "block-body") "BODY" else "PHOTOS")
                }
                "unblock-transport" -> {
                    // arm 전 준비 실패여도 호스트 복구 경로는 같은 실행 소유 fault만 확인해 해제합니다
                    transport.clear(request.memberId, run.id)
                    if (ext.has("faultMode")) ext.put("faultCleared", true)
                }
                "remote-update" -> remoteUpdate(request, run, saved)
                "remote-delete" -> remoteDelete(request, run, saved)
            }
            return if (action == "remote-update") mapOf("E2E_SERVER_CONTRACT" to "observed-version-edited-at") else emptyMap()
        }

        suspend fun assertRetained(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireFlow(request)
            database.withTransaction {
                val proof = extension(saved).optJSONObject("retained") ?: fail("offline_retention_proof_missing")
                val id = localId(saved)
                if (proof.getString("localId") != id || proof.getString("memberId") != request.memberId) {
                    fail("offline_exercise_identity_changed")
                }
                val dao = database.exerciseRecordDao()
                if (
                    dao.getListItem(request.memberId, id).toString() != proof.getString("item") ||
                    dao.getDetailByLocalId(request.memberId, id).toString() != proof.getString("detail") ||
                    receipts(request, id).toString() != proof.getString("receipts")
                ) {
                    fail("offline_retention_mismatch")
                }
                val journal = journal(request, id)
                if (MaestroOfflineExercisePolicy.journalIdentity(journal) != proof.getString("journalIdentity")) {
                    fail("offline_retention_mismatch")
                }
                val flight = proof.getJSONObject("inFlight")
                if (flight.isNull("body") && !journal.inFlightBody.isNullOrBlank()) {
                    val current = JSONObject(journal.inFlightBody!!)
                    val image =
                        database.exerciseImageDao().getByRecord(request.memberId, id).singleOrNull {
                            it.localId == current.optString("imageId") && it.requestId == current.optString("imageRequestId")
                        } ?: fail("offline_retry_snapshot_changed")
                    val files = proof.getJSONArray("files")
                    val file =
                        (0 until files.length()).map { files.getJSONObject(it) }.singleOrNull {
                            it.getString("localId") == image.localId
                        } ?: fail("offline_retry_snapshot_changed")
                    if (
                        current.optString("kind") != "UPLOAD" || current.optString("memberId") != request.memberId ||
                        current.optString("localId") != id || current.optString("requestId") != image.requestId ||
                        current.optString("filePath") != file.getString("path") ||
                        current.optString("sha256") != file.getString("sha256")
                    ) {
                        fail("offline_retry_snapshot_changed")
                    }
                }
                if (
                    !flight.isNull("body") &&
                    !MaestroOfflineExercisePolicy.sameObject(flight, MaestroOfflineExercisePolicy.inFlight(journal))
                ) {
                    fail("offline_retry_snapshot_changed")
                }
                val expectedImages = proof.getJSONArray("images")
                val actualImages = database.exerciseImageDao().getByRecord(request.memberId, id)
                if (actualImages.size != expectedImages.length()) fail("offline_photo_retention_mismatch")
                for (index in 0 until expectedImages.length()) {
                    val image = expectedImages.getJSONObject(index)
                    val actual =
                        actualImages.singleOrNull { it.localId == image.getString("localId") }
                            ?: fail("offline_photo_retention_mismatch")
                    MaestroOfflineExercisePolicy.requireImageRetention(actual, image)
                }
                media.assertFiles(proof.getJSONArray("files"))
            }
        }

        /** Session owner calls this before recording approval. It does not infer or grant approval. */
        suspend fun assertErased(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireFlow(request)
            if (!absent(request, saved)) fail("offline_exercise_erasure_not_observed")
            val ext = extension(saved)
            if (!ext.has("retained")) fail("offline_retention_proof_missing")
            for (key in PROOFS) ext.optJSONObject(key)?.let { media.assertFiles(it.getJSONArray("files"), erased = true) }
        }

        suspend fun requireSafeCleanup(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireFlow(request)
            val ext = extension(saved)
            val session = saved.optJSONObject("session")
            val approved = session?.optBoolean("approvedErasure") == true && session.optBoolean("cleanBaseline")
            if (!ext.optBoolean("uiDeleted") && !ext.optBoolean("discarded") && !approved) fail("offline_exercise_cleanup_unsafe")
            assertErased(request, run, saved)
            requireRemoteAbsent(request, run, saved)
            if (!ext.optBoolean("faultCleared") && ext.has("faultMode")) fail("offline_exercise_cleanup_unsafe")
            transport.assertClear()
        }

        suspend fun cleanup(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireSafeCleanup(request, run, saved)
            transport.finishTrace(request.memberId, run.id, localId(saved))
            extension(saved).put("traceFinished", true)
            media.deleteOriginals(run, saved, requireCopies = false)
            extension(saved).put("cleaned", true)
        }

        private suspend fun photosPending(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ): Boolean {
            requireMember(request)
            val id = localId(saved)
            val ext = extension(saved)
            if (ext.optString("faultMode") != "PHOTOS" || ext.optBoolean("faultCleared")) fail("offline_fault_not_owned")
            val detail = database.exerciseRecordDao().getDetailByLocalId(request.memberId, id) ?: return false
            val edited = ext.has("editedPending")
            val state = if (edited) SyncState.UPDATE_PENDING else SyncState.SYNCED
            val acknowledgedId = detail.serverId ?: return false
            if (detail.syncState != state) return false
            bindServer(saved, acknowledgedId)
            val journal = database.exerciseSyncDao().get(request.memberId, id) ?: return false
            if (journal.operation != state || journal.inFlightBody.isNullOrBlank()) return false
            val images = database.exerciseImageDao().getByRecord(request.memberId, id)
            if (images.none { it.state != ExerciseImageState.SYNCED }) return false
            val receipt = receipts(request, id).singleOrNull() ?: fail("offline_points_duplicate")
            requireBody(request, run, id, edited, remote = false, state)
            if (!edited && receipt.noticeConsumed) fail("offline_points_premature")
            if (edited && receipts(request, id).toString() != ext.getJSONObject("editedPending").getString("receipts")) {
                fail("offline_points_premature")
            }
            requireReceipt(request, saved, receipt.createRequestId, receipt.serverId, receipt.earnedPoints)
            val remote = read(api.getExerciseRecordDetail(acknowledgedId))
            // Pending remote-photo deletion precedes the edited body in the production claim order.
            if (!bodyMatches(remote, run, edited = false, remote = false, "", "")) return false
            if (remote.version != detail.serverVersion) return false
            val remoteIds = remote.pictureDataList.orEmpty().map { it.pictureId }
            if (!edited && remoteIds.isNotEmpty()) return false
            if (
                edited &&
                (
                    remoteIds != listOf(ext.getLong("photoAId")) ||
                        images.none {
                            it.serverPictureId == ext.getLong("photoAId") && it.state == ExerciseImageState.DELETE_PENDING
                        }
                )
            ) {
                return false
            }
            requirePhotos(request, saved, id, if (edited) "B" else "A", pending = true)
            ext.put("photoPendingObserved", true)
            return true
        }

        private suspend fun complete(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
            initial: Boolean,
        ): Boolean {
            requireMember(request)
            val id = localId(saved)
            val ext = extension(saved)
            val records = records(run)
            val owned = records.filter { it.exerciseTitle in run.exerciseTitles }
            if (owned.size > 1) fail("offline_exercise_server_duplicate")
            val summary = owned.singleOrNull() ?: return false
            bindServer(saved, summary.exerciseId)
            val remote = read(api.getExerciseRecordDetail(summary.exerciseId))
            val local = database.exerciseRecordDao().getDetailByLocalId(request.memberId, id) ?: return false
            val item = database.exerciseRecordDao().getListItem(request.memberId, id) ?: return false
            val remoteWins = !initial && request.flow == "offline-conflict-server"
            val edited = !initial && request.flow !in SESSION_FLOWS && !remoteWins
            val expectedType = ""
            val expectedLocation = ""
            if (!bodyMatches(remote, run, edited, remoteWins, expectedType, expectedLocation)) return false
            if (local.syncState != SyncState.SYNCED || item.syncState != SyncState.SYNCED) return false
            requireBody(request, run, id, edited, remoteWins, SyncState.SYNCED)
            if (database.exerciseSyncDao().get(request.memberId, id) != null) return false
            if (remote.version == null || summary.version == null) fail("offline_server_version_prerequisite")
            if (
                local.serverId != summary.exerciseId || item.serverId != summary.exerciseId ||
                local.serverVersion != remote.version || item.serverVersion != remote.version ||
                summary.version != remote.version || local.confirmedDate != item.date || item.confirmedDate != item.date
            ) {
                return false
            }
            val expectedTitle = if (edited) run.editedExerciseTitle else run.exerciseTitle
            val expectedStart = if (edited) run.editedStartedAt else run.startedAt
            if (
                summary.exerciseTitle != expectedTitle ||
                summary.exerciseStartedAt != expectedStart || summary.exerciseEndedAt != run.endedAt
            ) {
                return false
            }
            val label = if (!initial && request.flow == "offline-photo-create") "B" else "A"
            val images = database.exerciseImageDao().getByRecord(request.memberId, id)
            val pictures = remote.pictureDataList.orEmpty()
            if (images.size != 1 || pictures.size != 1 || images.any { it.state != ExerciseImageState.SYNCED }) return false
            val image = images.single()
            val picture = pictures.single()
            if (
                image.serverPictureId != picture.pictureId || image.serverUrl != picture.pictureUrl || picture.pictureId <= 0 ||
                pictures.map { it.pictureId }.toSet().size != pictures.size
            ) {
                fail("offline_photo_mapping_mismatch")
            }
            val copies = saved.getJSONObject("media").getJSONArray("copies")
            val copy =
                (0 until copies.length()).map { copies.getJSONObject(it) }.singleOrNull {
                    it.getString("label") == label && it.getString("localId") == image.localId
                } ?: fail("offline_photo_mapping_mismatch")
            media.assertFiles(JSONArray().put(copy))
            if (image.localFilePath != copy.getString("path") || image.requestId != copy.getString("requestId")) {
                fail("offline_photo_mapping_mismatch")
            }
            if (label == "B" && picture.pictureId == ext.optLong("photoAId", -1)) fail("offline_photo_delete_not_observed")
            media.assertRemote(picture.pictureUrl, label)
            if (request.flow.startsWith("offline-conflict-") && !initial) {
                val mutation = ext.getJSONObject("remoteMutation")
                MaestroOfflineExercisePolicy.requireOrdering(ext.getLong("localEditClock"), mutation.getLong("editedAt"), !remoteWins)
                val expectedClock = if (remoteWins) mutation.getLong("editedAt") else ext.getLong("localEditClock")
                val actualVersion = remote.version ?: fail("offline_server_version_prerequisite")
                if (
                    remote.editedAt != expectedClock ||
                    (if (remoteWins) actualVersion != mutation.getLong("version") else actualVersion <= mutation.getLong("version"))
                ) {
                    fail("offline_conflict_resolution_mismatch")
                }
                MaestroOfflineExercisePolicy.requireConflictTrace(
                    transport.bodyTrace(request.memberId, run.id, id),
                    ext.getString("localEditOperationId"),
                    ext.getLong("localEditVersion"),
                    ext.getLong("localEditClock"),
                    mutation.getLong("version"),
                    localWins = !remoteWins,
                )
            }
            if (request.flow == "offline-body-retry" && !initial) {
                if (!ext.has("failedWorkId") || !ext.has("firstAttempt") || !ext.optBoolean("faultCleared")) {
                    fail("offline_retry_not_observed")
                }
                val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ExerciseSyncWorker.WORK_NAME).await()
                if (
                    work.any { !it.state.isFinished } ||
                    work.none {
                        it.state == WorkInfo.State.SUCCEEDED && it.id.toString() != ext.getString("failedWorkId")
                    }
                ) {
                    return false
                }
                MaestroOfflineExercisePolicy.requireRetryTrace(
                    transport.bodyTrace(request.memberId, run.id, id),
                    ext.getJSONObject("firstAttempt"),
                    ext.getInt("retryTraceCount"),
                    completed = true,
                )
            }
            val receipt = receipts(request, id).singleOrNull() ?: fail("offline_points_duplicate")
            requireReceipt(request, saved, receipt.createRequestId, receipt.serverId, receipt.earnedPoints)
            ext.put("serverBodyVerified", true).put("serverPhotosVerified", true)
            return true
        }

        private suspend fun retryExhausted(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            if (request.flow != "offline-body-retry") fail("invalid_request")
            val ext = extension(saved)
            if (ext.optString("faultMode") != "BODY" || ext.optBoolean("faultCleared")) fail("offline_fault_not_owned")
            val id = localId(saved)
            awaitMatch("offline_retry_budget_not_exhausted", RETRY_TIMEOUT_MS) {
                requireMember(request)
                val journal = database.exerciseSyncDao().get(request.memberId, id) ?: fail("offline_retry_snapshot_missing")
                if (!journal.inFlightBody.isNullOrBlank()) {
                    if (!ext.has("firstAttempt")) ext.put("firstAttempt", MaestroOfflineExercisePolicy.inFlight(journal))
                    if (
                        !MaestroOfflineExercisePolicy.sameObject(
                            ext.getJSONObject("firstAttempt"),
                            MaestroOfflineExercisePolicy.inFlight(journal),
                        )
                    ) {
                        fail("offline_retry_snapshot_changed")
                    }
                }
                val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ExerciseSyncWorker.WORK_NAME).await()
                val baseline = ext.getJSONArray("baselineWorkIds")
                val baselineIds = (0 until baseline.length()).map { baseline.getString(it) }.toSet()
                val failed =
                    work.singleOrNull {
                        it.id.toString() !in baselineIds && it.state == WorkInfo.State.FAILED &&
                            it.runAttemptCount == ExerciseSyncWorker.MAX_ATTEMPTS
                    }
                val done =
                    failed != null && work.none { !it.state.isFinished } &&
                        journal.recoveryState == ExerciseRecoveryState.RETRYABLE && journal.lastError == ExerciseSyncError.NETWORK_ERROR
                if (done) {
                    MaestroOfflineExercisePolicy.requireRetrySnapshot(journal, ext.getJSONObject("firstAttempt"))
                    requireBody(request, run, id, edited = true, remote = false, state = SyncState.UPDATE_PENDING)
                    val remote = read(api.getExerciseRecordDetail(serverId(saved)))
                    if (
                        remote.exerciseDetail != initialDescription(run) ||
                        remote.version != ext.getJSONObject("remoteBefore").getLong("version")
                    ) {
                        fail("offline_retry_remote_changed")
                    }
                    val attempts = transport.bodyTrace(request.memberId, run.id, id)
                    if (attempts.size < ExerciseSyncWorker.MAX_ATTEMPTS) fail("offline_retry_request_trace_mismatch")
                    MaestroOfflineExercisePolicy.requireRetryTrace(
                        attempts,
                        ext.getJSONObject("firstAttempt"),
                        attempts.size,
                        completed = false,
                    )
                    ext.put("retryTraceCount", attempts.size)
                    ext.put("failedWorkId", failed!!.id.toString()).put("retryAttempts", failed.runAttemptCount)
                    capture(request, saved, id, "retryPending")
                }
                done
            }
        }

        private suspend fun remoteUpdate(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireMember(request)
            if (request.flow !in setOf("offline-conflict-local", "offline-conflict-server")) fail("invalid_request")
            val ext = extension(saved)
            if (!ext.optBoolean("baselineSynced") || ext.optString("faultMode") != "BODY" || ext.optBoolean("faultCleared")) {
                fail("offline_conflict_not_prepared")
            }
            if (ext.has("remoteMutation")) fail("offline_remote_mutation_uncertain")
            val before = read(api.getExerciseRecordDetail(serverId(saved)))
            val now = System.currentTimeMillis()
            val (version, clock) = MaestroOfflineExercisePolicy.requireClockContract(before, now)
            val baseline = ext.getJSONObject("remoteBefore")
            if (version != baseline.getLong("version") || before.exerciseDetail != initialDescription(run)) {
                fail("offline_remote_fixture_changed")
            }
            if (now <= clock) fail("offline_conflict_clock_order_prerequisite")
            if (request.flow == "offline-conflict-server") {
                MaestroOfflineExercisePolicy.requireOrdering(ext.optLong("localEditClock").takeIf { it > 0 }, now, false)
            }
            if (request.flow == "offline-conflict-local" && ext.has("localEditClock")) fail("offline_conflict_wrong_order")
            val editId = UUID.randomUUID().toString()
            val mutation = JSONObject().put("editId", editId).put("baseVersion", version).put("editedAt", now).put("acknowledged", false)
            ext.put("remoteMutation", mutation)
            val response =
                read(
                    api.patchExerciseRecord(
                        serverId(saved),
                        PatchExerciseRequestDto(
                            run.exerciseTitle,
                            before.exercisePersonalType,
                            before.exerciseLocation,
                            remoteDescription(run),
                            run.startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            run.endedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            editId,
                            version,
                            now,
                        ),
                    ),
                )
            val responseVersion = response.version ?: fail("offline_server_version_prerequisite")
            if (response.exerciseId != serverId(saved) || responseVersion <= version) fail("offline_server_version_prerequisite")
            val after = read(api.getExerciseRecordDetail(serverId(saved)))
            if (after.version == null) fail("offline_server_version_prerequisite")
            if (after.editedAt == null) fail("offline_server_clock_prerequisite")
            if (
                !bodyMatches(after, run, edited = false, remote = true, before.exercisePersonalType, before.exerciseLocation) ||
                after.version != responseVersion || after.editedAt != now || after.pictureDataList != before.pictureDataList
            ) {
                fail("offline_server_clock_contract_missing")
            }
            mutation.put("version", responseVersion).put("acknowledged", true)
        }

        private suspend fun remoteDelete(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            requireMember(request)
            if (request.flow != "offline-remote-delete") fail("invalid_request")
            val ext = extension(saved)
            if (
                !ext.optBoolean("baselineSynced") || !ext.has("editedPending") || ext.optString("faultMode") != "BODY" ||
                ext.optBoolean("faultCleared") || ext.has("remoteDeleteRequestId")
            ) {
                fail("offline_remote_delete_not_prepared")
            }
            val before = read(api.getExerciseRecordDetail(serverId(saved)))
            val frozen = ext.getJSONObject("remoteBefore")
            if (
                before.version != frozen.getLong("version") ||
                before.exerciseTitle != run.exerciseTitle || before.exerciseDetail != initialDescription(run)
            ) {
                fail("offline_remote_fixture_changed")
            }
            val key = UUID.randomUUID().toString()
            ext.put("remoteDeleteRequestId", key)
            val result = api.deleteExerciseRecord(serverId(saved), key)
            if (!result.succeed) fail("offline_remote_mutation_failed")
            requireRemoteAbsent(request, run, saved)
            ext.put("remoteDeleted", true)
        }

        private suspend fun freezeRemote(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            val ext = extension(saved)
            if (ext.has("remoteBefore")) fail("offline_remote_mutation_uncertain")
            val remote = read(api.getExerciseRecordDetail(serverId(saved)))
            val proof = ext.getJSONObject("firstPending")
            if (!bodyMatches(remote, run, false, false, proof.getString("personalType"), proof.getString("location"))) {
                fail("offline_remote_fixture_changed")
            }
            val version = remote.version ?: fail("offline_server_version_prerequisite")
            if (
                version != ext.getLong("baselineVersion") ||
                remote.pictureDataList.orEmpty().map { it.pictureId } != listOf(ext.getLong("photoAId"))
            ) {
                fail("offline_remote_fixture_changed")
            }
            if (request.flow.startsWith("offline-conflict-")) {
                MaestroOfflineExercisePolicy.requireClockContract(remote, System.currentTimeMillis())
            }
            ext.put(
                "remoteBefore",
                JSONObject()
                    .put("version", version)
                    .put("editedAt", remote.editedAt ?: JSONObject.NULL)
                    .put("body", remote.toString())
                    .put("localJournal", database.exerciseSyncDao().get(request.memberId, localId(saved)).toString()),
            )
        }

        private suspend fun identify(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ): String {
            val ids = ownedLocalIds(request.memberId, run)
            val id = ids.singleOrNull() ?: fail("offline_exercise_local_mismatch")
            val baseline = saved.getJSONArray("baselineExerciseLocalIds")
            if ((0 until baseline.length()).any { baseline.getString(it) == id }) fail("offline_exercise_identity_changed")
            val ext = extension(saved)
            if (ext.has("localId") && ext.getString("localId") != id) fail("offline_exercise_identity_changed")
            ext.put("localId", id)
            return id
        }

        private suspend fun capture(
            request: MaestroFeatureRequest,
            saved: JSONObject,
            id: String,
            key: String,
        ) {
            val ext = extension(saved)
            val dao = database.exerciseRecordDao()
            val detail = dao.getDetailByLocalId(request.memberId, id) ?: fail("offline_exercise_local_mismatch")
            val item = dao.getListItem(request.memberId, id) ?: fail("offline_exercise_local_mismatch")
            val journal = journal(request, id)
            val images = database.exerciseImageDao().getByRecord(request.memberId, id)
            val files = JSONArray()
            val imageProofs = JSONArray()
            for (image in images) {
                imageProofs.put(
                    JSONObject()
                        .put("localId", image.localId)
                        .put("identity", MaestroOfflineExercisePolicy.imageIdentity(image))
                        .put("state", image.state.name),
                )
                image.localFilePath?.let { path ->
                    val copies = saved.getJSONObject("media").getJSONArray("copies")
                    val copy =
                        (0 until copies.length()).map { copies.getJSONObject(it) }.singleOrNull { it.getString("path") == path }
                            ?: fail("offline_photo_mapping_mismatch")
                    files.put(JSONObject(copy.toString()))
                }
            }
            val proof =
                JSONObject().put("memberId", request.memberId).put("localId", id).put("item", item.toString())
                    .put("detail", detail.toString()).put("journal", journal.toString())
                    .put("journalIdentity", MaestroOfflineExercisePolicy.journalIdentity(journal))
                    .put("inFlight", MaestroOfflineExercisePolicy.inFlight(journal)).put("receipts", receipts(request, id).toString())
                    .put("images", imageProofs).put("files", files)
                    .put("personalType", detail.personalType).put("location", detail.location)
            if (ext.has(key)) {
                if (!MaestroOfflineExercisePolicy.sameObject(ext.getJSONObject(key), proof)) fail("offline_retention_mismatch")
            } else {
                ext.put(key, proof)
            }
            ext.put("retained", ext.getJSONObject(key))
        }

        private suspend fun requirePhotos(
            request: MaestroFeatureRequest,
            saved: JSONObject,
            id: String,
            label: String,
            pending: Boolean,
        ) {
            val images = database.exerciseImageDao().getByRecord(request.memberId, id)
            val candidates = images.filter { it.state != ExerciseImageState.DELETE_PENDING }
            val image = candidates.singleOrNull() ?: fail("offline_photo_local_mismatch")
            if (pending && image.state !in setOf(ExerciseImageState.ADD_LOCAL, ExerciseImageState.ADD_PENDING)) {
                fail("offline_photo_local_mismatch")
            }
            if (!pending && image.state != ExerciseImageState.SYNCED) fail("offline_photo_local_mismatch")
            if (image.requestId.isNullOrBlank()) fail("offline_photo_mapping_mismatch")
            val proof = media.verifyOwned(image, label, saved)
            val mediaState = saved.getJSONObject("media")
            val copies = mediaState.optJSONArray("copies") ?: JSONArray().also { mediaState.put("copies", it) }
            val existing = (0 until copies.length()).map { copies.getJSONObject(it) }.filter { it.getString("label") == label }
            if (existing.isEmpty()) {
                copies.put(proof)
            } else if (existing.size != 1 || !MaestroOfflineExercisePolicy.sameObject(existing.single(), proof)) {
                fail("offline_photo_identity_changed")
            }
        }

        private suspend fun requireBody(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            id: String,
            edited: Boolean,
            remote: Boolean,
            state: SyncState,
        ) {
            val dao = database.exerciseRecordDao()
            val item = dao.getListItem(request.memberId, id) ?: fail("offline_exercise_local_mismatch")
            val detail = dao.getDetailByLocalId(request.memberId, id) ?: fail("offline_exercise_local_mismatch")
            val title = if (edited) run.editedExerciseTitle else run.exerciseTitle
            val start = if (edited) run.editedStartedAt else run.startedAt
            val description =
                if (remote) {
                    remoteDescription(run)
                } else if (edited) {
                    editedDescription(run)
                } else {
                    initialDescription(run)
                }
            if (
                item.title != title || detail.title != title || detail.detail != description ||
                item.startedAt != start || detail.startedAt != start ||
                item.endedAt != run.endedAt || detail.endedAt != run.endedAt || item.date != run.startedAt.toLocalDate() ||
                item.syncState != state || detail.syncState != state || item.serverId != detail.serverId ||
                item.personalType.isNotEmpty() || detail.personalType.isNotEmpty() || detail.location.isNotEmpty()
            ) {
                fail("offline_exercise_local_mismatch")
            }
        }

        private fun bodyMatches(
            body: GetExerciseRecordData,
            run: MaestroOfflineRun,
            edited: Boolean,
            remote: Boolean,
            personalType: String,
            location: String,
        ): Boolean =
            body.exerciseTitle == (if (edited) run.editedExerciseTitle else run.exerciseTitle) &&
                body.exerciseDetail ==
                (
                    if (remote) {
                        remoteDescription(run)
                    } else if (edited) {
                        editedDescription(run)
                    } else {
                        initialDescription(run)
                    }
                ) &&
                body.exerciseStartedAt == (if (edited) run.editedStartedAt else run.startedAt) && body.exerciseEndedAt == run.endedAt &&
                body.exercisePersonalType == personalType && body.exerciseLocation == location

        private suspend fun absent(
            request: MaestroFeatureRequest,
            saved: JSONObject,
        ): Boolean =
            database.withTransaction {
                val id = localId(saved)
                val dao = database.exerciseRecordDao()
                dao.getListItem(request.memberId, id) == null && dao.getDetailByLocalId(request.memberId, id) == null &&
                    database.exerciseSyncDao().get(request.memberId, id) == null &&
                    database.exerciseImageDao().getByRecord(request.memberId, id).isEmpty() &&
                    receipts(request, id).isEmpty() &&
                    (
                        !extension(saved).has("serverId") ||
                            dao.findListItemLocalId(request.memberId, serverId(saved)) == null &&
                            dao.findDetailLocalId(request.memberId, serverId(saved)) == null &&
                            database.exerciseSyncDao().findLocalId(request.memberId, serverId(saved)) == null
                    )
            }

        private suspend fun remoteAbsent(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ): Boolean {
            requireMember(request)
            val records = records(run)
            return records.none { it.exerciseTitle in run.exerciseTitles || it.exerciseId == extension(saved).optLong("serverId", -1) }
        }

        private suspend fun requireRemoteAbsent(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            if (!remoteAbsent(request, run, saved)) fail("offline_remote_delete_not_observed")
        }

        private suspend fun records(run: MaestroOfflineRun): List<GetExerciseRecordListDto> =
            read(api.getExerciseList(run.startedAt.toLocalDate())).also {
                if (
                    it.any { row -> row.exerciseId <= 0 || row.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate() } ||
                    it.map { row -> row.exerciseId }.toSet().size != it.size
                ) {
                    fail("offline_exercise_server_mismatch")
                }
            }

        private fun ownedLocalIds(
            memberId: String,
            run: MaestroOfflineRun,
        ): Set<String> =
            database.openHelper.readableDatabase.query(
                "SELECT localId FROM exercise_list_item WHERE memberId = ? AND title IN (?, ?) " +
                    "UNION SELECT localId FROM exercise_record_detail WHERE memberId = ? AND title IN (?, ?)",
                arrayOf(memberId, run.exerciseTitle, run.editedExerciseTitle, memberId, run.exerciseTitle, run.editedExerciseTitle),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

        private suspend fun journal(
            request: MaestroFeatureRequest,
            id: String,
        ): ExerciseSyncEntity = database.exerciseSyncDao().get(request.memberId, id) ?: fail("offline_retry_snapshot_missing")

        private suspend fun receipts(
            request: MaestroFeatureRequest,
            id: String,
        ) = database.exercisePointReceiptDao().getByMember(request.memberId).filter { it.recordLocalId == id }

        private suspend fun requireReceipt(
            request: MaestroFeatureRequest,
            saved: JSONObject,
            key: String,
            server: Long,
            points: Int,
        ) {
            val ext = extension(saved)
            MaestroOfflineExercisePolicy.requireReceiptIdentity(
                database.exercisePointReceiptDao().getByMember(request.memberId),
                request.memberId,
                localId(saved),
                key,
                server,
                points,
            )
            if (key != ext.getString("createRequestId") || server != serverId(saved) || points < 0) fail("offline_points_duplicate")
            if (ext.has("earnedPoints") && ext.getInt("earnedPoints") != points) fail("offline_points_duplicate")
            ext.put("earnedPoints", points)
        }

        private fun bindServer(
            saved: JSONObject,
            id: Long,
        ) {
            val ext = extension(saved)
            if (id <= 0 || ext.has("serverId") && ext.getLong("serverId") != id) fail("offline_exercise_identity_changed")
            ext.put("serverId", id)
        }

        private suspend fun awaitMatch(
            code: String,
            timeout: Long = UPLOAD_TIMEOUT_MS,
            condition: suspend () -> Boolean,
        ) {
            if (
                withTimeoutOrNull(timeout) {
                    while (!condition()) delay(500)
                    true
                } != true
            ) {
                fail(code)
            }
        }

        private fun extension(saved: JSONObject): JSONObject = saved.optJSONObject(NAMESPACE) ?: fail("offline_snapshot_missing")

        private fun localId(saved: JSONObject): String =
            extension(saved).optString("localId").takeIf { it.isNotBlank() } ?: fail("offline_exercise_identity_changed")

        private fun serverId(saved: JSONObject): Long =
            extension(saved).optLong("serverId").takeIf { it > 0 } ?: fail("offline_exercise_identity_changed")

        private fun requireMember(request: MaestroFeatureRequest) {
            if (preferences.getMemberId() != request.memberId) fail("member_mismatch")
        }

        private fun requireFlow(request: MaestroFeatureRequest) {
            if (request.feature != "offline" || request.flow !in FLOWS) fail("invalid_request")
        }

        private fun <T : Any> read(response: BaseResponse<T>): T =
            if (response.succeed) response.data ?: fail("fixture_read_failed") else fail("fixture_read_failed")

        companion object {
            private const val NAMESPACE = "extendedExercise"
            private const val UPLOAD_TIMEOUT_MS = 120_000L
            private const val RETRY_TIMEOUT_MS = 720_000L
            private val PROOFS =
                setOf(
                    "firstPending",
                    "editedPending",
                    "retainedBodyAckInitial",
                    "retainedBodyAckEdited",
                    "retryPending",
                    "remoteDeletedPending",
                )
            val SESSION_FLOWS = setOf("offline-session-retain", "offline-session-logout", "offline-session-adoption")
            val FLOWS =
                setOf(
                    "offline-photo-create",
                    "offline-conflict-local",
                    "offline-conflict-server",
                    "offline-remote-delete",
                    "offline-body-retry",
                ) + SESSION_FLOWS
            val LOCAL_STAGES =
                setOf(
                    "exercise-case-local-created",
                    "exercise-case-local-edited",
                    "exercise-case-retained",
                    "exercise-case-remote-deleted",
                    "exercise-case-discard-cancelled",
                    "exercise-case-discarded",
                    "exercise-case-points-consumed",
                    "exercise-case-points-stable",
                )
            val SERVER_STAGES =
                setOf(
                    "exercise-case-baseline-synced",
                    "exercise-case-photos-pending",
                    "exercise-case-complete",
                    "exercise-case-retry-exhausted",
                    "exercise-case-ui-deleted-synced",
                )
            val CONTROL_ACTIONS =
                setOf(
                    "media-delete-originals",
                    "block-body",
                    "block-photos",
                    "unblock-transport",
                    "remote-update",
                    "remote-delete",
                )

            private fun initialDescription(run: MaestroOfflineRun) = "e2e-body-${run.id}"

            private fun editedDescription(run: MaestroOfflineRun) = "e2e-local-${run.id}"

            private fun remoteDescription(run: MaestroOfflineRun) = "e2e-remote-${run.id}"

            private fun fail(code: String): Nothing = throw MaestroSetupFailure(code)
        }
    }
