package com.project200.data.mapper

import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.domain.model.SimpleTimer

fun SimpleTimerDTO.toModel(): SimpleTimer {
    return SimpleTimer(
        id = simpleTimerId,
        time = time
    )
}