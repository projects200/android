package com.project200.data.mapper

import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.PostCustomTimerStepDTO
import com.project200.domain.model.CustomTimer
import com.project200.data.dto.SimpleTimerDTO
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step

fun SimpleTimerDTO.toModel(): SimpleTimer {
    return SimpleTimer(
        id = simpleTimerId,
        time = time
    )
}

fun GetCustomTimerListDTO.toModel(): List<CustomTimer> {
    return this.customTimers.map {
        CustomTimer(
            id = it.customTimerId,
            name = it.customTimerName
        )
    }
}

fun GetCustomTimerDetailDTO.toModel(): CustomTimer {
    return CustomTimer(
        id = this.customTimerId,
        name = this.customTimerName,
        steps = this.customTimerSteps.map {
            Step(
                id = it.customTimerStepId,
                order = it.customTimerStepOrder,
                time = it.customTimerStepTime,
                name = it.customTimerStepName
            )
        }
    )
}

fun List<Step>.toDTO(): List<PostCustomTimerStepDTO> {
    return this.map {
        PostCustomTimerStepDTO(
            customTimerStepName = it.name,
            customTimerStepOrder = it.order,
            customTimerStepTime = it.time
        )
    }
}