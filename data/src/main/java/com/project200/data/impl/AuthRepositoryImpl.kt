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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher

class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override suspend fun checkIsRegistered(): Boolean = withContext(ioDispatcher) {
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

    override suspend fun signUp(
        gender: String,
        nickname: String,
        birth: LocalDate
    ): SignUpResult = withContext(ioDispatcher) {
        try {
            val response = apiService.postSignUp(PostSignUpRequest(gender, birth, nickname))

            if (response.succeed && response.data != null) {
                Timber.i("회원가입 성공 MemberId: ${response.data.memberId}")
                SignUpResult.Success(response.data.memberId)
            } else {
                // API 응답은 성공했지만, 서버 로직상 실패(예: 중복, 유효성 검사 실패 등)
                SignUpResult.Failure(response.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Registration failed due to IOException.")
            SignUpResult.Failure("NETWORK_ERROR")
        } catch (e: Exception) {
            Timber.e(e, "Registration failed due to an unexpected exception.")
            SignUpResult.Failure("UNKNOWN_ERROR")
        }
    }

    companion object {
        const val TAG = "AuthRepositoryImpl"
    }
}