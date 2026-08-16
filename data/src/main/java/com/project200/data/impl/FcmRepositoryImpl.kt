package com.project200.data.impl

import android.content.SharedPreferences
import com.project200.common.constants.FcmConstants.KEY_FCM_TOKEN
import com.project200.common.di.IoDispatcher
import com.project200.common.utils.EncryptedPrefs
import com.project200.data.api.ApiService
import com.project200.data.dto.PostLoginRequest
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FcmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class FcmRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        @EncryptedPrefs private val sharedPreferences: SharedPreferences,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FcmRepository {
        override suspend fun getSavedToken(): String? {
            return withContext(ioDispatcher) {
                sharedPreferences.getString(KEY_FCM_TOKEN, null)
            }
        }

        /**
         * 토큰 전용 엔드포인트가 없어 당분간 POST /login을 씁니다
         * 서버는 이 요청의 X-Fcm-Token 헤더로 기기 토큰을 등록합니다
         * 헤더가 없으면 등록을 건너뛰고 LOGIN_SUCCESS를 돌려주므로 토큰이 없으면 보내지 않습니다
         */
        override suspend fun registerToken(): BaseResult<Unit> {
            if (getSavedToken().isNullOrBlank()) {
                Timber.tag(TAG).w("FCM 토큰이 없어 등록 요청을 보내지 않습니다")
                return BaseResult.Error(
                    errorCode = NO_FCM_TOKEN_ERROR_CODE,
                    message = NO_FCM_TOKEN_ERROR_MESSAGE,
                )
            }

            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postLogin(PostLoginRequest("ANDROID", "APP")) },
                mapper = { Unit },
            )
        }

        companion object {
            private const val TAG = "FcmRepositoryImpl"
            const val NO_FCM_TOKEN_ERROR_CODE = "NO_FCM_TOKEN"
            private const val NO_FCM_TOKEN_ERROR_MESSAGE = "FCM 토큰이 없어 등록 요청을 보내지 않았습니다"
        }
    }
