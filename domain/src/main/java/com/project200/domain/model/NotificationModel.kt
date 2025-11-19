package com.project200.domain.model

data class NotificationState(
    val type: NotificationType,
    val enabled: Boolean,
)

enum class NotificationType {
    CHAT_MESSAGE, // 채팅 알림
    WORKOUT_REMINDER, // 운동 독려 알림
    UNKNOWN, // 알 수 없는 타입
}