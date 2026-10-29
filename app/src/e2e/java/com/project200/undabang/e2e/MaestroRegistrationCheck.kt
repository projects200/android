package com.project200.undabang.e2e

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.impl.AuthRepositoryImpl
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.RegistrationStatus
import com.project200.undabang.oauth.AuthStateManager
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

internal class MaestroRegistrationCheck
    @Inject
    constructor(
        private val apiService: ApiService,
        private val preferences: PreferenceManager,
        private val authStateManager: AuthStateManager,
        private val sessionDataCleaner: SessionDataCleaner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun requireRegistered(): String {
            val observed = RegistrationObservation(apiService)
            // 실제 저장소의 회원 저장·계정 변경 시 캐시 삭제 동작을 그대로 사용합니다.
            val repository = AuthRepositoryImpl(observed, preferences, authStateManager, sessionDataCleaner, ioDispatcher)
            if (repository.checkIsRegistered() != RegistrationStatus.Registered) {
                throw MaestroSetupFailure(observed.failureReason ?: "registration_unknown")
            }
            // 로컬 저장 실패는 네트워크 진단으로 잡지 않고 원래 예외로 전달합니다.
            return repository.getMemberId()?.takeIf { it.isNotBlank() }
                ?: throw MaestroSetupFailure("registration_member_id_missing")
        }

        /** 관찰·복구에서는 회원 교체에 따른 Room 삭제나 회원 저장을 실행하지 않습니다. */
        suspend fun readRegisteredMember(): String {
            val observed = RegistrationObservation(apiService)
            val response =
                try {
                    observed.getIsRegistered()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    throw MaestroSetupFailure(observed.failureReason ?: "registration_unknown")
                }
            observed.failureReason?.let { throw MaestroSetupFailure(it) }
            return response.data?.memberId ?: throw MaestroSetupFailure("registration_response_missing")
        }

        // 각 조회 안에서만 고정 코드 하나를 보관합니다. 응답·예외·본문은 저장하지 않습니다.
        private class RegistrationObservation(private val delegate: ApiService) : ApiService by delegate {
            var failureReason: String? = null
                private set

            override suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData> {
                val response =
                    try {
                        delegate.getIsRegistered()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        failureReason = reasonFor(exception)
                        // apiCallBuilder가 원본 errorBody를 읽도록 예외도 그대로 전달합니다.
                        throw exception
                    }
                val data = response.data
                failureReason =
                    when {
                        !response.succeed ->
                            when (response.code) {
                                "AUTHENTICATION_FAILED" -> "registration_authentication_failed"
                                "USER_ID_HEADER_MISSING" -> "registration_user_id_header_missing"
                                else -> "registration_business_error"
                            }
                        data == null -> "registration_response_missing"
                        !data.isRegistered -> "registration_unregistered"
                        data.memberId.isBlank() -> "registration_member_id_missing"
                        else -> null
                    }
                return response
            }

            private fun reasonFor(exception: Exception): String =
                when (exception) {
                    is HttpException ->
                        exception.code().takeIf { it in 100..599 }?.let { "registration_http_$it" }
                            ?: "registration_unknown"
                    is JsonDataException, is JsonEncodingException, is EOFException -> "registration_parse_error"
                    is SocketTimeoutException -> "registration_network_timeout"
                    is IOException -> "registration_network_error"
                    else -> "registration_unknown"
                }
        }
    }
