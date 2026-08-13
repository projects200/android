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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
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
        override suspend fun checkIsRegistered(): RegistrationStatus =
            withContext(ioDispatcher) {
                try {
                    val response = apiService.getIsRegistered()
                    if (response.data?.isRegistered == true) {
                        spManager.saveMemberId(response.data.memberId)
                        RegistrationStatus.Registered
                    } else {
                        RegistrationStatus.Unregistered
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Timber.tag(TAG).w("checkIsRegistered network error: $e")
                    RegistrationStatus.Indeterminate
                } catch (e: Exception) {
                    Timber.tag(TAG).w("checkIsRegistered failed: $e")
                    RegistrationStatus.Indeterminate
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
