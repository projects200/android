package com.project200.undabang.e2e

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.project200.data.api.ApiService
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.DeleteTimerRequestDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetExerciseRecordListDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

internal data class MaestroOfflineRun(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val timerInput: MaestroOfflineTimerInput,
) {
    init {
        if (!ID_PATTERN.matches(id) || endedAt != startedAt.plusHours(1) ||
            startedAt.minute != 0 || startedAt.second != 0 || startedAt.nano != 0
        ) {
            throw MaestroSetupFailure("invalid_request")
        }
    }

    val exerciseTitle: String = "maestro-e2e-$id"
    val editedExerciseTitle: String = "$exerciseTitle-edited"
    val exerciseTitles: Set<String> = setOf(exerciseTitle, editedExerciseTitle)
    val editedStartedAt: LocalDateTime = startedAt.plusMinutes(5)
    val createdTimer: String = "maestro-offline-$id-create"
    val originalTimer: String = "maestro-offline-$id-update"
    val updatedTimer: String = "maestro-offline-$id-updated"
    val deletedTimer: String = "maestro-offline-$id-delete"
    val draftTimer: String = "maestro-offline-$id-draft"
    val timerNames: Set<String> = setOf(createdTimer, originalTimer, updatedTimer, deletedTimer, draftTimer)

    fun variables(): Map<String, String> =
        timerInput.variables() +
            mapOf(
                "TIMER_CREATE_TITLE" to createdTimer,
                "TIMER_ORIGINAL_TITLE" to originalTimer,
                "TIMER_UPDATED_TITLE" to updatedTimer,
                "TIMER_DELETE_TITLE" to deletedTimer,
                "TIMER_DRAFT_TITLE" to draftTimer,
            )

    companion object {
        private val ID_PATTERN = Regex("[a-f0-9]{32}")
    }
}

/** 생성·수정은 UI와 실제 동기화만 수행합니다. 이 클래스는 직접 GET과 실행별 정리만 수행합니다 */
internal class MaestroOfflineFixtures
    @Inject
    constructor(
        private val api: ApiService,
        @ApplicationContext private val context: Context,
        private val localDataSource: TimerLocalDataSource,
        private val exercises: MaestroOfflineExerciseFixture,
    ) : MaestroFeatureFixture {
        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            val run = run(request)
            if (request.parameters.has("stage")) throw MaestroSetupFailure("invalid_feature_request")
            val file = snapshot().baseFile
            if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) {
                throw MaestroSetupFailure("offline_recovery_required")
            }
            save(
                JSONObject()
                    .put("memberId", request.memberId)
                    .put("runId", request.runId)
                    .put("flow", request.flow)
                    .put("parameters", request.parameters)
                    .put("baselineLocalIds", JSONArray(localDataSource.getCustomTimers().map { it.localId }))
                    .put("baselineExerciseLocalIds", exercises.baseline(request.memberId, run)),
            )
            validateDate(run)
            clean(run)
            return run.variables()
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            if (request.parameters.has("stage")) throw MaestroSetupFailure("invalid_feature_request")
            val file = snapshot().baseFile
            val saved =
                if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) {
                    validateRequest(request)
                } else {
                    null
                }
            val run = run(request)
            validateDate(run)
            clean(run, saved)
        }

        fun validateRequest(request: MaestroFeatureRequest): JSONObject {
            run(request)
            val saved =
                try {
                    JSONObject(snapshot().openRead().bufferedReader().use { it.readText() })
                } catch (_: Exception) {
                    throw MaestroSetupFailure("offline_snapshot_missing")
                }
            if (saved.getString("memberId") != request.memberId || saved.getString("runId") != request.runId ||
                saved.getString("flow") != request.flow
            ) {
                throw MaestroSetupFailure("offline_snapshot_identity_invalid")
            }
            val expected = saved.getJSONObject("parameters")
            if (MaestroOfflineTimerInput.PARAMETER_KEYS.any { expected.getString(it) != request.parameters.getString(it) }) {
                throw MaestroSetupFailure("offline_snapshot_input_mismatch")
            }
            return saved
        }

        override suspend fun observe(
            request: MaestroFeatureRequest,
            stage: String,
        ): Map<String, String> {
            val run = run(request)
            if (stage in MaestroOfflineExerciseFixture.LOCAL_STAGES) {
                if (request.flow != "offline-exercise-create" || request.parameters.optString("stage") != stage) {
                    throw MaestroSetupFailure("invalid_request")
                }
                val saved = validateRequest(request)
                exercises.observeLocal(request.memberId, run, saved, stage)
                save(saved)
                return emptyMap()
            }
            if (request.flow != "offline-timers-online" ||
                (request.parameters.has("stage") && request.parameters.getString("stage") != stage)
            ) {
                throw MaestroSetupFailure("invalid_request")
            }
            val saved = validateRequest(request)
            val roles =
                when (stage) {
                    "timers-local-online" -> mapOf("ORIGINAL" to run.originalTimer, "DELETE" to run.deletedTimer)
                    "timers-local-created" -> mapOf("CREATE" to run.createdTimer)
                    else -> throw MaestroSetupFailure("invalid_request")
                }
            val baseline = saved.getJSONArray("baselineLocalIds")
            val baselineIds = (0 until baseline.length()).map { baseline.getString(it) }.toSet()
            val current = localDataSource.getCustomTimers()
            val variables = mutableMapOf<String, String>()
            for ((role, name) in roles) {
                val owned = current.filter { it.localId !in baselineIds && it.name == name }
                val timer = owned.singleOrNull() ?: throw MaestroSetupFailure("offline_timer_local_id_missing")
                val key = "localId$role"
                if (saved.has(key) && saved.getString(key) != timer.localId) {
                    throw MaestroSetupFailure("offline_timer_identity_changed")
                }
                saved.put(key, timer.localId)
                variables["TIMER_${role}_LOCAL_ID"] = timer.localId
            }
            save(saved)
            return variables
        }

        suspend fun observeServer(
            request: MaestroFeatureRequest,
            stage: String,
        ) {
            val run = run(request)
            if (stage in MaestroOfflineExerciseFixture.SERVER_STAGES) {
                if (request.flow != "offline-exercise-create") throw MaestroSetupFailure("invalid_request")
                val saved = validateRequest(request)
                exercises.observeServer(request.memberId, run, saved, stage)
                save(saved)
            } else {
                if (request.flow != "offline-timers-online") throw MaestroSetupFailure("invalid_request")
                observe(run, stage)
            }
        }

        private fun snapshot() = AtomicFile(File(context.filesDir, "maestro-offline-state.json"))

        private fun save(saved: JSONObject) {
            val file = snapshot()
            val stream = file.startWrite()
            try {
                Os.fchmod(stream.fd, PRIVATE_FILE_MODE)
                stream.write(saved.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (_: Exception) {
                file.failWrite(stream)
                throw MaestroSetupFailure("offline_snapshot_write_failed")
            }
        }

        fun validateDate(run: MaestroOfflineRun) {
            if (run.endedAt.toLocalDate() > LocalDate.now()) throw MaestroSetupFailure("fixture_range_invalid")
        }

        suspend fun observe(
            run: MaestroOfflineRun,
            stage: String,
        ) {
            val expected =
                when (stage) {
                    "timers-online" ->
                        mapOf(
                            run.originalTimer to run.timerInput.initialSeconds,
                            run.deletedTimer to run.timerInput.initialSeconds,
                        )
                    "timers-synced" ->
                        mapOf(
                            run.createdTimer to run.timerInput.initialSeconds,
                            run.updatedTimer to run.timerInput.updatedSeconds,
                        )
                    else -> throw MaestroSetupFailure("invalid_request")
                }
            val observed =
                withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    var matches = timersMatch(run, expected)
                    while (!matches) {
                        delay(2000)
                        matches = timersMatch(run, expected)
                    }
                    true
                }
            if (observed != true) throw MaestroSetupFailure("offline_upload_not_observed")
        }

        private suspend fun timersMatch(
            run: MaestroOfflineRun,
            expected: Map<String, Int>,
        ): Boolean {
            val summaries = timerSummaries()
            val owned = summaries.filter { it.customTimerName in run.timerNames }
            if (owned.size != expected.size || owned.map { it.customTimerName }.toSet() != expected.keys) return false
            for (summary in owned) {
                val detail = read(api.getCustomTimer(summary.customTimerId))
                if (
                    !matchesTimer(
                        detail,
                        summary.customTimerId,
                        summary.customTimerName,
                        expected.getValue(summary.customTimerName),
                        run.timerInput.stepName,
                    )
                ) {
                    return false
                }
            }
            return true
        }

        suspend fun clean(
            run: MaestroOfflineRun,
            saved: JSONObject? = null,
        ) {
            // 호출자가 DEV 세션과 전용 member ID를 검증한 뒤에만 실행합니다.
            val records = exerciseRecords(run, saved)
            val timers = timerSummaries().filter { it.customTimerName in run.timerNames }
            val memberId = localDataSource.currentMemberId() ?: throw MaestroSetupFailure("member_mismatch")
            exercises.requireSafeCleanup(memberId, run, saved)
            // 양쪽 목록 GET이 성공한 뒤 UUID와 상세를 재확인한 행만 삭제합니다.
            for (record in records) {
                val detail = read(api.getExerciseRecordDetail(record.exerciseId))
                if (detail.exerciseTitle !in run.exerciseTitles) continue
                if (detail.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate()) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
                exercises.requireSafeCleanup(memberId, run, saved, record.exerciseId)
                if (!api.deleteExerciseRecord(record.exerciseId).succeed) throw MaestroSetupFailure("fixture_delete_failed")
            }
            for (timer in timers) {
                val detail = read(api.getCustomTimer(timer.customTimerId))
                if (detail.customTimerId != timer.customTimerId) throw MaestroSetupFailure("fixture_read_failed")
                if (detail.customTimerName !in run.timerNames) continue
                if (!api.deleteCustomTimer(timer.customTimerId, DeleteTimerRequestDTO()).succeed) {
                    throw MaestroSetupFailure("fixture_delete_failed")
                }
            }
        }

        private suspend fun exerciseRecords(
            run: MaestroOfflineRun,
            saved: JSONObject?,
        ): List<GetExerciseRecordListDto> {
            val records = read(api.getExerciseList(run.startedAt.toLocalDate()))
            if (records.any { it.exerciseId <= 0 || it.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate() } ||
                records.map { it.exerciseId }.toSet().size != records.size
            ) {
                throw MaestroSetupFailure("fixture_read_failed")
            }
            val savedServerId =
                saved?.takeIf { it.has(MaestroOfflineExerciseFixture.SERVER_ID) }?.getLong(MaestroOfflineExerciseFixture.SERVER_ID)
            return records.filter { it.exerciseTitle in run.exerciseTitles || it.exerciseId == savedServerId }
        }

        private suspend fun timerSummaries() =
            read(api.getCustomTimerList()).also { list ->
                if (list.customTimerCount != list.customTimers.size || list.customTimers.any { it.customTimerId <= 0 } ||
                    list.customTimers.map { it.customTimerId }.toSet().size != list.customTimers.size
                ) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
            }.customTimers

        private fun <T : Any> read(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("fixture_read_failed")
        }

        companion object {
            private const val PRIVATE_FILE_MODE = 384
            private val FLOWS = setOf("offline-exercise-create", "offline-exercise-missing", "offline-timers-online")

            fun run(request: MaestroFeatureRequest): MaestroOfflineRun {
                MaestroFeaturePolicy.validateIdentity(request.feature, request.flow, request.runId)
                if (request.feature != "offline" || request.flow !in FLOWS ||
                    request.parameters.keys().asSequence().toSet() - "stage" != MaestroOfflineTimerInput.PARAMETER_KEYS
                ) {
                    throw MaestroSetupFailure("invalid_feature_request")
                }
                return try {
                    MaestroOfflineRun(
                        request.runId,
                        LocalDateTime.parse(request.parameters.getString("startedAt")),
                        LocalDateTime.parse(request.parameters.getString("endedAt")),
                        MaestroOfflineTimerInput.parse(request.parameters),
                    )
                } catch (_: Exception) {
                    throw MaestroSetupFailure("invalid_feature_request")
                }
            }

            val LOCAL_STAGES = MaestroOfflineExerciseFixture.LOCAL_STAGES + setOf("timers-local-online", "timers-local-created")
            val STAGES = MaestroOfflineExerciseFixture.SERVER_STAGES + setOf("timers-online", "timers-synced")
            private const val UPLOAD_TIMEOUT_MS = 120_000L

            internal fun matchesTimer(
                detail: GetCustomTimerDetailDTO,
                id: Long,
                name: String,
                seconds: Int,
                stepName: String,
            ): Boolean =
                detail.customTimerId == id && detail.customTimerName == name && detail.customTimerStepCount == 1 &&
                    detail.customTimerSteps.singleOrNull()?.let {
                        it.customTimerStepName == stepName && it.customTimerStepOrder == 0 && it.customTimerStepTime == seconds
                    } == true
        }
    }
