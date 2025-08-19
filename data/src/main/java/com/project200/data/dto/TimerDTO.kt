package com.project200.data.dto

data class GetSimpleTimersDTO(
    val simpleTimerCount: Int,
    val simpleTimers: List<SimpleTimerDTO>
)

data class SimpleTimerDTO(
    val simpleTimerId: Long,
    val time: Int
)

data class PatchSimpleTimerRequest(
    val time: Int
)
