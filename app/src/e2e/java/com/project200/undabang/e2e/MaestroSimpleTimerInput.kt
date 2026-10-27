package com.project200.undabang.e2e

import org.json.JSONObject
import java.util.Locale

internal data class MaestroSimpleTimerInput(
    val initialSeconds: Int,
    val updatedSeconds: Int,
) {
    fun variables(): Map<String, String> =
        buildMap {
            putMaestroTimerTimes(initialSeconds, updatedSeconds)
        }

    fun json(): JSONObject =
        JSONObject().put("initialSeconds", initialSeconds.toString()).put("updatedSeconds", updatedSeconds.toString())

    companion object {
        fun parse(parameters: JSONObject): MaestroSimpleTimerInput {
            val keys = parameters.keys().asSequence().toSet()
            if (keys - "stage" != setOf("initialSeconds", "updatedSeconds") ||
                (parameters.has("stage") && parameters.get("stage") !is String)
            ) {
                throw MaestroSetupFailure("invalid_feature_request")
            }
            return MaestroSimpleTimerInput(
                readMaestroTimerSeconds(parameters, "initialSeconds"),
                readMaestroTimerSeconds(parameters, "updatedSeconds"),
            )
        }
    }
}

internal fun readMaestroTimerSeconds(
    parameters: JSONObject,
    key: String,
): Int {
    val text = parameters.get(key) as? String ?: throw MaestroSetupFailure("invalid_feature_request")
    val value = text.takeIf { TIMER_SECONDS_PATTERN.matches(it) }?.toIntOrNull()
    return value?.takeIf { it in 1..3599 } ?: throw MaestroSetupFailure("invalid_feature_request")
}

internal fun MutableMap<String, String>.putMaestroTimerTimes(
    initialSeconds: Int,
    updatedSeconds: Int,
) {
    fun putTime(
        label: String,
        seconds: Int,
    ) {
        val minutes = seconds / 60
        val secondsPart = seconds % 60
        put("TIMER_${label}_SECONDS", seconds.toString())
        put("TIMER_${label}_MINUTES_PART", minutes.toString())
        put("TIMER_${label}_SECONDS_PART", secondsPart.toString())
        put("TIMER_${label}_TIME", String.format(Locale.ROOT, "%02d:%02d", minutes, secondsPart))
    }

    putTime("INITIAL", initialSeconds)
    putTime("UPDATED", updatedSeconds)
}

private val TIMER_SECONDS_PATTERN = Regex("[1-9][0-9]{0,3}")
