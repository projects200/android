package com.project200.data.mapper

import com.project200.data.dto.GetNotificationStateDTO
import com.project200.domain.model.NotificationState

fun GetNotificationStateDTO.toModel() = NotificationState(
    exerciseEncouragement = this.exerciseEncouragement,
    chatAlarm = this.chatAlarm,
)