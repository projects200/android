package com.project200.domain.usecase

import com.project200.domain.model.SignUpResult
import com.project200.domain.repository.AuthRepository
import java.time.LocalDate
import javax.inject.Inject

class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        gender: String,
        nickname: String,
        birth: LocalDate
    ): SignUpResult {
        return authRepository.signUp(gender, nickname, birth)
    }
}