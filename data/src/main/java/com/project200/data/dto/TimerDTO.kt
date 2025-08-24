package com.project200.data.dto


data class GetCustomTimerDTO(
    val customTimerCount: Int,
    val customTimers: List<CustomTimerDTO>
)

data class CustomTimerDTO(
    val id: Long,
    val name: String
)

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

