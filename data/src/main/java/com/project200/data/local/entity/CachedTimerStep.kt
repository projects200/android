package com.project200.data.local.entity

import com.squareup.moshi.JsonClass

/**
 * 커스텀 타이머의 스텝 한 줄입니다.
 *
 * 서버 스텝 ID를 담지 않습니다. 서버 수정이 전체 교체(PUT)라서 스텝 ID가 왕복에서
 * 유지된다는 보장이 없습니다
 */
@JsonClass(generateAdapter = true)
data class CachedTimerStep(
    val order: Int,
    val name: String,
    val time: Int,
)
