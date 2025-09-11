package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class CheckNicknameDuplicatedUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(nickname: String): BaseResult<Boolean> {
        return authRepository.checkNicknameDuplicated(nickname)
    }
}