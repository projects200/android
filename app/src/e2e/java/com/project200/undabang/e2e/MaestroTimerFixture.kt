package com.project200.undabang.e2e

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.project200.data.api.ApiService
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.DeleteTimerRequestDTO
import com.project200.data.dto.GetSimpleTimersDTO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

internal class MaestroTimerFixture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: ApiService,
        private val localDataSource: TimerLocalDataSource,
    ) : MaestroFeatureFixture {
        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            requireRequest(request)
            if (stateFile().exists()) fail("timer_recovery_required")

            val baselineLocalIds = localDataSource.getSimpleTimers().map { it.localId }
            val baselineServer = read(api.getSimpleTimers())
            val baselineCustom = read(api.getCustomTimerList())
            writeState(
                JSONObject()
                    .put("memberId", request.memberId)
                    .put("runId", request.runId)
                    .put("flow", request.flow)
                    .put("baselineLocalIds", JSONArray(baselineLocalIds))
                    .put("baselineServer", simpleTimersJson(baselineServer))
                    .put("baselineCustomServer", customTimersJson(baselineCustom))
                    .put("ownedServerIds", JSONArray())
                    .put("ownedCustomServerIds", JSONArray()),
            )
            return emptyMap()
        }

        override suspend fun observe(
            request: MaestroFeatureRequest,
            stage: String,
        ): Map<String, String> {
            requireRequest(request)
            val state = readState(request)
            return when (stage) {
                "simple-baseline" -> observeBaseline(state)
                "simple-created" -> observeCreated(request, state)
                "simple-synced" -> observeSynced(request, state)
                "simple-deleted" -> {
                    captureOwnedServerId(state)
                    writeState(state)
                    emptyMap()
                }
                else -> fail("invalid_request")
            }
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            requireRequest(request)
            if (!stateFile().exists()) return
            val state = readState(request)
            captureOwnedServerId(state)
            val baseline = baselineServer(state)
            val ownedIds = ownedServerIds(state)
            writeState(state)
            val current = read(api.getSimpleTimers())
            for (serverId in ownedIds) {
                if (serverId in baseline.keys) fail("timer_server_ownership_changed")
                if (current.simpleTimers.any { it.simpleTimerId == serverId }) {
                    if (!api.deleteSimpleTimer(serverId, DeleteTimerRequestDTO()).succeed) {
                        fail("timer_server_cleanup_failed")
                    }
                }
            }
            val remaining = read(api.getSimpleTimers())
            if (remaining.simpleTimers.any { it.simpleTimerId in ownedIds }) fail("timer_server_cleanup_failed")
            if (baseline.any { (id, time) -> remaining.simpleTimers.singleOrNull { it.simpleTimerId == id }?.time != time }) {
                fail("timer_server_baseline_changed")
            }
            val baselineCustom = baselineCustomServer(state)
            val currentCustom = read(api.getCustomTimerList())
            val marker = customMarker(request)
            val ownedCustom =
                currentCustom.customTimers.filter {
                    it.customTimerId !in baselineCustom.keys && it.customTimerName.startsWith(marker)
                }
            val ownedCustomIds = ownedCustom.map { it.customTimerId }.toSet()
            state.put("ownedCustomServerIds", JSONArray(ownedCustomIds.toList().sorted()))
            writeState(state)
            for (timer in ownedCustom) {
                val detail = read(api.getCustomTimer(timer.customTimerId))
                if (detail.customTimerName != timer.customTimerName || !timer.customTimerName.startsWith(marker)) {
                    fail("timer_server_ownership_changed")
                }
                if (!api.deleteCustomTimer(timer.customTimerId, DeleteTimerRequestDTO()).succeed) {
                    fail("timer_server_cleanup_failed")
                }
            }
            val finalCustom = read(api.getCustomTimerList())
            if (finalCustom.customTimers.any { it.customTimerId in ownedCustomIds }) {
                fail("timer_server_cleanup_failed")
            }
            deleteState()
        }

        private suspend fun observeBaseline(state: JSONObject): Map<String, String> {
            state.put("baselineLocalIds", JSONArray(localDataSource.getSimpleTimers().map { it.localId }))
            writeState(state)
            return emptyMap()
        }

        private suspend fun observeCreated(
            request: MaestroFeatureRequest,
            state: JSONObject,
        ): Map<String, String> {
            val baseline = stringArray(state.getJSONArray("baselineLocalIds")).toSet()
            val current = localDataSource.getSimpleTimers()
            val created = current.filterNot { it.localId in baseline }
            if (created.size != 1) fail("timer_local_id_missing")
            val timer = created.single()
            state.put("simpleLocalId", timer.localId)
                .put("createRequestId", timer.createRequestId)
                .put("createdTime", timer.time)
            writeState(state)
            return mapOf("TIMER_LOCAL_ID" to timer.localId)
        }

        private suspend fun observeSynced(
            request: MaestroFeatureRequest,
            state: JSONObject,
        ): Map<String, String> {
            val localId = state.optString("simpleLocalId").takeIf { it.isNotBlank() } ?: fail("timer_local_id_missing")
            val expectedTime =
                if (request.flow == "timer-simple-preset-crud") 42 else state.getInt("createdTime")
            var observed: Long? = null
            withTimeoutOrNull(SERVER_ID_TIMEOUT_MS) {
                while (observed == null) {
                    val local = localDataSource.getSimpleTimer(localId)
                    val serverId = local?.serverId
                    if (serverId != null && local.time == expectedTime) {
                        val server = read(api.getSimpleTimers()).simpleTimers.singleOrNull { it.simpleTimerId == serverId }
                        if (server?.time == expectedTime) observed = serverId
                    }
                    if (observed == null) delay(POLL_INTERVAL_MS)
                }
            }
            val confirmedServerId = observed ?: fail("timer_server_id_missing")
            val owned = ownedServerIds(state).toMutableSet()
            owned += confirmedServerId
            state.put("ownedServerIds", JSONArray(owned.toList().sorted()))
            writeState(state)
            return mapOf("TIMER_LOCAL_ID" to localId)
        }

        private suspend fun captureOwnedServerId(state: JSONObject) {
            val localId = state.optString("simpleLocalId").takeIf { it.isNotBlank() } ?: return
            val local = localDataSource.getSimpleTimer(localId) ?: return
            val serverId = local.serverId ?: return
            val baseline = baselineServer(state)
            if (serverId in baseline.keys) fail("timer_server_ownership_changed")
            val owned = ownedServerIds(state).toMutableSet()
            owned += serverId
            state.put("ownedServerIds", JSONArray(owned.toList().sorted()))
        }

        private fun requireRequest(request: MaestroFeatureRequest) {
            if (request.feature != "timer" || request.flow !in FLOWS ||
                !Regex("[a-f0-9]{16,32}").matches(request.runId) ||
                request.parameters.keys().asSequence().any { it != "stage" }
            ) {
                fail("invalid_feature_request")
            }
        }

        private fun readState(request: MaestroFeatureRequest): JSONObject {
            if (!stateFile().isFile) fail("timer_snapshot_missing")
            val state = readJson(stateFile())
            if (state.getString("memberId") != request.memberId ||
                state.getString("runId") != request.runId ||
                state.getString("flow") != request.flow
            ) {
                fail("timer_snapshot_identity_invalid")
            }
            return state
        }

        private fun baselineCustomServer(state: JSONObject): Map<Long, String> =
            objects(state.getJSONArray("baselineCustomServer")).associate { it.getLong("id") to it.getString("name") }

        private fun customMarker(request: MaestroFeatureRequest): String =
            when (request.flow) {
                "timer-custom-crud" -> "e2e611c-${request.runId}"
                "timer-custom-run" -> "e2e611r-${request.runId}"
                else -> "e2e611v-${request.runId}"
            }

        private fun baselineServer(state: JSONObject): Map<Long, Int> =
            objects(state.getJSONArray("baselineServer")).associate { it.getLong("id") to it.getInt("time") }

        private fun ownedServerIds(state: JSONObject): Set<Long> =
            (0 until state.getJSONArray("ownedServerIds").length())
                .map { state.getJSONArray("ownedServerIds").getLong(it) }
                .toSet()

        private fun simpleTimersJson(response: GetSimpleTimersDTO): JSONArray =
            JSONArray().apply {
                response.simpleTimers.forEach { put(JSONObject().put("id", it.simpleTimerId).put("time", it.time)) }
            }

        private fun customTimersJson(response: com.project200.data.dto.GetCustomTimerListDTO): JSONArray =
            JSONArray().apply {
                response.customTimers.forEach {
                    put(JSONObject().put("id", it.customTimerId).put("name", it.customTimerName))
                }
            }

        private fun stringArray(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }

        private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map { array.getJSONObject(it) }

        private fun <T : Any> read(response: BaseResponse<T>): T {
            if (!response.succeed) fail("timer_fixture_read_failed")
            return response.data ?: fail("timer_fixture_read_failed")
        }

        private fun stateFile(): File = File(context.filesDir, STATE_FILE)

        private fun writeState(json: JSONObject) {
            val atomic = AtomicFile(stateFile())
            val stream = atomic.startWrite()
            try {
                Os.fchmod(stream.fd, PRIVATE_FILE_MODE)
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (_: Exception) {
                atomic.failWrite(stream)
                fail("timer_private_file_failed")
            }
        }

        private fun readJson(file: File): JSONObject =
            JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })

        private fun deleteState() {
            AtomicFile(stateFile()).delete()
            if (stateFile().exists() || File(context.filesDir, "$STATE_FILE.new").exists() ||
                File(context.filesDir, "$STATE_FILE.bak").exists()
            ) {
                fail("timer_private_file_failed")
            }
        }

        private fun fail(reason: String): Nothing = throw MaestroSetupFailure(reason)

        companion object {
            private const val STATE_FILE = "maestro-timer-state.json"
            private const val PRIVATE_FILE_MODE = 384
            private const val POLL_INTERVAL_MS = 2_000L
            private const val SERVER_ID_TIMEOUT_MS = 120_000L
            private val FLOWS =
                setOf(
                    "timer-simple-preset-crud",
                    "timer-simple-run",
                    "timer-custom-crud",
                    "timer-custom-run",
                    "timer-form-validation",
                )
        }
    }
