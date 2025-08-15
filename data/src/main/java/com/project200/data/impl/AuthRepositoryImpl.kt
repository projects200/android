package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PreferenceManager
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SignUpResult
import com.project200.domain.repository.AuthRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import java.util.NoSuchElementException

class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val spManager: PreferenceManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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

    override suspend fun login(): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postLogin() },
            mapper = { Unit }
        )
    }

    override suspend fun logout(): BaseResult<Unit> {
        TODO("Not yet implemented")
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
                spManager.saveMemberId(response.data.memberId)
                SignUpResult.Success
            } else {
                // API 응답은 성공했지만, 서버 로직상 실패(예: 중복, 유효성 검사 실패 등)
                SignUpResult.Failure(response.code, response.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Registration failed due to IOException.")
            SignUpResult.Failure("NETWORK_ERROR", "네트워크 오류")
        } catch (e: Exception) {
            Timber.e(e, "Registration failed due to an unexpected exception.")
            SignUpResult.Failure("UNKNOWN_ERROR", "알 수 없는 오류")
        }
    }

    companion object {
        const val TAG = "AuthRepositoryImpl"
    }
}