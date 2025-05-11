package com.project200.domain.usecase

import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class CheckIsRegisteredUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return authRepository.checkIsRegistered()
    }
}