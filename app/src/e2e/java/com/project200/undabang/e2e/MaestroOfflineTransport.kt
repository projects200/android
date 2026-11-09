package com.project200.undabang.e2e

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.utils.ExerciseSyncAccount
import com.project200.data.utils.ExerciseSyncAccountGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 소유 기록의 실제 전송만 실패시키며 서버 응답이나 Worker 상태는 만들지 않습니다. */
@Singleton
internal class MaestroOfflineTransport
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: UndabangDatabase,
        private val preferences: PreferenceManager,
        private val accounts: ExerciseSyncAccountGuard,
    ) : Interceptor {
        enum class Mode {
            BODY,
            PHOTOS,
        }

        private data class Fault(
            val memberId: String,
            val runId: String,
            val localId: String,
            val mode: Mode,
            val serverId: Long?,
            val flow: String,
            val startedAt: LocalDateTime,
            val endedAt: LocalDateTime,
        )

        enum class BodyOutcome { INJECTED, STARTED, IO_ERROR, ACK, CONFLICT, OTHER }

        /** Only fingerprints and protocol metadata leave the request's in-memory buffer. */
        data class BodyAttempt(
            val editId: String,
            val baseVersion: Long,
            val editedAt: Long,
            val bodySha256: String,
            val intendedSha256: String,
            val outcome: BodyOutcome,
            val responseStatus: Int?,
        )

        private data class Trace(val owner: Fault, val attempts: List<BodyAttempt>, val invalid: Boolean = false)

        private data class AttemptToken(val owner: Fault, val index: Int, val account: ExerciseSyncAccount)

        private var traceBroken = false

        @Synchronized
        fun arm(
            memberId: String,
            runId: String,
            localId: String,
            mode: Mode,
        ) {
            validateIdentity(memberId, runId, localId)
            val account = accounts.capture(memberId) ?: fail("offline_transport_ownership_invalid")
            val saved = ownedSnapshot(memberId, runId, localId)
            val proposed =
                guarded {
                    Fault(
                        memberId,
                        runId,
                        localId,
                        mode,
                        null,
                        string(saved, "flow"),
                        LocalDateTime.parse(string(saved.getJSONObject("parameters"), "startedAt")),
                        LocalDateTime.parse(string(saved.getJSONObject("parameters"), "endedAt")),
                    ).also {
                        if (
                            it.endedAt != it.startedAt.plusHours(1) || it.startedAt.minute != 0 ||
                            it.startedAt.second != 0 || it.startedAt.nano != 0
                        ) {
                            fail("offline_transport_ownership_invalid")
                        }
                    }
                }
            val id = ownedServerId(proposed)
            if (mode == Mode.BODY && id == null) fail("offline_transport_ownership_invalid")
            if (preferences.getMemberId() != memberId || !accounts.isCurrent(account)) {
                fail("offline_transport_ownership_invalid")
            }
            val fault = proposed.copy(serverId = id)
            val existing = readFault()
            if (existing != null) {
                if (existing != fault) fail("offline_transport_already_armed")
                if (mode == Mode.BODY) requireTraceOwner(readTrace(), fault.memberId, fault.runId, fault.localId)
                return
            }
            if (hasTrace()) fail("offline_transport_already_armed")
            if (mode == Mode.BODY) writeTrace(Trace(fault, emptyList()))
            writeFault(fault)
        }

        @Synchronized
        fun clear(
            memberId: String,
            runId: String,
        ) {
            validateIdentity(memberId, runId)
            val fault = readFault()
            if (fault != null && (fault.memberId != memberId || fault.runId != runId)) {
                fail("offline_transport_ownership_invalid")
            }
            // 승인된 로그아웃/폐기로 행과 로그인 상태가 사라져도 원 실행의 복구 증거가 필요합니다
            ownedSnapshot(memberId, runId, fault?.localId)
            if (fault == null) return
            validateSnapshot(fault)
            try {
                atomic().delete()
                if (hasState()) fail("offline_transport_clear_failed")
            } catch (_: Exception) {
                fail("offline_transport_clear_failed")
            }
        }

        @Synchronized
        fun assertClear() {
            if (hasState()) fail("offline_transport_armed")
        }

        /**
         * clear() disarms only the fault. The owned trace remains active across process restarts
         * until ExerciseCases checks the actual requests and verifies safe owned UI deletion.
         */
        @Synchronized
        fun bodyTrace(
            memberId: String,
            runId: String,
            localId: String,
        ): List<BodyAttempt> {
            validateIdentity(memberId, runId, localId)
            if (traceBroken) fail("offline_transport_trace_invalid")
            val trace = readTrace()
            requireTraceOwner(trace, memberId, runId, localId)
            validateSnapshot(trace.owner)
            if (trace.invalid) fail("offline_transport_trace_invalid")
            return trace.attempts
        }

        /** Caller must first verify local erasure and independent remote deletion/ACK. */
        @Synchronized
        fun finishTrace(
            memberId: String,
            runId: String,
            localId: String,
        ) {
            validateIdentity(memberId, runId, localId)
            val saved = ownedSnapshot(memberId, runId, localId)
            if (readFault() != null) fail("offline_transport_armed")
            if (!hasTrace()) {
                if (traceBroken) fail("offline_transport_trace_invalid")
                if (
                    saved.optJSONObject("extendedExercise")?.optString("faultMode") == "BODY" &&
                    saved.optJSONObject("extendedExercise")?.optBoolean("traceFinished") != true
                ) {
                    fail("offline_transport_trace_missing")
                }
                return
            }
            val trace = readTrace()
            requireTraceOwner(trace, memberId, runId, localId)
            if (bodyTrace(memberId, runId, localId).any { it.outcome == BodyOutcome.STARTED }) fail("offline_transport_trace_invalid")
            try {
                traceAtomic().delete()
                if (hasTrace()) fail("offline_transport_trace_invalid")
            } catch (_: Exception) {
                fail("offline_transport_trace_invalid")
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // Fixture API calls have no sync account. Never inspect their URL, body or private state.
            if (request.tag(ExerciseSyncAccount::class.java) == null) return chain.proceed(request)
            val inject: Boolean
            val token =
                synchronized(this) {
                    inject = shouldInject(request)
                    beginAttempt(request, inject)
                }
            if (inject) throw IOException("MAESTRO_OFFLINE_TRANSPORT_INJECTED")
            val response =
                try {
                    chain.proceed(request)
                } catch (failure: IOException) {
                    finishAttempt(token, BodyOutcome.IO_ERROR, null)
                    throw failure
                }
            if (token != null) finishAttempt(token, responseOutcome(response), response.code)
            return response
        }

        private fun beginAttempt(
            request: Request,
            inject: Boolean,
        ): AttemptToken? {
            if (request.method != "PATCH" || traceBroken || !hasTrace()) return null
            val account = request.tag(ExerciseSyncAccount::class.java) ?: return null
            val endpoint = ENDPOINT.matchEntire(request.url.encodedPath) ?: return null
            if (endpoint.groupValues[2].isNotEmpty()) return null
            try {
                val trace = readTrace()
                val owner = trace.owner
                if (
                    account.memberId != owner.memberId || !accounts.isCurrent(account) ||
                    endpoint.groupValues[1].toLongOrNull() != owner.serverId
                ) {
                    return null
                }
                // Reuse the fault ownership boundary even after clear(): never adopt another row.
                try {
                    validateSnapshot(owner)
                    if (ownedServerId(owner) != owner.serverId || !accounts.isCurrent(account)) return null
                } catch (_: MaestroSetupFailure) {
                    return null
                }
                if (trace.invalid || trace.attempts.size >= MAX_ATTEMPTS) {
                    writeTrace(trace.copy(invalid = true))
                    traceBroken = true
                    return null
                }
                val attempt = fingerprint(request, if (inject) BodyOutcome.INJECTED else BodyOutcome.STARTED)
                writeTrace(trace.copy(attempts = trace.attempts + attempt))
                return if (inject) null else AttemptToken(owner, trace.attempts.size, account)
            } catch (_: Exception) {
                // Trace failures must never replace a real response or change fault/restoration behavior.
                traceBroken = true
                invalidateTrace()
                return null
            }
        }

        private fun finishAttempt(
            token: AttemptToken?,
            outcome: BodyOutcome,
            status: Int?,
        ) {
            if (token == null) return
            synchronized(this) {
                if (traceBroken) return
                try {
                    val trace = readTrace()
                    if (
                        trace.owner != token.owner || !accounts.isCurrent(token.account) ||
                        trace.attempts.getOrNull(token.index)?.outcome != BodyOutcome.STARTED
                    ) {
                        traceBroken = true
                        invalidateTrace()
                        return
                    }
                    val attempts = trace.attempts.toMutableList()
                    attempts[token.index] = attempts[token.index].copy(outcome = outcome, responseStatus = status)
                    writeTrace(trace.copy(attempts = attempts))
                } catch (_: Exception) {
                    traceBroken = true
                    invalidateTrace()
                }
            }
        }

        private fun fingerprint(
            request: Request,
            outcome: BodyOutcome,
        ): BodyAttempt {
            val body = request.body ?: fail("offline_transport_trace_invalid")
            if (body.isOneShot() || body.isDuplex() || body.contentLength() !in 1..MAX_BODY_BYTES.toLong()) {
                fail("offline_transport_trace_invalid")
            }
            val bytes =
                Buffer().use { buffer ->
                    body.writeTo(buffer)
                    if (buffer.size !in 1..MAX_BODY_BYTES.toLong()) fail("offline_transport_trace_invalid")
                    buffer.readByteArray()
                }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.keys().asSequence().toSet() != PATCH_KEYS) fail("offline_transport_trace_invalid")
            val key = string(json, "editId")
            if (!EDIT_ID.matches(key)) fail("offline_transport_trace_invalid")
            val intended = JSONArray()
            for (field in BODY_FIELDS) {
                val value = json.get(field) as? String ?: fail("offline_transport_trace_invalid")
                intended.put(value)
            }
            return BodyAttempt(
                key,
                long(json, "baseVersion"),
                long(json, "editedAt").also {
                    if (it <= 0) fail("offline_transport_trace_invalid")
                },
                sha256(bytes),
                sha256(intended.toString().toByteArray(Charsets.UTF_8)),
                outcome,
                null,
            )
        }

        private fun responseOutcome(response: Response): BodyOutcome =
            try {
                // This copy is bounded, never persisted, and does not consume or replace the response.
                val json = response.peekBody(MAX_RESPONSE_BYTES.toLong()).use { JSONObject(it.string()) }
                when {
                    response.isSuccessful && json.opt("succeed") == true -> BodyOutcome.ACK
                    (response.isSuccessful || response.code in 400..499) && json.opt("succeed") == false &&
                        json.opt("code") == "EXERCISE_EDIT_CONFLICT" -> BodyOutcome.CONFLICT
                    else -> BodyOutcome.OTHER
                }
            } catch (_: Exception) {
                BodyOutcome.OTHER
            }

        private fun requireTraceOwner(
            trace: Trace,
            memberId: String,
            runId: String,
            localId: String,
        ) {
            if (trace.owner.memberId != memberId || trace.owner.runId != runId || trace.owner.localId != localId) {
                fail("offline_transport_ownership_invalid")
            }
        }

        private fun readTrace(): Trace {
            if (!hasTrace()) fail("offline_transport_trace_missing")
            try {
                val json = readJson(traceAtomic(), MAX_TRACE_BYTES)
                if (json.keys().asSequence().toSet() != TRACE_KEYS || json.get("schema") != 1 || json.get("invalid") !is Boolean) {
                    fail("offline_transport_trace_invalid")
                }
                val owner = parseFault(json.getJSONObject("owner"))
                if (owner.mode != Mode.BODY) fail("offline_transport_trace_invalid")
                val entries = json.getJSONArray("attempts")
                if (entries.length() > MAX_ATTEMPTS) fail("offline_transport_trace_invalid")
                val attempts =
                    (0 until entries.length()).map { index ->
                        val entry = entries.getJSONObject(index)
                        if (entry.keys().asSequence().toSet() != ATTEMPT_KEYS || entry.get("method") != "PATCH") {
                            fail("offline_transport_trace_invalid")
                        }
                        val status =
                            if (entry.isNull("status")) {
                                null
                            } else {
                                long(entry, "status").also {
                                    if (it !in 100L..599L) fail("offline_transport_trace_invalid")
                                }.toInt()
                            }
                        val outcome = BodyOutcome.valueOf(string(entry, "stage"))
                        val attempt =
                            BodyAttempt(
                                string(entry, "editId"),
                                long(entry, "baseVersion"),
                                long(entry, "editedAt"),
                                string(entry, "bodySha256"),
                                string(entry, "intendedSha256"),
                                outcome,
                                status,
                            )
                        if (
                            !EDIT_ID.matches(attempt.editId) || attempt.editedAt <= 0 ||
                            !SHA256.matches(attempt.bodySha256) || !SHA256.matches(attempt.intendedSha256) ||
                            (status != null) != (outcome in RESPONSE_OUTCOMES) ||
                            (outcome == BodyOutcome.ACK && (status == null || status !in 200..299)) ||
                            (outcome == BodyOutcome.CONFLICT && (status == null || status !in 200..299 && status !in 400..499))
                        ) {
                            fail("offline_transport_trace_invalid")
                        }
                        attempt
                    }
                return Trace(owner, attempts, json.getBoolean("invalid"))
            } catch (_: Exception) {
                fail("offline_transport_trace_invalid")
            }
        }

        private fun writeTrace(trace: Trace) {
            val entries = JSONArray()
            trace.attempts.forEach { attempt ->
                entries.put(
                    JSONObject().put("method", "PATCH").put("stage", attempt.outcome.name)
                        .put("editId", attempt.editId).put("baseVersion", attempt.baseVersion).put("editedAt", attempt.editedAt)
                        .put("bodySha256", attempt.bodySha256).put("intendedSha256", attempt.intendedSha256)
                        .put("status", attempt.responseStatus ?: JSONObject.NULL),
                )
            }
            val bytes =
                JSONObject().put("schema", 1).put("owner", faultJson(trace.owner))
                    .put("invalid", trace.invalid).put("attempts", entries).toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_TRACE_BYTES) fail("offline_transport_trace_invalid")
            val file = traceAtomic()
            val stream = file.startWrite()
            try {
                Os.fchmod(stream.fd, PRIVATE_FILE_MODE)
                stream.write(bytes)
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (_: Exception) {
                // Keep the unfinished .new marker: restoring an older successful trace could hide loss.
                try {
                    stream.close()
                } catch (_: Exception) {
                    /* no private diagnostics */
                }
                fail("offline_transport_trace_invalid")
            }
        }

        private fun invalidateTrace() {
            try {
                val trace = readTrace()
                writeTrace(trace.copy(invalid = true))
            } catch (_: Exception) {
                // Missing/corrupt/interrupted state already fails proof. Never replace that evidence.
            }
        }

        private fun traceAtomic() = AtomicFile(File(context.filesDir, TRACE_FILE))

        private fun hasTrace(): Boolean =
            File(context.filesDir, TRACE_FILE).exists() || File(context.filesDir, "$TRACE_FILE.bak").exists() ||
                File(context.filesDir, "$TRACE_FILE.new").exists()

        private fun long(
            json: JSONObject,
            key: String,
        ): Long =
            when (val value = json.get(key)) {
                is Int -> value.toLong()
                is Long -> value
                else -> fail("offline_transport_trace_invalid")
            }.also { if (it < 0) fail("offline_transport_trace_invalid") }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun shouldInject(request: Request): Boolean {
            val account = request.tag(ExerciseSyncAccount::class.java) ?: return false
            val endpoint = ENDPOINT.matchEntire(request.url.encodedPath) ?: return false
            val serverId = endpoint.groupValues[1].toLongOrNull() ?: return false
            val photo = endpoint.groupValues[2] == "/pictures"
            if (if (photo) request.method !in PHOTO_METHODS else request.method != "PATCH") return false
            val fault =
                try {
                    readFault() ?: return false
                } catch (_: MaestroSetupFailure) {
                    // 손상된 제어 파일로 다른 요청까지 네트워크 실패로 바꾸지 않습니다
                    return false
                }
            if (account.memberId != fault.memberId || !accounts.isCurrent(account)) return false
            if (photo != (fault.mode == Mode.PHOTOS) || (fault.serverId != null && fault.serverId != serverId)) return false
            val currentId =
                try {
                    validateSnapshot(fault)
                    ownedServerId(fault)
                } catch (_: MaestroSetupFailure) {
                    return false
                }
            if (currentId != serverId || !accounts.isCurrent(account)) return false
            if (fault.serverId == null) {
                // 생성 ACK 뒤 첫 사진 전송에서만 서버 ID를 고정합니다
                try {
                    writeFault(fault.copy(serverId = serverId))
                } catch (_: MaestroSetupFailure) {
                    throw IOException("MAESTRO_OFFLINE_TRANSPORT_STATE_FAILED")
                }
            }
            return true
        }

        /** 목록/상세를 한 SQLite 읽기 스냅샷으로 대조합니다. repository 조회의 캐시 쓰기는 사용하지 않습니다. */
        private fun ownedServerId(fault: Fault): Long? =
            guarded {
                val title = "maestro-e2e-${fault.runId}"
                var rows = 0
                var id: Long? = null
                var firstTitle: String? = null
                database.openHelper.readableDatabase.query(
                    "SELECT localId, serverId, title, startedAt, endedAt FROM exercise_list_item " +
                        "WHERE memberId = ? AND (localId = ? OR title IN (?, ?)) " +
                        "UNION ALL SELECT localId, serverId, title, startedAt, endedAt FROM exercise_record_detail " +
                        "WHERE memberId = ? AND (localId = ? OR title IN (?, ?))",
                    arrayOf(fault.memberId, fault.localId, title, "$title-edited", fault.memberId, fault.localId, title, "$title-edited"),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val rowTitle = cursor.getString(2)
                        val expectedStart = if (rowTitle == title) fault.startedAt else fault.startedAt.plusMinutes(5)
                        val rowId = if (cursor.isNull(1)) null else cursor.getLong(1)
                        if (
                            cursor.getString(0) != fault.localId || (rowTitle != title && rowTitle != "$title-edited") ||
                            LocalDateTime.parse(cursor.getString(3)) != expectedStart ||
                            LocalDateTime.parse(cursor.getString(4)) != fault.endedAt ||
                            (rowId != null && rowId <= 0) || (rows > 0 && (rowId != id || rowTitle != firstTitle))
                        ) {
                            fail("offline_transport_ownership_invalid")
                        }
                        id = rowId
                        firstTitle = rowTitle
                        rows++
                    }
                }
                if (rows != 2 || (fault.serverId != null && fault.serverId != id)) {
                    fail("offline_transport_ownership_invalid")
                }
                id
            }

        private fun validateSnapshot(fault: Fault) {
            val saved = ownedSnapshot(fault.memberId, fault.runId, fault.localId)
            guarded {
                val parameters = saved.getJSONObject("parameters")
                if (
                    string(saved, "flow") != fault.flow ||
                    LocalDateTime.parse(string(parameters, "startedAt")) != fault.startedAt ||
                    LocalDateTime.parse(string(parameters, "endedAt")) != fault.endedAt
                ) {
                    fail("offline_transport_ownership_invalid")
                }
            }
        }

        private fun ownedSnapshot(
            memberId: String,
            runId: String,
            localId: String?,
        ): JSONObject =
            guarded {
                val saved = readJson(AtomicFile(File(context.filesDir, SNAPSHOT_FILE)), MAX_SNAPSHOT_BYTES)
                if (string(saved, "memberId") != memberId || string(saved, "runId") != runId) {
                    fail("offline_transport_ownership_invalid")
                }
                if (localId != null) {
                    val baseline = saved.getJSONArray("baselineExerciseLocalIds")
                    if ((0 until baseline.length()).any { baseline.get(it) !is String || baseline.getString(it) == localId }) {
                        fail("offline_transport_ownership_invalid")
                    }
                }
                saved
            }

        private fun readFault(): Fault? {
            if (!hasState()) return null
            return guarded { parseFault(readJson(atomic(), MAX_FAULT_BYTES)) }
        }

        private fun parseFault(json: JSONObject): Fault =
            guarded {
                if (json.keys().asSequence().toSet() != FAULT_KEYS || json.get("schema") != 1) {
                    fail("offline_transport_state_invalid")
                }
                val server = json.get("serverId")
                val id =
                    when (server) {
                        JSONObject.NULL -> null
                        is Int -> server.toLong()
                        is Long -> server
                        else -> fail("offline_transport_state_invalid")
                    }
                val fault =
                    Fault(
                        string(json, "memberId"),
                        string(json, "runId"),
                        string(json, "localId"),
                        Mode.valueOf(string(json, "mode")),
                        id,
                        string(json, "flow"),
                        LocalDateTime.parse(string(json, "startedAt")),
                        LocalDateTime.parse(string(json, "endedAt")),
                    )
                validateIdentity(fault.memberId, fault.runId, fault.localId)
                if (
                    (id != null && id <= 0) || (fault.mode == Mode.BODY && id == null) ||
                    fault.endedAt != fault.startedAt.plusHours(1) || fault.startedAt.minute != 0 ||
                    fault.startedAt.second != 0 || fault.startedAt.nano != 0
                ) {
                    fail("offline_transport_state_invalid")
                }
                fault
            }

        private fun faultJson(fault: Fault): JSONObject =
            JSONObject()
                .put("schema", 1)
                .put("memberId", fault.memberId)
                .put("runId", fault.runId)
                .put("localId", fault.localId)
                .put("mode", fault.mode.name)
                .put("serverId", fault.serverId ?: JSONObject.NULL)
                .put("flow", fault.flow)
                .put("startedAt", fault.startedAt.toString())
                .put("endedAt", fault.endedAt.toString())

        private fun writeFault(fault: Fault) {
            val json = faultJson(fault)
            val file = atomic()
            val stream =
                try {
                    file.startWrite()
                } catch (_: Exception) {
                    fail("offline_transport_write_failed")
                }
            try {
                Os.fchmod(stream.fd, PRIVATE_FILE_MODE)
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (_: Exception) {
                try {
                    file.failWrite(stream)
                } catch (_: Exception) {
                    // 복구 실패의 원문도 공개 오류에 포함하지 않습니다
                }
                fail("offline_transport_write_failed")
            }
        }

        private fun readJson(
            file: AtomicFile,
            maxBytes: Int,
        ): JSONObject {
            if (File(file.baseFile.path + ".new").exists()) fail("offline_transport_state_invalid")
            return file.openRead().use { input ->
                if (input.channel.size() !in 1..maxBytes.toLong()) fail("offline_transport_state_invalid")
                JSONObject(input.readBytes().toString(Charsets.UTF_8))
            }
        }

        private fun atomic() = AtomicFile(File(context.filesDir, FAULT_FILE))

        private fun hasState(): Boolean = listOf("", ".bak", ".new").any { File(context.filesDir, FAULT_FILE + it).exists() }

        private fun string(
            json: JSONObject,
            key: String,
        ): String = (json.get(key) as? String)?.takeIf { it.isNotBlank() } ?: fail("offline_transport_state_invalid")

        private fun validateIdentity(
            memberId: String,
            runId: String,
            localId: String? = null,
        ) {
            if (memberId.isBlank() || !RUN_ID.matches(runId) || (localId != null && localId.isBlank())) {
                fail("offline_transport_invalid_request")
            }
        }

        private inline fun <T> guarded(block: () -> T): T =
            try {
                block()
            } catch (failure: MaestroSetupFailure) {
                throw failure
            } catch (_: Exception) {
                fail("offline_transport_state_invalid")
            }

        private fun fail(code: String): Nothing = throw MaestroSetupFailure(code)

        companion object {
            private const val FAULT_FILE = "maestro-offline-transport.json"
            private const val TRACE_FILE = "maestro-offline-body-trace.json"
            private const val SNAPSHOT_FILE = "maestro-offline-state.json"
            private const val PRIVATE_FILE_MODE = 384
            private const val MAX_FAULT_BYTES = 4096
            private const val MAX_SNAPSHOT_BYTES = 1_048_576
            private const val MAX_TRACE_BYTES = 65_536
            private const val MAX_BODY_BYTES = 65_536
            private const val MAX_RESPONSE_BYTES = 16_384
            private const val MAX_ATTEMPTS = 64
            private val EDIT_ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
            private val SHA256 = Regex("[a-f0-9]{64}")
            private val BODY_FIELDS =
                listOf(
                    "exerciseTitle",
                    "exercisePersonalType",
                    "exerciseLocation",
                    "exerciseDetail",
                    "exerciseStartedAt",
                    "exerciseEndedAt",
                )
            private val PATCH_KEYS = BODY_FIELDS.toSet() + setOf("editId", "baseVersion", "editedAt")
            private val RESPONSE_OUTCOMES = setOf(BodyOutcome.ACK, BodyOutcome.CONFLICT, BodyOutcome.OTHER)
            private val TRACE_KEYS = setOf("schema", "owner", "invalid", "attempts")
            private val ATTEMPT_KEYS =
                setOf(
                    "method",
                    "stage",
                    "editId",
                    "baseVersion",
                    "editedAt",
                    "bodySha256",
                    "intendedSha256",
                    "status",
                )
            private val RUN_ID = Regex("[a-f0-9]{32}")
            private val ENDPOINT = Regex("/(?:dev/)?api/v1/exercises/([1-9][0-9]*)(/pictures)?")
            private val PHOTO_METHODS = setOf("POST", "DELETE")
            private val FAULT_KEYS =
                setOf("schema", "memberId", "runId", "localId", "mode", "serverId", "flow", "startedAt", "endedAt")
        }
    }
