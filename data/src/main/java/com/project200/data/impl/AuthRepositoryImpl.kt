package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AuthRepository {
        override suspend fun checkIsRegistered(): Boolean =
            withContext(ioDispatcher) {
                try {
                    val response = apiService.getIsRegistered()
                    response.data?.isRegistered ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).d("checkIsRegistered failed: $e")
                    false
                }
            }

        override suspend fun login(): BaseResult<Unit> {
            return apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postLogin() },
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

        companion object {
            const val TAG = "AuthRepositoryImpl"
        }
    }
