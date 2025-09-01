package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.Step

interface TimerRepository {
    suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>>
    suspend fun editSimpleTimer(simpleTimer: SimpleTimer): BaseResult<Unit>
    suspend fun addSimpleTimer(time: Int): BaseResult<Long>
    suspend fun deleteSimpleTimer(id: Long): BaseResult<Unit>

    suspend fun getCustomTimerList(): BaseResult<List<CustomTimer>> // 커스텀 타이머 전체 조회
    suspend fun getCustomTimer(customTimerId: Long): BaseResult<CustomTimer> // 특정 커스텀 타이머 조회
    suspend fun createCustomTimer(title: String, steps: List<Step>): BaseResult<Long> // 커스텀 타이머 생성
    suspend fun deleteCustomTimer(customTimerId: Long): BaseResult<Unit> // 커스텀 타이머 삭제
    suspend fun editCustomTimerTitle(customTimerId: Long, title: String): BaseResult<Unit> // 커스텀 타이머 이름 수정
    suspend fun editCustomTimer(customTimerId: Long, title: String, steps: List<Step>): BaseResult<Unit> // 커스텀 타이머 전체 수정
}