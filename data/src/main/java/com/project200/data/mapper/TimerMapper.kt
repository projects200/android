package com.project200.data.mapper

import com.project200.data.datasource.ServerCustomTimer
import com.project200.data.datasource.ServerSimpleTimer
import com.project200.data.dto.CustomTimerDetailStepDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.PostCustomTimerStepDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.Step

fun SimpleTimerEntity.toModel(): SimpleTimer {
    return SimpleTimer(
        localId = localId,
        serverId = serverId,
        time = time,
        isSyncPending = syncState != SyncState.SYNCED,
    )
}

fun CustomTimerEntity.toModel(): CustomTimer {
    return CustomTimer(
        localId = localId,
        serverId = serverId,
        name = name,
        steps = steps.sortedBy { it.order }.toSteps(),
        isSyncPending = syncState != SyncState.SYNCED,
    )
}

fun List<Step>.toCachedSteps(): List<CachedTimerStep> {
    return map { CachedTimerStep(order = it.order, name = it.name, time = it.time) }
}

fun List<CachedTimerStep>.toSteps(): List<Step> {
    return map { Step(order = it.order, name = it.name, time = it.time) }
}

fun SimpleTimerDTO.toServerModel(): ServerSimpleTimer {
    return ServerSimpleTimer(
        serverId = simpleTimerId,
        time = time,
    )
}

fun GetCustomTimerDetailDTO.toServerModel(): ServerCustomTimer {
    return ServerCustomTimer(
        serverId = customTimerId,
        name = customTimerName,
        steps = customTimerSteps.sortedBy { it.customTimerStepOrder }.map { it.toCachedStep() },
    )
}

private fun CustomTimerDetailStepDTO.toCachedStep(): CachedTimerStep {
    return CachedTimerStep(
        order = customTimerStepOrder,
        name = customTimerStepName,
        time = customTimerStepTime,
    )
}

// 서버 생성/전체 수정 요청에 씁니다. 서버 전송은 #588 워커가 맡습니다
fun List<Step>.toDTO(): List<PostCustomTimerStepDTO> {
    return map {
        PostCustomTimerStepDTO(
            customTimerStepName = it.name,
            customTimerStepOrder = it.order,
            customTimerStepTime = it.time,
        )
    }
}
