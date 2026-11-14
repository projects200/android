package com.project200.undabang.e2e

import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExercisePointReceiptEntity
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import org.json.JSONObject

/** Pure checks shared by real observations and uncertain-boundary tests. Never changes product state. */
internal object MaestroOfflineExercisePolicy {
    fun requireClockContract(
        remote: GetExerciseRecordData,
        now: Long,
    ): Pair<Long, Long> {
        val version = remote.version?.takeIf { it >= 0 } ?: fail("offline_server_version_prerequisite")
        val clock = remote.editedAt?.takeIf { it > 0 } ?: fail("offline_server_clock_prerequisite")
        // The client sends real epoch milliseconds. Seconds, future clocks and large skew are not usable fixtures.
        if (kotlin.math.abs(now - clock) > 300_000L) fail("offline_server_clock_untrusted")
        return version to clock
    }

    fun syncedVersionsMatch(
        detailVersion: Long?,
        listVersion: Long?,
        localDetailVersion: Long?,
        localListVersion: Long?,
    ): Boolean {
        val version = detailVersion ?: fail("offline_server_version_prerequisite")
        return (listVersion == null || listVersion == version) &&
            localDetailVersion == version && localListVersion == version
    }

    fun requireOrdering(
        local: Long?,
        remote: Long,
        localWins: Boolean,
    ) {
        val clock = local?.takeIf { it > 0 } ?: fail("offline_local_clock_prerequisite")
        if (if (localWins) clock <= remote else remote <= clock) fail("offline_conflict_clock_order_prerequisite")
    }

    fun journalIdentity(journal: ExerciseSyncEntity): String =
        listOf(
            journal.memberId,
            journal.localId,
            journal.serverId,
            journal.operation,
            journal.revision,
            journal.operationId,
            journal.createRequestId,
            journal.serverVersion,
            journal.confirmedDate,
            journal.currentDate,
            journal.desiredEditedAt,
        ).joinToString("\u0000")

    fun inFlight(journal: ExerciseSyncEntity): JSONObject =
        JSONObject()
            .put("operation", journal.inFlightOperation?.name ?: JSONObject.NULL)
            .put("revision", journal.inFlightRevision ?: JSONObject.NULL)
            .put("operationId", journal.inFlightOperationId ?: JSONObject.NULL)
            .put("requestId", journal.inFlightRequestId ?: JSONObject.NULL)
            .put("body", journal.inFlightBody ?: JSONObject.NULL)
            .put("version", journal.inFlightBaseVersion ?: JSONObject.NULL)
            .put("editedAt", journal.inFlightEditedAt ?: JSONObject.NULL)

    fun requireRetrySnapshot(
        journal: ExerciseSyncEntity,
        frozen: JSONObject,
    ) {
        if (
            journal.operation != SyncState.UPDATE_PENDING || journal.lastError != ExerciseSyncError.NETWORK_ERROR ||
            journal.inFlightOperation != SyncState.UPDATE_PENDING || journal.inFlightBody.isNullOrBlank() ||
            journal.inFlightRequestId.isNullOrBlank() || !sameObject(inFlight(journal), frozen)
        ) {
            fail("offline_retry_snapshot_changed")
        }
    }

    /** Room may delete the in-flight journal at ACK; only actual transport attempts prove replay. */
    fun requireRetryTrace(
        attempts: List<MaestroOfflineTransport.BodyAttempt>,
        frozen: JSONObject,
        injectedCount: Int,
        completed: Boolean,
    ) {
        val code = "offline_retry_request_trace_mismatch"
        val first = attempts.firstOrNull() ?: fail(code)
        if (
            injectedCount <= 0 || attempts.size < injectedCount ||
            first.editId != frozen.getString("requestId") || first.baseVersion != frozen.getLong("version") ||
            first.editedAt != frozen.getLong("editedAt")
        ) {
            fail(code)
        }
        for ((index, attempt) in attempts.withIndex()) {
            if (!sameRequest(first, attempt)) fail(code)
            if (index < injectedCount) {
                if (attempt.outcome != MaestroOfflineTransport.BodyOutcome.INJECTED) fail(code)
            } else if (!completed || !ordinaryResponse(attempt)) {
                fail(code)
            }
        }
        if (completed && (attempts.size <= injectedCount || attempts.last().outcome != MaestroOfflineTransport.BodyOutcome.ACK)) fail(code)
    }

    fun requireConflictTrace(
        attempts: List<MaestroOfflineTransport.BodyAttempt>,
        originalKey: String,
        originalVersion: Long,
        localClock: Long,
        latestVersion: Long,
        localWins: Boolean,
    ) {
        val code = "offline_conflict_request_trace_mismatch"
        val first = attempts.firstOrNull() ?: fail(code)
        if (
            latestVersion <= originalVersion ||
            first.editId != originalKey || first.baseVersion != originalVersion || first.editedAt != localClock
        ) {
            fail(code)
        }
        for (index in attempts.indices) {
            val attempt = attempts[index]
            if (
                !sameRequest(first, attempt) || index < attempts.lastIndex &&
                attempt.outcome != MaestroOfflineTransport.BodyOutcome.INJECTED &&
                attempt.outcome != MaestroOfflineTransport.BodyOutcome.IO_ERROR
            ) {
                fail(code)
            }
        }
        val terminal = attempts.last()
        if (localWins) {
            if (terminal.outcome != MaestroOfflineTransport.BodyOutcome.ACK || terminal.responseStatus != 200) fail(code)
        } else if (terminal.outcome != MaestroOfflineTransport.BodyOutcome.CONFLICT) {
            fail(code)
        }
    }

    private fun ordinaryResponse(attempt: MaestroOfflineTransport.BodyAttempt): Boolean =
        attempt.outcome == MaestroOfflineTransport.BodyOutcome.IO_ERROR || attempt.outcome == MaestroOfflineTransport.BodyOutcome.ACK

    private fun sameRequest(
        left: MaestroOfflineTransport.BodyAttempt,
        right: MaestroOfflineTransport.BodyAttempt,
    ): Boolean =
        left.editId == right.editId && left.baseVersion == right.baseVersion && left.editedAt == right.editedAt &&
            left.bodySha256 == right.bodySha256 && left.intendedSha256 == right.intendedSha256

    fun imageIdentity(image: ExerciseImageEntity): String =
        listOf(
            image.memberId,
            image.recordLocalId,
            image.localId,
            image.serverPictureId,
            image.localFilePath,
            image.serverUrl,
            image.requestId,
            image.sortOrder,
        ).joinToString("\u0000")

    fun requireImageRetention(
        image: ExerciseImageEntity,
        frozen: JSONObject,
    ) {
        if (imageIdentity(image) != frozen.getString("identity")) fail("offline_photo_retention_mismatch")
        val before = ExerciseImageState.valueOf(frozen.getString("state"))
        if (image.state != before && !(before == ExerciseImageState.ADD_LOCAL && image.state == ExerciseImageState.ADD_PENDING)) {
            fail("offline_photo_retention_mismatch")
        }
    }

    fun requireReceiptIdentity(
        receipts: List<ExercisePointReceiptEntity>,
        memberId: String,
        localId: String,
        createRequestId: String,
        serverId: Long,
        earnedPoints: Int,
    ) {
        val matches =
            receipts.filter {
                it.createRequestId == createRequestId || it.serverId == serverId || it.recordLocalId == localId
            }
        val receipt = matches.singleOrNull() ?: fail("offline_points_duplicate")
        if (
            receipt.memberId != memberId || receipt.recordLocalId != localId || receipt.createRequestId != createRequestId ||
            receipt.serverId != serverId || receipt.earnedPoints != earnedPoints || earnedPoints < 0 || serverId <= 0
        ) {
            fail("offline_points_duplicate")
        }
    }

    fun sameObject(
        left: JSONObject,
        right: JSONObject,
    ): Boolean {
        val keys = left.keys().asSequence().toSet()
        if (keys != right.keys().asSequence().toSet()) return false
        return keys.all { key -> left.opt(key).toString() == right.opt(key).toString() }
    }

    private fun fail(code: String): Nothing = throw MaestroSetupFailure(code)
}
