package com.project200.domain.usecase

import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.repository.AppUpdateRepository
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) {
    suspend operator fun invoke(): Result<UpdateCheckResult> {
        return appUpdateRepository.getUpdateInfo().mapCatching { updateInfo ->
            val currentVersionCode = appUpdateRepository.getCurrentVersionCode()

            val needsUpdate = updateInfo.latestVersionCode > currentVersionCode
            // 강제 업데이트 여부
            val isForceUpdate = updateInfo.minRequiredVersionCode > currentVersionCode

            if (needsUpdate) {
                UpdateCheckResult.UpdateAvailable(isForceUpdate = isForceUpdate)
            } else {
                UpdateCheckResult.NoUpdateNeeded
            }
        }
    }
}