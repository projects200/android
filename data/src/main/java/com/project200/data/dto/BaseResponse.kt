package com.project200.data.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseResponse<T>(
    val succeed: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
)

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    @Json(name = "succeed") val succeed: Boolean,
    @Json(name = "code") val code: String?,
    @Json(name = "message") val message: String?,
)
