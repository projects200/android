package com.project200.feature.matching

import com.project200.domain.model.BaseResult
import java.io.IOException

internal data class MatchingPlaceLoadRequest(
    val sequence: Int,
    val startedAtNanos: Long,
)

/** 오류 원문은 보관하지 않고 기존 결과 코드와 직접 원인 타입만 고정 분류로 바꿉니다. */
internal enum class MatchingPlaceLoadErrorKind(val value: String) {
    NETWORK("network"),
    MAPPING("mapping"),
    AUTH("auth"),
    SERVER("server"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun from(error: BaseResult.Error): MatchingPlaceLoadErrorKind =
            when (val code = error.errorCode) {
                "NETWORK_ERROR" -> NETWORK
                "MAPPING_ERROR" -> MAPPING
                "401", "403", "AUTHENTICATION_FAILED", "USER_ID_HEADER_MISSING" -> AUTH
                else ->
                    if (code != null && code.length == 3 && code[0] == '5' && code[1] in '0'..'9' && code[2] in '0'..'9') {
                        SERVER
                    } else {
                        from(error.cause)
                    }
            }

        fun from(failure: Throwable?): MatchingPlaceLoadErrorKind =
            when (failure) {
                is IOException -> NETWORK
                else -> UNKNOWN
            }
    }
}
