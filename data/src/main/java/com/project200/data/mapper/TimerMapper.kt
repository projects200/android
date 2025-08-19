package com.project200.data.mapper

import com.project200.data.dto.GetCustomTimerDTO
import com.project200.domain.model.CustomTimer

fun GetCustomTimerDTO.toModel(): List<CustomTimer> {
    return this.customTimers.map {
        CustomTimer(
            id = it.id,
            name = it.name
        )
    }
}