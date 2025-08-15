package com.project200.domain.repository

import com.project200.domain.model.SignUpResult
import java.time.LocalDate

interface AuthRepository {
    suspend fun checkIsRegistered(): Boolean
    suspend fun login(): BaseResult<Unit>
    suspend fun signUp(gender: String, nickname: String, birth: LocalDate): SignUpResult
}