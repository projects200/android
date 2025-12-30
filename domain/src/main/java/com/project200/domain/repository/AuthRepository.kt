package com.project200.domain.repository

import com.project200.domain.model.BaseResult
import java.time.LocalDate

interface AuthRepository {
    suspend fun checkIsRegistered(): Boolean
    suspend fun login(): BaseResult<Unit>
    suspend fun logout(): BaseResult<Unit>
    suspend fun signUp(gender: String, nickname: String, birth: LocalDate): BaseResult<Unit>
    suspend fun checkNicknameDuplicated(nickname: String): BaseResult<Boolean>
    suspend fun getMemberId(): String
}