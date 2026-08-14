package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostLoginRequest
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
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

        override suspend fun login(): BaseResult<Unit> {
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
        }
    }
