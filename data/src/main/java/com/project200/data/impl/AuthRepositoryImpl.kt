package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.manager.SessionDataCleaner
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
        private val sessionDataCleaner: SessionDataCleaner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AuthRepository {
        override suspend fun checkIsRegistered(): RegistrationStatus {
            val result =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = { apiService.getIsRegistered() },
                    mapper = { data -> data?.takeIf { it.isRegistered }?.memberId },
                )

            return when (result) {
                is BaseResult.Success -> {
                    val memberId = result.data
                    if (memberId != null) {
                        replaceMemberId(memberId)
                        RegistrationStatus.Registered
                    } else {
                        RegistrationStatus.Unregistered
                    }
                }
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
         * 회원ID를 저장합니다.
         *
         * 저장 전에 이전 회원ID와 다르면 로컬 캐시를 지웁니다. 로그아웃을 거치지 않은 계정 전환에서
         * 이전 계정의 행이 기기에 남기 때문입니다
         * 이전 값이 없을 때도 지웁니다. 로그아웃 도중 캐시 삭제만 실패한 상태를 흡수합니다
         */
        private suspend fun replaceMemberId(memberId: String) =
            withContext(ioDispatcher) {
                if (spManager.getMemberId() != memberId) {
                    Timber.tag(TAG).i("계정 경계 변경 - 로컬 캐시 삭제")
                    sessionDataCleaner.clearAll()
                }
                spManager.saveMemberId(memberId)
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
