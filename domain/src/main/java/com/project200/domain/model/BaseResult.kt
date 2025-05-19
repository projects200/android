package com.project200.domain.model

sealed class BaseResult<out T> {
    data class Success<T>(val data: T) : BaseResult<T>()
    data class Error(
        val errorCode: String? = null,
        val message: String?,
        val cause: Throwable? = null
    ) : BaseResult<Nothing>()
}