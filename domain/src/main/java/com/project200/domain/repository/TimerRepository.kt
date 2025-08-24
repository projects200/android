package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.CustomTimer

interface TimerRepository {
    suspend fun getCustomTimers(): BaseResult<List<CustomTimer>>
    suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>>
    suspend fun editSimpleTimer(simpleTimer: SimpleTimer): BaseResult<Unit>
}