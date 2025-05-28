package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseResponse<T>(
    val succeed: Boolean,
    val code: String,
    val message: String,
    val data: T? = null
)