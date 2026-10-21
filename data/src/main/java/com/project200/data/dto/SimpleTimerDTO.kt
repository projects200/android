package com.project200.data.dto

import java.time.LocalDateTime

data class GetSimpleTimersDTO(
    val simpleTimerCount: Int,
    val simpleTimers: List<SimpleTimerDTO>,
)

data class SimpleTimerDTO(
    val simpleTimerId: Long,
    val time: Int,
    val updatedAt: LocalDateTime? = null,
)

data class SimpleTimerRequest(
    val time: Int,
    val createRequestId: String? = null,
    val editId: String? = null,
    val editedAt: Long? = null,
)

data class SimpleTimerIdDTO(
    val simpleTimerId: Long,
    val updatedAt: LocalDateTime? = null,
)
