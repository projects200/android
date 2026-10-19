package com.project200.domain.model

sealed class BaseResult<out T> {
    /** refreshError는 갱신 오류이며, isCachedSnapshot은 이번 조회에서 서버 확인 없이 반환한 값입니다. */
    data class Success<T>(
        val data: T,
        val refreshError: Error? = null,
        val isCachedSnapshot: Boolean = false,
    ) : BaseResult<T>()
    data class Error(
        val errorCode: String? = null,
        val message: String?,
        val cause: Throwable? = null
    ) : BaseResult<Nothing>()
}