package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer

interface TimerRepository {
    suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>>
    suspend fun editSimpleTimer(simpleTimer: SimpleTimer): BaseResult<Unit>
}