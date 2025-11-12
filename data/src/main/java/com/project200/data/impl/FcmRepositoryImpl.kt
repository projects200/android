package com.project200.data.impl

import android.content.Context
import android.content.SharedPreferences
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.EncryptedPrefs
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FcmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FcmRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @EncryptedPrefs private var sharedPreferences: SharedPreferences,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FcmRepository {
        // FCM 토큰을 SharedPreferences에서 가져오는 함수
        override suspend fun getFcmTokenFromPrefs(): String? {
            return withContext(ioDispatcher) {
                sharedPreferences.getString(KEY_FCM_TOKEN, null)
            }
        }

        override suspend fun sendFcmToken(): BaseResult<Unit> {
            val token = getFcmTokenFromPrefs()

            if (token.isNullOrBlank()) {
                return BaseResult.Error(
                    NO_FCM_TOKEN_ERROR_CODE,
                    NO_FCM_TOKEN_ERROR_MESSAGE,
                    NoSuchElementException(NO_FCM_TOKEN_ERROR_MESSAGE),
                )
            }

            // TODO: FCM 토큰을 서버로 전송하는 API 호출
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = {
                    /** apiService.sendFcmToken(FcmTokenRequest(token)) */
                    BaseResponse<Unit>(code = "", message = "", succeed = true, data = Unit)
                },
                mapper = { Unit },
            )
        }

        companion object {
            private const val NO_FCM_TOKEN_ERROR_CODE = "-1"
            private const val NO_FCM_TOKEN_ERROR_MESSAGE = "fcm_token_not_found"
        }
    }
