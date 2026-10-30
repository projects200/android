package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.utils.apiCallBuilder
import com.project200.data.utils.idTokenSubject
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
        private val imageFileStore: ExerciseImageFileStore,
    ) : AuthRepository {
        override suspend fun checkIsRegistered(): RegistrationStatus {
            var memberSubject: String? = null
            val result =
                apiCallBuilder(
                    ioDispatcher = ioDispatcher,
                    apiCall = {
                        val requestSubject = idTokenSubject(authStateManager.getCurrent().idToken)
                        val response = apiService.getIsRegistered()
                        val responseSubject = idTokenSubject(authStateManager.getCurrent().idToken)
                        // 요청 중 계정이 바뀌면 다른 계정의 subject를 서버 회원ID에 연결하지 않습니다
                        memberSubject = requestSubject?.takeIf { it == responseSubject }
                        response
                    },
                    mapper = { data -> data?.takeIf { it.isRegistered }?.memberId },
                )

            return when (result) {
                is BaseResult.Success -> {
                    val memberId = result.data
                    if (memberId != null) {
                        replaceMemberId(memberId, memberSubject)
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
        private suspend fun replaceMemberId(
            memberId: String,
            subject: String?,
        ) =
            withContext(ioDispatcher) {
                if (spManager.getMemberId() != memberId) {
                    Timber.tag(TAG).i("계정 경계 변경 - 로컬 캐시 삭제")
                    sessionDataCleaner.clearAll()
                }
                // 이관된 계정은 서버 회원ID와 JWT subject가 다를 수 있어 함께 저장합니다
                spManager.saveMemberId(memberId, subject)
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
            var memberSubject: String? = null
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = {
                    val requestSubject = idTokenSubject(authStateManager.getCurrent().idToken)
                    val response = apiService.postSignUp(PostSignUpRequest(gender, birth, nickname))
                    val responseSubject = idTokenSubject(authStateManager.getCurrent().idToken)
                    // 요청 전후 subject가 같을 때만 서버가 확정한 회원ID에 연결합니다
                    memberSubject = requestSubject?.takeIf { it == responseSubject }
                    response
                },
                mapper = { responseData ->
                    // 계정 전환이 감지되면 subject 없이 서버 회원ID만 저장합니다
                    responseData?.memberId?.let {
                        Timber.i("회원가입 성공 MemberId: $it")
                        spManager.saveMemberId(it, memberSubject)
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
                imageFileStore.withLock {
                    imageFileStore.invalidateSession()
                    spManager.invalidateExerciseSession()
                    authStateManager.clearAuthState()
                    spManager.clearMemberId()
                }
            }
        }

        override suspend fun clearTokens() {
            withContext(ioDispatcher) {
                imageFileStore.withLock {
                    imageFileStore.invalidateSession()
                    spManager.invalidateExerciseSession()
                    authStateManager.clearAuthState()
                }
            }
        }

        companion object {
            const val TAG = "AuthRepositoryImpl"
        }
    }
