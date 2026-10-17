package com.project200.feature.matching.map

import com.kakao.vectormap.MapAuthException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class MatchingMapErrorKind(val value: String) {
    AUTH("auth"),
    NETWORK("network"),
    RENDERING("rendering"),
    INITIALIZATION("initialization"),
    QUOTA("quota"),
    REQUEST("request"),
    UNKNOWN("unknown"),
}

/** 예외의 원문·원인·URL 대신 SDK 공개 정수 코드와 고정 분류만 보관합니다. */
internal data class MatchingMapFailure(
    val kind: MatchingMapErrorKind,
    val sdkErrorCode: Int?,
) {
    companion object {
        fun from(error: Exception): MatchingMapFailure =
            when (error) {
                is MapAuthException -> {
                    val code = error.getErrorCode()
                    val kind =
                        when (code) {
                            401, 403, MapAuthException.APP_KEY_INVALID_ERROR -> MatchingMapErrorKind.AUTH
                            MapAuthException.CONNECT_INITIATE_FAILURE,
                            MapAuthException.SOCKET_TIMEOUT_EXCEPTION,
                            MapAuthException.CONNECT_TIMEOUT_EXCEPTION,
                            MapAuthException.CONNECT_ERROR,
                            -> MatchingMapErrorKind.NETWORK
                            MapAuthException.INITIALIZE_FAILURE -> MatchingMapErrorKind.INITIALIZATION
                            MapAuthException.RENDER_VIEW_FAILURE -> MatchingMapErrorKind.RENDERING
                            400 -> MatchingMapErrorKind.REQUEST
                            429 -> MatchingMapErrorKind.QUOTA
                            else -> MatchingMapErrorKind.UNKNOWN
                        }
                    MatchingMapFailure(kind, code)
                }
                is ConnectException, is SocketTimeoutException, is UnknownHostException, is SSLException ->
                    MatchingMapFailure(MatchingMapErrorKind.NETWORK, null)
                else -> MatchingMapFailure(MatchingMapErrorKind.UNKNOWN, null)
            }
    }
}

internal enum class MatchingMapStatus(val value: String) {
    LOADING("loading"),
    READY("ready"),
    FAILED("failed"),
}

internal data class MatchingMapReadiness(
    val status: MatchingMapStatus = MatchingMapStatus.LOADING,
    val failure: MatchingMapFailure? = null,
) {
    val testTag: String get() = "matching_map_${status.value}"

    // 같은 지도에서 실패 뒤 늦게 도착한 ready는 성공 증거가 아닙니다.
    fun ready(): MatchingMapReadiness {
        return if (status == MatchingMapStatus.FAILED) {
            this
        } else {
            MatchingMapReadiness(MatchingMapStatus.READY)
        }
    }

    fun failed(error: Exception): MatchingMapReadiness {
        return if (status == MatchingMapStatus.FAILED) {
            this
        } else {
            MatchingMapReadiness(MatchingMapStatus.FAILED, MatchingMapFailure.from(error))
        }
    }
}
