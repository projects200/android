package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.model.CustomTimer
import com.project200.domain.model.Step

interface TimerRepository {
    suspend fun getSimpleTimers(): BaseResult<List<SimpleTimer>> // 심플 타이머 전체 조회
    suspend fun editSimpleTimer(simpleTimer: SimpleTimer): BaseResult<Unit> // 심플 타이머 수정

    suspend fun getCustomTimerList(): BaseResult<List<CustomTimer>> // 커스텀 타이머 전체 조회
    suspend fun getCustomTimer(customTimerId: Long): BaseResult<CustomTimer> // 특정 커스텀 타이머 조회
    suspend fun createCustomTimer(title: String, steps: List<Step>): BaseResult<Long> // 커스텀 타이머 생성
    suspend fun deleteCustomTimer(customTimerId: Long): BaseResult<Unit> // 커스텀 타이머 삭제
    suspend fun editCustomTimerTitle(customTimerId: Long, title: String): BaseResult<Long> // 커스텀 타이머 수정
}