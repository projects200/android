package com.project200.data.utils

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ErrorResponse
import com.project200.domain.model.BaseResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

suspend fun <DTO, Domain> apiCallBuilder(
    ioDispatcher: CoroutineDispatcher,
    apiCall: suspend () -> BaseResponse<DTO>,
    mapper: (dto: DTO?) -> Domain,
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
                            cause = e,
                        )
                    }
                } else { // response.succeed == false (서버에서 정의한 비즈니스 오류)
                    Timber.w("API call failed server-side. Code: ${response.code}, Message: ${response.message}")
                    BaseResult.Error(
                        errorCode = response.code,
                        message = response.message,
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
                            cause = exception,
                        )
                    }
                    is retrofit2.HttpException -> { // HTTP 상태 코드 오류
                        val errorBody = exception.response()?.errorBody()?.string()
                        var errorMessage = errorBody // 기본값은 파싱 전 원본 문자열

                        Timber.d("errorBody: $errorBody")
                        if (!errorBody.isNullOrBlank()) {
                            try {
                                val moshi =
                                    Moshi.Builder()
                                        .add(KotlinJsonAdapterFactory())
                                        .build()

                                val adapter = moshi.adapter(ErrorResponse::class.java)
                                val errorResponse = adapter.fromJson(errorBody)

                                // 파싱 성공 후 메시지가 있다면 사용
                                if (errorResponse != null && !errorResponse.message.isNullOrEmpty()) {
                                    Timber.d("Parsed error message: ${errorResponse.message}")
                                    errorMessage = errorResponse.message
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to parse error body JSON with Moshi.")
                            }
                        }
                        BaseResult.Error(
                            errorCode = exception.code().toString(),
                            message = errorMessage, // 추출한 메시지 또는 원본 문자열 사용
                            cause = exception,
                        )
                    }
                    else -> { // 기타 모든 예외
                        BaseResult.Error(
                            errorCode = "UNKNOWN_ERROR",
                            message = "알 수 없는 오류가 발생했습니다.",
                            cause = exception,
                        )
                    }
                }
            },
        )
    }
}
