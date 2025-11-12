package com.project200.data.mapper

import com.project200.data.dto.NotificationStateDTO
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType

fun NotificationStateDTO.toModel(): NotificationState {
    val type = when (this.type) {
        "CHAT_MESSAGE" -> NotificationType.CHAT_MESSAGE
        "WORKOUT_REMINDER" -> NotificationType.WORKOUT_REMINDER
        else -> NotificationType.UNKNOWN
    }

    return NotificationState(
        type = type,
        enabled = this.enabled
    )
}

fun NotificationState.toDTO(): NotificationStateDTO {
    return NotificationStateDTO(
        type = this.type.name,
        enabled = this.enabled
    )
}