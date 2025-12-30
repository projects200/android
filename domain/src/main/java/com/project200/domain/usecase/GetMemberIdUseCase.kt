package com.project200.domain.usecase

import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class GetMemberIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): String {
        return authRepository.getMemberId()
    }
}