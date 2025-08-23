package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val userRepository: AuthRepository
) {

    suspend operator fun invoke(): BaseResult<Unit> {
        return userRepository.login()
    }
}