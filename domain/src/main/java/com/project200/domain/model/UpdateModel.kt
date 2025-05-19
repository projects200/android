package com.project200.domain.model

data class UpdateInfo(
    val latestVersionCode: Long,
    val minRequiredVersionCode: Long
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val isForceUpdate: Boolean) : UpdateCheckResult
    data object NoUpdateNeeded : UpdateCheckResult
}