package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType
import com.project200.domain.repository.NotificationRepository
import jdk.jfr.Enabled
import javax.inject.Inject

class UpdateNotificationStateUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(type: NotificationType, enabled: Boolean): BaseResult<Unit> {
        return notificationRepository.updateNotiState(type, enabled)
    }
}