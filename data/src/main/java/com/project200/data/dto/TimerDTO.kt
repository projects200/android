package com.project200.data.dto

data class GetCustomTimerDTO(
    val customTimerCount: Int,
    val customTimers: List<CustomTimerDTO>
)

data class CustomTimerDTO(
    val id: Long,
    val name: String
)