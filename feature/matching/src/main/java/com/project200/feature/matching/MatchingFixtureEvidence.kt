package com.project200.feature.matching

import android.content.Context
import android.content.pm.ApplicationInfo
import android.system.Os
import android.util.AtomicFile
import com.project200.domain.model.ExercisePlace
import com.project200.feature.matching.map.MatchingMapLabelKind
import com.project200.feature.matching.map.MatchingMapReadiness
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 실제 UI의 서버 응답·지도 상태를 기록하며, e2e 준비 파일이 없는 일반 앱에서는 파일을 만들지 않습니다. */
object MatchingFixtureEvidence {
    const val FILE_NAME = "maestro-matching-events.json"

    fun places(
        context: Context,
        places: List<ExercisePlace>,
    ) {
        if (!isArmed(context)) return
        append(context) {
            put("type", "places")
            put(
                "places",
                JSONArray().apply {
                    places.forEach { place ->
                        put(
                            JSONObject()
                                .put("id", place.id)
                                .put("name", place.name)
                                .put("address", place.address)
                                .put("latitude", place.latitude)
                                .put("longitude", place.longitude),
                        )
                    }
                },
            )
        }
    }

    fun chat(
        context: Context,
        peerId: String,
        locationId: Long,
        roomId: Long,
    ) {
        if (!isArmed(context)) return
        append(context) {
            put("type", "chat")
            put("peerId", peerId)
            put("locationId", locationId)
            put("roomId", roomId)
        }
    }

    fun denied(
        context: Context,
        peerId: String,
        locationId: Long,
    ) {
        if (!isArmed(context)) return
        append(context) {
            put("type", "denied")
            put("peerId", peerId)
            put("locationId", locationId)
        }
    }

    /** start의 순번을 지도 인스턴스 식별자로 사용하며 회원·장소 값은 포함하지 않습니다. */
    internal fun mapStarted(context: Context): Int? {
        if (!isArmed(context)) return null
        return append(context) { sequence ->
            put("type", "map")
            put("sequence", sequence)
            put("attempt", sequence)
            put("state", "loading")
        }
    }

    internal fun map(
        context: Context,
        attempt: Int?,
        readiness: MatchingMapReadiness,
    ) {
        if (attempt == null || !isArmed(context)) return
        append(context) { sequence ->
            put("type", "map")
            put("sequence", sequence)
            put("attempt", attempt)
            put("state", readiness.status.value)
            readiness.failure?.let { failure ->
                put("category", failure.kind.value)
                put("sdkErrorCode", failure.sdkErrorCode ?: JSONObject.NULL)
            }
        }
    }

    /** 실제 SDK 클릭의 고정 종류만 기록하며 좌표·태그 객체·회원 값은 복사하지 않습니다. */
    internal fun mapLabelClick(
        context: Context,
        attempt: Int?,
        kind: MatchingMapLabelKind,
    ) {
        if (attempt == null || !isArmed(context)) return
        append(context) { sequence ->
            put("type", "map_label_click")
            put("sequence", sequence)
            put("attempt", attempt)
            put("kind", kind.value)
        }
    }

    /** 일반 UI와 E2E 전용 수집·접근성 표식을 같은 디버그/증거 파일 경계로 구분합니다. */
    fun isArmed(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            File(context.filesDir, FILE_NAME).isFile

    @Synchronized
    private fun append(
        context: Context,
        event: JSONObject.(Int) -> Unit,
    ): Int? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return null
        val atomic = AtomicFile(file)
        val document = JSONObject(atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        val events = document.getJSONArray("events")
        check(events.length() < 1000) { "matching_evidence_limit" }
        val sequence = events.length() + 1
        events.put(JSONObject().apply { event(sequence) })
        val stream = atomic.startWrite()
        try {
            Os.fchmod(stream.fd, 384) // 0600: 닉네임·주소와 탐색 증거는 앱 내부에만 보관합니다.
            stream.write(document.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (_: Exception) {
            atomic.failWrite(stream)
            throw IllegalStateException("matching_evidence_write_failed")
        }
        return sequence
    }
}
