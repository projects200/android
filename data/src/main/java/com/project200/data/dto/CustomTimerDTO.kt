package com.project200.data.dto

import java.time.LocalDateTime

data class GetCustomTimerListDTO(
    val customTimerCount: Int,
    val customTimers: List<CustomTimerSummaryDTO>,
)

data class CustomTimerSummaryDTO(
    val customTimerId: Long,
    val customTimerName: String,
    val updatedAt: LocalDateTime? = null,
)

data class GetCustomTimerDetailDTO(
    val customTimerId: Long,
    val customTimerName: String,
    val customTimerStepCount: Int,
    val customTimerSteps: List<CustomTimerDetailStepDTO>,
    val updatedAt: LocalDateTime? = null,
)

data class CustomTimerDetailStepDTO(
    val customTimerStepId: Long,
    val customTimerStepName: String,
    val customTimerStepOrder: Int,
    val customTimerStepTime: Int,
)

data class PostCustomTimerRequest(
    val customTimerName: String,
    val customTimerSteps: List<PostCustomTimerStepDTO>,
    val createRequestId: String? = null,
    val editedAt: Long? = null,
    val editId: String? = null,
)

data class PostCustomTimerStepDTO(
    val customTimerStepName: String,
    val customTimerStepOrder: Int,
    val customTimerStepTime: Int,
)

data class CustomTimerIdDTO(
    val customTimerId: Long,
    val updatedAt: LocalDateTime? = null,
)

data class PatchCustomTimerTitleRequest(
    val customTimerName: String,
    val editId: String? = null,
    val editedAt: Long? = null,
)
