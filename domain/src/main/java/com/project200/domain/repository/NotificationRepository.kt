package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState

interface NotificationRepository {
    suspend fun getNotiState(): BaseResult<NotificationState>
}