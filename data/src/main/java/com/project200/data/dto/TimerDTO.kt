package com.project200.data.dto

data class GetSimpleTimersDTO(
    val simpleTimerCount: Int,
    val simpleTimers: List<SimpleTimerDTO>
)

data class SimpleTimerDTO(
    val simpleTimerId: Long,
    val time: Int
)

data class PatchSimpleTimerRequest(
    val time: Int
)

data class GetCustomTimerListDTO(
    val customTimerCount: Int,
    val customTimers: List<CustomTimerSummaryDTO>
)

data class CustomTimerSummaryDTO(
    val customTimerId: Long,
    val customTimerName: String
)

// 커스텀 타이머 상세 조회 DTO
data class GetCustomTimerDetailDTO(
    val customTimerId: Long,
    val customTimerName: String,
    val customTimerStepCount: Int,
    val customTimerSteps: List<CustomTimerDetailStepDTO>
)

data class CustomTimerDetailStepDTO(
    val customTimerStepId: Long,
    val customTimerStepName: String,
    val customTimerStepOrder: Int,
    val customTimerStepTime: Int
)

// 커스텀 타이머 생성 요청 DTO
data class PostCustomTimerRequest(
    val customTimerName: String,
    val customTimerSteps: List<PostCustomTimerStepDTO>
)

data class PostCustomTimerStepDTO(
    val customTimerStepName: String,
    val customTimerStepOrder: Int,
    val customTimerStepTime: Int
)

data class CustomTimerIdDTO(
    val customTimerId: Long
)