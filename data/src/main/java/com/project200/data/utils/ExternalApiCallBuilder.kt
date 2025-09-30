package com.project200.data.utils

import com.project200.domain.model.BaseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * Kakao API와 같이 BaseResponse 래퍼가 없는 API 호출을 위한 빌더 함수
 */
suspend fun <DTO, Domain> externalApiCallBuilder(
    ioDispatcher: CoroutineDispatcher,
    apiCall: suspend () -> DTO,
    mapper: (dto: DTO) -> Domain,
): BaseResult<Domain> {
    return withContext(ioDispatcher) {
        runCatching {
            apiCall()
        }.fold(
            onSuccess = { responseDto ->
                try {
                    BaseResult.Success(mapper(responseDto))
                } catch (e: Exception) {
                    Timber.e(e, "Data mapping failed for Kakao API")
                    BaseResult.Error(
                        errorCode = "MAPPING_ERROR",
                        message = "데이터를 변환하는 중 오류가 발생했습니다.",
                        cause = e,
                    )
                }
            },
            onFailure = { exception ->
                Timber.e(exception, "Kakao API call failed with exception.")
                when (exception) {
                    is CancellationException -> throw exception
                    is IOException -> {
                        BaseResult.Error(
                            errorCode = "NETWORK_ERROR",
                            message = "네트워크 연결 오류가 발생했습니다.",
                            cause = exception,
                        )
                    }
                    is retrofit2.HttpException -> {
                        val errorBody = exception.response()?.errorBody()?.string()
                        BaseResult.Error(
                            errorCode = exception.code().toString(),
                            message = "외부 API 오류 ${exception.code()}: ${errorBody ?: exception.message()}",
                            cause = exception,
                        )
                    }
                    else -> {
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