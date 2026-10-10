package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import com.project200.domain.model.RegistrationStatus
import java.time.LocalDate

interface AuthRepository {
    suspend fun checkIsRegistered(): RegistrationStatus
    suspend fun logout(): BaseResult<Unit>
    suspend fun signUp(gender: String, nickname: String, birth: LocalDate): BaseResult<Unit>
    suspend fun checkNicknameDuplicated(nickname: String): BaseResult<Boolean>
    suspend fun getMemberId(): String?

    /** 토큰과 회원ID를 모두 지웁니다 */
    suspend fun clearSession()

    /**
     * 토큰만 지우고 회원ID는 남깁니다.
     *
     * 남은 회원ID는 재로그인 때 계정 전환 판정에 쓰입니다. 같은 계정이면 캐시를 지우지
     * 않아 전송 대기 행이 살아남습니다
     */
    suspend fun clearTokens()
}