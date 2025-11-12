package com.project200.data.dto

import com.project200.domain.model.NotificationState

data class GetNotificationStateDTO(
    val exerciseEncouragement: Boolean,
    val chatAlarm: Boolean,
)

data class PatchNotificationStateRequest(
    val type: String,
    val enabled: Boolean,
)