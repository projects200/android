package com.project200.feature.matching

import android.content.Context
import android.content.pm.ApplicationInfo
import android.system.Os
import android.util.AtomicFile
import com.project200.domain.model.ExercisePlace
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 실제 UI의 서버 응답만 기록하며, e2e 준비 파일이 없는 일반 앱에서는 파일을 만들지 않습니다. */
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

    /** 일반 UI와 E2E 전용 수집·접근성 표식을 같은 디버그/증거 파일 경계로 구분합니다. */
    fun isArmed(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            File(context.filesDir, FILE_NAME).isFile

    @Synchronized
    private fun append(
        context: Context,
        event: JSONObject.() -> Unit,
    ) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return
        val atomic = AtomicFile(file)
        val document = JSONObject(atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        val events = document.getJSONArray("events")
        check(events.length() < 1000) { "matching_evidence_limit" }
        events.put(JSONObject().apply(event))
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
    }
}
