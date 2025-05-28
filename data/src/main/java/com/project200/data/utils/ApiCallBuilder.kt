package com.project200.data.utils

import com.project200.data.dto.BaseResponse
import com.project200.domain.model.BaseResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.NoSuchElementException
import kotlinx.coroutines.CancellationException

suspend fun <DTO, Domain> apiCallBuilder(
    ioDispatcher: CoroutineDispatcher,
    apiCall: suspend () -> BaseResponse<DTO>,
    mapper: (dto: DTO?) -> Domain
): BaseResult<Domain> {
    return withContext(ioDispatcher) {
        runCatching {
            apiCall()
        }.fold(
            onSuccess = { response ->
                if (response.succeed) {
                    try {
                        BaseResult.Success(mapper(response.data))
                    } catch (e: Exception) {
                        Timber.e(e, "Data mapping failed")
                        BaseResult.Error(
                            errorCode = "MAPPING_ERROR",
                            message = "데이터를 변환하는 중 오류가 발생했습니다.",
                            cause = e
                        )
                    }
                } else { // response.succeed == false (서버에서 정의한 비즈니스 오류)
                    Timber.w("API call failed server-side. Code: ${response.code}, Message: ${response.message}")
                    BaseResult.Error(
                        errorCode = response.code ?: "UNKNOWN_SERVER_ERROR",
                        message = response.message ?: "알 수 없는 서버 오류가 발생했습니다."
                    )
                }
            },
            onFailure = { exception -> // apiCall() 자체에서 예외 발생 시
                Timber.e(exception, "API call failed with exception.")
                when (exception) {
                    is CancellationException -> throw exception
                    is IOException -> {
                        BaseResult.Error(
                            errorCode = "NETWORK_ERROR",
                            message = "네트워크 연결 오류가 발생했습니다.",
                            cause = exception
                        )
                    }
                    is retrofit2.HttpException -> { // HTTP 상태 코드 오류
                        val errorBody = exception.response()?.errorBody()?.string()
                        BaseResult.Error(
                            errorCode = exception.code().toString(),
                            message = "HTTP 오류 ${exception.code()}: ${errorBody ?: exception.message()}",
                            cause = exception
                        )
                    }
                    else -> { // 기타 모든 예외
                        BaseResult.Error(
                            errorCode = "UNKNOWN_ERROR",
                            message = "알 수 없는 오류가 발생했습니다.",
                            cause = exception
                        )
                    }
                }
            }
        )
    }
}