package com.project200.undabang.e2e

import android.app.Application
import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExercisePointReceiptEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroOfflineExercisePolicyTest {
    private val now = 1794150000000L

    @Test
    fun `missing server version and clock are distinct prerequisites not fixture success`() {
        failure("offline_server_version_prerequisite") { MaestroOfflineExercisePolicy.requireClockContract(remote(version = null), now) }
        failure("offline_server_clock_prerequisite") { MaestroOfflineExercisePolicy.requireClockContract(remote(clock = null), now) }
        failure("offline_server_clock_untrusted") { MaestroOfflineExercisePolicy.requireClockContract(remote(clock = now / 1000), now) }
        assertThat(MaestroOfflineExercisePolicy.requireClockContract(remote(), now)).isEqualTo(7L to now)
    }

    @Test
    fun `representative conflict requires strict genuine clock ordering and does not invent equal timestamps`() {
        MaestroOfflineExercisePolicy.requireOrdering(now + 1, now, true)
        MaestroOfflineExercisePolicy.requireOrdering(now - 1, now, false)
        failure("offline_conflict_clock_order_prerequisite") { MaestroOfflineExercisePolicy.requireOrdering(now, now, true) }
        failure("offline_conflict_clock_order_prerequisite") { MaestroOfflineExercisePolicy.requireOrdering(now, now, false) }
        failure("offline_local_clock_prerequisite") { MaestroOfflineExercisePolicy.requireOrdering(null, now, true) }
    }

    @Test
    fun `ordinary retry rejects changed key original body revision clock and base version`() {
        val original = journal()
        val proof = MaestroOfflineExercisePolicy.inFlight(original)
        MaestroOfflineExercisePolicy.requireRetrySnapshot(original, proof)
        for (changed in listOf(
            original.copy(inFlightRequestId = "other-key"),
            original.copy(inFlightBody = "different-original-bytes"),
            original.copy(inFlightRevision = 2),
            original.copy(inFlightBaseVersion = 8),
            original.copy(inFlightEditedAt = now + 1),
            original.copy(lastError = ExerciseSyncError.CONFLICT),
        )) {
            failure("offline_retry_snapshot_changed") { MaestroOfflineExercisePolicy.requireRetrySnapshot(changed, proof) }
        }
        assertThat(proof.getString("body")).isEqualTo("original-bytes")
    }

    @Test
    fun `photo retention permits claim state only and rejects key path mapping and disappearance changes`() {
        val image =
            ExerciseImageEntity(
                "member",
                "record",
                "image",
                null,
                "/private/image.png",
                null,
                ExerciseImageState.ADD_LOCAL,
                "image-request",
                0,
            )
        val proof = JSONObject().put("identity", MaestroOfflineExercisePolicy.imageIdentity(image)).put("state", image.state.name)
        MaestroOfflineExercisePolicy.requireImageRetention(image.copy(state = ExerciseImageState.ADD_PENDING), proof)
        for (changed in listOf(
            image.copy(requestId = "new-request"),
            image.copy(localFilePath = "/private/replaced.png"),
            image.copy(serverPictureId = 12),
            image.copy(state = ExerciseImageState.SYNCED),
            image.copy(memberId = "peer"),
            image.copy(state = ExerciseImageState.DELETE_PENDING),
        )) {
            failure("offline_photo_retention_mismatch") { MaestroOfflineExercisePolicy.requireImageRetention(changed, proof) }
        }
    }

    @Test
    fun `independent photo content accepts recompression but rejects swapped or count-only images`() {
        val a = encoded("A", Bitmap.CompressFormat.PNG)
        val jpeg = encoded("A", Bitmap.CompressFormat.JPEG)
        val b = encoded("B", Bitmap.CompressFormat.PNG)
        assertThat(MaestroOfflinePhotoMedia.matchesContent(a, "A")).isTrue()
        assertThat(MaestroOfflinePhotoMedia.matchesContent(jpeg, "A")).isTrue()
        assertThat(MaestroOfflinePhotoMedia.matchesContent(b, "A")).isFalse()
        assertThat(MaestroOfflinePhotoMedia.matchesContent(byteArrayOf(1, 2, 3), "A")).isFalse()
        assertThat(MaestroOfflinePhotoMedia.sha256(a)).isNotEqualTo(MaestroOfflinePhotoMedia.sha256(b))
    }

    @Test
    fun `zero-point genuine receipt is valid but duplicate request server or record receipts are not`() {
        val receipt = ExercisePointReceiptEntity("member", "create", "record", 101, 0, false)

        fun check(receipts: List<ExercisePointReceiptEntity>) =
            MaestroOfflineExercisePolicy.requireReceiptIdentity(receipts, "member", "record", "create", 101, 0)
        check(listOf(receipt))
        check(listOf(receipt.copy(noticeConsumed = true)))
        for (other in listOf(
            receipt.copy(createRequestId = "duplicate-key"),
            receipt.copy(recordLocalId = "duplicate-record", createRequestId = "other-key"),
            receipt.copy(serverId = 102, createRequestId = "other-key"),
        )) {
            failure("offline_points_duplicate") { check(listOf(receipt, other)) }
        }
        failure("offline_points_duplicate") { check(emptyList()) }
        failure("offline_points_duplicate") { check(listOf(receipt.copy(earnedPoints = 1))) }
    }

    @Test
    fun `actual retry trace must retain the rejected request key and exact serialized bytes through ACK`() {
        val frozen = MaestroOfflineExercisePolicy.inFlight(journal())
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val ack = attempt()
        MaestroOfflineExercisePolicy.requireRetryTrace(listOf(injected, injected), frozen, 2, completed = false)
        MaestroOfflineExercisePolicy.requireRetryTrace(listOf(injected, injected, ack), frozen, 2, completed = true)
        for (changed in listOf(
            ack.copy(editId = "replacement"),
            ack.copy(baseVersion = 8),
            ack.copy(editedAt = now + 1),
            ack.copy(bodySha256 = "b".repeat(64)),
            ack.copy(intendedSha256 = "c".repeat(64)),
            ack.copy(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT),
            ack.copy(outcome = MaestroOfflineTransport.BodyOutcome.STARTED),
        )) {
            failure("offline_retry_request_trace_mismatch") {
                MaestroOfflineExercisePolicy.requireRetryTrace(listOf(injected, injected, changed), frozen, 2, completed = true)
            }
        }
        failure("offline_retry_request_trace_mismatch") {
            MaestroOfflineExercisePolicy.requireRetryTrace(listOf(injected, injected), frozen, 2, completed = true)
        }
    }

    @Test
    fun `newer local clock accepts HTTP 200 ACK directly with the original stale base and exact request`() {
        val ack = attempt()
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val uncertain = attempt(outcome = MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        for (trace in listOf(
            listOf(ack),
            listOf(injected, ack),
            listOf(uncertain, ack),
            listOf(injected, uncertain, ack),
        )) {
            MaestroOfflineExercisePolicy.requireConflictTrace(trace, "request", 7, now, 8, localWins = true)
        }
    }

    @Test
    fun `both clock winners require the captured original key stale version and clock`() {
        for (localWins in listOf(true, false)) {
            val terminal = if (localWins) attempt() else attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = 409)
            for (changed in listOf(
                terminal.copy(editId = "replacement"),
                terminal.copy(baseVersion = 8),
                terminal.copy(editedAt = now + 1),
            )) {
                failure("offline_conflict_request_trace_mismatch") {
                    MaestroOfflineExercisePolicy.requireConflictTrace(listOf(changed), "request", 7, now, 8, localWins)
                }
            }
            for (latestVersion in listOf(6L, 7L)) {
                failure("offline_conflict_request_trace_mismatch") {
                    MaestroOfflineExercisePolicy.requireConflictTrace(listOf(terminal), "request", 7, now, latestVersion, localWins)
                }
            }
        }
    }

    @Test
    fun `both clock winners reject changed request keys serialized bytes intended body clock and base`() {
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val uncertain = attempt(outcome = MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        for (localWins in listOf(true, false)) {
            val terminal = if (localWins) attempt() else attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = 409)
            for (changed in listOf(
                uncertain.copy(editId = "replacement"),
                uncertain.copy(baseVersion = 8),
                uncertain.copy(editedAt = now + 1),
                uncertain.copy(bodySha256 = "b".repeat(64)),
                uncertain.copy(intendedSha256 = "c".repeat(64)),
            )) {
                failure("offline_conflict_request_trace_mismatch") {
                    MaestroOfflineExercisePolicy.requireConflictTrace(listOf(injected, changed, terminal), "request", 7, now, 8, localWins)
                }
                failure("offline_conflict_request_trace_mismatch") {
                    MaestroOfflineExercisePolicy.requireConflictTrace(
                        listOf(injected, changed.copy(outcome = terminal.outcome, responseStatus = terminal.responseStatus)),
                        "request",
                        7,
                        now,
                        8,
                        localWins,
                    )
                }
            }
        }
    }

    @Test
    fun `newer local clock rejects old CAS conflict and new-key rebase instead of accepting both contracts`() {
        val rejected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = 409)
        val rebased = attempt().copy(editId = "new-request", baseVersion = 8, bodySha256 = "b".repeat(64))
        for (trace in listOf(listOf(rejected, rebased), listOf(rebased))) {
            failure("offline_conflict_request_trace_mismatch") {
                MaestroOfflineExercisePolicy.requireConflictTrace(trace, "request", 7, now, 8, localWins = true)
            }
        }
    }

    @Test
    fun `newer local clock requires a final actual HTTP 200 ACK with only uncertain failures before it`() {
        val ack = attempt()
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val uncertain = attempt(outcome = MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        val rejected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = 409)
        val started = attempt(outcome = MaestroOfflineTransport.BodyOutcome.STARTED)
        val other = attempt(outcome = MaestroOfflineTransport.BodyOutcome.OTHER, status = 500)
        for (trace in listOf(
            emptyList(),
            listOf(injected, uncertain),
            listOf(rejected),
            listOf(started),
            listOf(other),
            listOf(ack.copy(responseStatus = null)),
            listOf(ack.copy(responseStatus = 201)),
            listOf(ack.copy(responseStatus = 204)),
            listOf(ack.copy(responseStatus = 409)),
            listOf(rejected, ack),
            listOf(started, ack),
            listOf(other, ack),
            listOf(ack, ack),
            listOf(ack, uncertain),
        )) {
            failure("offline_conflict_request_trace_mismatch") {
                MaestroOfflineExercisePolicy.requireConflictTrace(trace, "request", 7, now, 8, localWins = true)
            }
        }
    }

    @Test
    fun `newer server clock requires definitive conflict on the unchanged original request`() {
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val uncertain = attempt(outcome = MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        for (status in listOf(200, 409)) {
            val rejected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = status)
            for (trace in listOf(listOf(rejected), listOf(injected, uncertain, rejected))) {
                MaestroOfflineExercisePolicy.requireConflictTrace(trace, "request", 7, now, 8, localWins = false)
            }
        }
    }

    @Test
    fun `newer server clock rejects missing conflict or any subsequent request including ACK or rebase`() {
        val ack = attempt()
        val injected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.INJECTED)
        val uncertain = attempt(outcome = MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        val rejected = attempt(outcome = MaestroOfflineTransport.BodyOutcome.CONFLICT, status = 409)
        val started = attempt(outcome = MaestroOfflineTransport.BodyOutcome.STARTED)
        val other = attempt(outcome = MaestroOfflineTransport.BodyOutcome.OTHER, status = 500)
        val rebased = ack.copy(editId = "new-request", baseVersion = 8, bodySha256 = "b".repeat(64))
        for (trace in listOf(
            emptyList(),
            listOf(ack),
            listOf(injected, uncertain),
            listOf(started),
            listOf(other),
            listOf(ack, rejected),
            listOf(started, rejected),
            listOf(other, rejected),
        ) + listOf(ack, injected, uncertain, rejected, started, other, rebased).map { listOf(rejected, it) }) {
            failure("offline_conflict_request_trace_mismatch") {
                MaestroOfflineExercisePolicy.requireConflictTrace(trace, "request", 7, now, 8, localWins = false)
            }
        }
    }

    private fun attempt(
        outcome: MaestroOfflineTransport.BodyOutcome = MaestroOfflineTransport.BodyOutcome.ACK,
        status: Int? = if (outcome == MaestroOfflineTransport.BodyOutcome.ACK) 200 else null,
    ) = MaestroOfflineTransport.BodyAttempt("request", 7, now, "a".repeat(64), "e".repeat(64), outcome, status)

    private fun encoded(
        label: String,
        format: Bitmap.CompressFormat,
    ): ByteArray {
        val bitmap = MaestroOfflinePhotoMedia.bitmap(label)
        return try {
            ByteArrayOutputStream().use {
                bitmap.compress(format, 85, it)
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun remote(
        version: Long? = 7,
        clock: Long? = now,
    ) =
        GetExerciseRecordData(
            "owned",
            "body",
            "",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0),
            "",
            null,
            version,
            clock,
        )

    private fun journal() =
        ExerciseSyncEntity(
            memberId = "member",
            localId = "record",
            serverId = 1,
            operation = SyncState.UPDATE_PENDING,
            revision = 1,
            operationId = "operation",
            createRequestId = "create",
            serverVersion = 7,
            confirmedDate = LocalDate.of(2026, 1, 1),
            currentDate = LocalDate.of(2026, 1, 1),
            recoveryState = ExerciseRecoveryState.RETRYABLE,
            lastError = ExerciseSyncError.NETWORK_ERROR,
            inFlightOperation = SyncState.UPDATE_PENDING,
            inFlightRevision = 1,
            inFlightOperationId = "operation",
            inFlightRequestId = "request",
            inFlightBody = "original-bytes",
            inFlightBaseVersion = 7,
            desiredEditedAt = now,
            inFlightEditedAt = now,
        )

    private fun failure(
        code: String,
        block: () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Expected private fixture failure")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(code)
        }
    }
}
