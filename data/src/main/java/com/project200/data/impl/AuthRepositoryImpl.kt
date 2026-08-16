package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostLoginRequest
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.utils.FcmTokenProvider
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.repository.AuthRepository
import com.project200.undabang.oauth.AuthStateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

class AuthRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        private val spManager: PreferenceManager,
        private val authStateManager: AuthStateManager,
        private val fcmTokenProvider: FcmTokenProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AuthRepository {
        override suspend fun checkIsRegistered(): RegistrationStatus {
            val result =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getIsRegistered() },
                    mapper = { data ->
                        // 가입 확인된 경우에만 저장
                        if (data != null && data.isRegistered) {
                            spManager.saveMemberId(data.memberId)
                            true
                        } else {
                            false
                        }
                    },
                )

            return when (result) {
                is BaseResult.Success ->
                    if (result.data) RegistrationStatus.Registered else RegistrationStatus.Unregistered
                is BaseResult.Error ->
                    // 미가입자는 인터셉터가 401 AUTHENTICATION_FAILED로 막는다
                    if (result.errorCode == "AUTHENTICATION_FAILED") {
                        RegistrationStatus.Unregistered
                    } else {
                        // 게이트웨이 401("401") / USER_ID_HEADER_MISSING / NETWORK_ERROR / 500 → 판별 불가
                        Timber.tag(TAG).w("checkIsRegistered failed: ${result.errorCode} ${result.message}")
                        RegistrationStatus.Indeterminate
                    }
            }
        }

        /**
         * POST /login은 X-Fcm-Token 헤더로 기기 토큰을 등록합니다.
         * 토큰이 없으면 서버는 헤더 없는 요청을 LOGIN_SUCCESS로 돌려주고 등록은 건너뜁니다.
         * 그 성공을 그대로 올리면 재시도 경로가 닫히므로 보내기 전에 막습니다.
         */
        override suspend fun login(): BaseResult<Unit> {
            val fcmToken = withContext(ioDispatcher) { fcmTokenProvider.getFcmToken() }
            if (fcmToken.isNullOrBlank()) {
                Timber.tag(TAG).w("FCM 토큰이 없어 서버 로그인을 보내지 않습니다.")
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

        override suspend fun logout(): BaseResult<Unit> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postLogout() },
                mapper = { Unit },
            )
        }

        override suspend fun signUp(
            gender: String,
            nickname: String,
            birth: LocalDate,
        ): BaseResult<Unit> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postSignUp(PostSignUpRequest(gender, birth, nickname)) },
                mapper = { responseData ->
                    // 회원가입 성공 시 memberId를 저장
                    responseData?.memberId?.let {
                        Timber.i("회원가입 성공 MemberId: $it")
                        spManager.saveMemberId(it)
                    }
                    Unit
                },
            )
        }

        override suspend fun checkNicknameDuplicated(nickname: String): BaseResult<Boolean> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getIsNicknameDuplicated(nickname) },
                mapper = { dto: GetIsNicknameDuplicated? ->
                    dto?.available == true
                },
            )
        }

        override suspend fun getMemberId(): String? {
            return spManager.getMemberId()
        }

        override suspend fun clearSession() {
            withContext(ioDispatcher) {
                authStateManager.clearAuthState()
                spManager.clearMemberId()
            }
        }

        companion object {
            const val TAG = "AuthRepositoryImpl"
            const val NO_FCM_TOKEN_ERROR_CODE = "NO_FCM_TOKEN"
            private const val NO_FCM_TOKEN_ERROR_MESSAGE = "FCM 토큰이 없어 서버 로그인을 보내지 않았습니다."
        }
    }
