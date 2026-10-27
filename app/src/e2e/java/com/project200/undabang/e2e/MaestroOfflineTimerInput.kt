package com.project200.undabang.e2e

import org.json.JSONObject

internal data class MaestroOfflineTimerInput(
    val initialSeconds: Int,
    val updatedSeconds: Int,
    val stepName: String,
) {
    init {
        if (initialSeconds !in 5..3599 || updatedSeconds !in 5..3599 || initialSeconds == updatedSeconds ||
            !Regex("[A-Za-z0-9][A-Za-z0-9 _-]{0,39}").matches(stepName)
        ) {
            throw MaestroSetupFailure("invalid_feature_request")
        }
    }

    fun variables(): Map<String, String> =
        buildMap {
            putMaestroTimerTimes(initialSeconds, updatedSeconds)
            put("TIMER_STEP_NAME", stepName)
        }

    companion object {
        val PARAMETER_KEYS = setOf("startedAt", "endedAt", "initialSeconds", "updatedSeconds", "stepName")

        fun parse(parameters: JSONObject): MaestroOfflineTimerInput {
            if (parameters.keys().asSequence().toSet() - "stage" != PARAMETER_KEYS ||
                PARAMETER_KEYS.any { parameters.get(it) !is String } ||
                (parameters.has("stage") && parameters.get("stage") !is String)
            ) {
                throw MaestroSetupFailure("invalid_feature_request")
            }
            return MaestroOfflineTimerInput(
                readMaestroTimerSeconds(parameters, "initialSeconds"),
                readMaestroTimerSeconds(parameters, "updatedSeconds"),
                parameters.getString("stepName"),
            )
        }
    }
}
