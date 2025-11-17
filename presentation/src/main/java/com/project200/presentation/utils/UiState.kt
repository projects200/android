package com.project200.presentation.utils

import android.content.Context
import androidx.core.content.ContextCompat.getString

sealed interface Failure {
    data object NetworkError : Failure
    data class ServerError(val code: String?, val message: String?) : Failure
    data object Unknown : Failure
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val failure: Failure) : UiState<Nothing>
}

sealed interface UiEvent {
    data class ShowToast(val failure: Failure) : UiEvent
}

fun mapCodeToFailure(code: String?, message: String?): Failure {
    return when (code) {
        "NETWORK_ERROR" -> Failure.NetworkError
        "UNKNOWN_ERROR" -> Failure.Unknown
        else -> Failure.ServerError(code, message)
    }
}

/**
 * Failure 객체를 문자열로 변환합니다.
 * @param failure 변환할 Failure 객체
 * @param onServerError ServerError 타입일 경우 실행할 커스텀 로직. null이면 기본 메시지를 사용합니다.
 */
fun Context.mapFailureToString(
    failure: Failure,
    onServerError: ((serverError: Failure.ServerError) -> String)? = null
): String {
    return when (failure) {
        is Failure.NetworkError -> getString(com.project200.undabang.presentation.R.string.network_error)
        is Failure.ServerError -> {
            onServerError?.invoke(failure)
                ?: failure.message
                ?: getString(com.project200.undabang.presentation.R.string.server_error)
        }
        is Failure.Unknown -> getString(com.project200.undabang.presentation.R.string.unknown_error)
    }
}