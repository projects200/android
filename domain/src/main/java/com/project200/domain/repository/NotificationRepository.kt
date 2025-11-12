package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType

interface NotificationRepository {
    suspend fun getNotiState(): BaseResult<NotificationState>
    suspend fun updateNotiState(type: NotificationType, enabled: Boolean): BaseResult<Unit>
}