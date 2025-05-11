package com.project200.data.impl

import com.project200.data.api.ApiService
import com.project200.data.dto.PostSignUpRequest
import com.project200.domain.model.SignUpResult
import com.project200.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService
) : AuthRepository {

    override suspend fun checkIsRegistered(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getIsRegistered()
        }.fold(
            onSuccess = { it.data?.isRegistered ?: false },
            onFailure = {
                Timber.tag(TAG).d("checkIsRegistered failed$it")
                false
            }
        )
    }

    override suspend fun signUp(
        gender: String,
        nickname: String,
        birth: LocalDate // 파라미터 타입은 API 스펙 및 DTO와 일치
    ): SignUpResult = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.postSignUp(PostSignUpRequest(gender, birth, nickname))

            if (response.succeed && response.data != null) {
                Timber.i("회원가입 성공 MemberId: ${response.data.memberId}")
                SignUpResult.Success(response.data.memberId)
            } else {
                SignUpResult.Failure(response.code)
            }
        }.fold(
            onSuccess = { result: SignUpResult ->
                Timber.i("Registration attempt processed. Result: $result")
                result
            },
            onFailure = { exception ->
                Timber.e(exception, "Registration failed due to an exception in runCatching.")
                when (exception) {
                    is IOException -> SignUpResult.Failure("NETWORK_ERROR")
                    else -> SignUpResult.Failure("UNKNOWN_ERROR")
                }
            }
        )
    }

    companion object {
        const val TAG = "AuthRepositoryImpl"
    }
}