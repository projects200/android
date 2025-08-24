package com.project200.data.mapper

import com.project200.data.dto.GetCustomTimerDTO
import com.project200.domain.model.CustomTimer
import com.project200.data.dto.GetCustomTimerDTO
import com.project200.domain.model.CustomTimer
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.domain.model.SimpleTimer

fun GetCustomTimerDTO.toModel(): List<CustomTimer> {
    return this.customTimers.map {
        CustomTimer(
            id = it.id,
            name = it.name
        )
    }

fun SimpleTimerDTO.toModel(): SimpleTimer {
    return SimpleTimer(
        id = simpleTimerId,
        time = time
    )
}