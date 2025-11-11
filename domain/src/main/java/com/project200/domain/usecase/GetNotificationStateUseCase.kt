package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.repository.NotificationRepository
import javax.inject.Inject

class GetNotificationStateUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    suspend operator fun invoke(): BaseResult<NotificationState> {
        return notificationRepository.getNotiState()
    }
}