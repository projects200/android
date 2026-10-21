package com.project200.data.dto

import java.time.LocalDateTime

data class TimerMutationResponseDTO(
    val updatedAt: LocalDateTime? = null,
)

data class DeleteTimerRequestDTO(
    val editId: String? = null,
    val editedAt: Long? = null,
)
